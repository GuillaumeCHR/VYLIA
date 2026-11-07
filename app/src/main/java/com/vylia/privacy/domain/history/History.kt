package com.vylia.privacy.domain.history

import com.vylia.privacy.domain.model.PrivacyScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.sqrt

/**
 * Une app utilisateur à un instant donné : son score, ses permissions notées accordées, et de quoi expliquer une baisse
 * d'un scan à l'autre (noms des traqueurs, motifs de sécurité). [trackers] et [security] valent `null` dans les points
 * enregistrés avant qu'ils existent : inconnus, ils ne sont pas comparés.
 */
@Serializable
data class AppPoint(
    val label: String,
    val score: Int,
    val granted: Set<String> = emptySet(),
    val trackers: Set<String>? = null,
    val security: Set<String>? = null,
)

/** Un point de la courbe : moyenne et écart-type des scores, et l'état de chaque app (pour les événements). */
@Serializable
data class ScoreSnapshot(
    val at: Long,
    val average: Double,
    val stdDev: Double,
    val apps: Map<String, AppPoint>,
    /** Empreinte des règles de score (et de la base des stalkerwares) en vigueur ; vide dans les anciens points. */
    val rules: String = "",
) {
    /** Score de l'appareil à ce point, arrondi comme partout ailleurs. */
    val score: Int get() = PrivacyScore.ofAverage(average).value
}

enum class EventType { INSTALLED, REMOVED, PERMISSION_GRANTED, PERMISSION_REVOKED }

/** Événement constaté d'un scan complet à l'autre. [score] : score de l'app à ce moment. */
@Serializable
data class HistoryEvent(
    val at: Long,
    val type: EventType,
    val packageName: String,
    val label: String,
    val permission: String? = null,
    val score: Int? = null,
)

@Serializable
data class History(val snapshots: List<ScoreSnapshot> = emptyList(), val events: List<HistoryEvent> = emptyList()) {

    /**
     * Évolution du score moyen sur [periodMs] : dernier point moins le plus récent point d'au moins [periodMs], ou le
     * tout premier point si l'historique est plus court. `null` avec moins de deux jours de points.
     */
    fun progress(periodMs: Long = PROGRESS_PERIOD_MS): Progress? {
        val last = snapshots.lastOrNull() ?: return null
        val reference = snapshots.lastOrNull { it.at <= last.at - periodMs } ?: snapshots.first()
        if (reference === last) return null
        return Progress(
            points = last.score - reference.score,
            sinceStart = reference === snapshots.first() && last.at - reference.at < periodMs,
        )
    }

    /** [sinceStart] : l'historique couvre moins que la période demandée, l'écart part du premier scan. */
    data class Progress(val points: Int, val sinceStart: Boolean)

    companion object {
        const val PROGRESS_PERIOD_MS = 30L * 24 * 60 * 60 * 1000
    }
}

interface HistoryStore {
    fun load(): History?
    fun save(history: History)
}

/** Poids d'une app dans la moyenne : de combien elle tire la courbe vers le haut (positif) ou le bas. */
data class Influence(val packageName: String, val label: String, val score: Int, val pull: Double)

/**
 * Suivi longitudinal, 100 % local : à chaque scan complet, un point de courbe (un par jour, le plus récent l'emporte)
 * et les événements constatés depuis le point précédent (apps installées ou supprimées, permissions accordées ou
 * retirées). Le premier scan sert de référence, sans événement.
 */
class HistoryRecorder(
    private val store: HistoryStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _history = MutableStateFlow(store.load() ?: History())
    val history: StateFlow<History> = _history.asStateFlow()

    @Synchronized
    fun record(apps: Map<String, AppPoint>, rules: String = "") {
        if (apps.isEmpty()) return
        val now = clock()
        val current = _history.value
        val previous = current.snapshots.lastOrNull()
        val events = previous?.let { diff(it.apps, apps, now) }.orEmpty()
        val scores = apps.values.map { it.score.toDouble() }
        val mean = scores.average()
        val snapshot = ScoreSnapshot(now, mean, sqrt(scores.sumOf { (it - mean) * (it - mean) } / scores.size), apps, rules)
        val snapshots = if (previous != null && sameDay(previous.at, now)) current.snapshots.dropLast(1) + snapshot else current.snapshots + snapshot
        update(History(snapshots.takeLast(MAX_SNAPSHOTS), (events.sortedByDescending { it.at } + current.events).take(MAX_EVENTS)))
    }

    @Synchronized
    fun clear() = update(History())

    /** Remplace le suivi par le résultat de [transform] (restauration d'une sauvegarde). */
    @Synchronized
    fun <T> merge(transform: (History) -> Pair<History, T>): T {
        val (history, result) = transform(_history.value)
        update(history)
        return result
    }

    private fun diff(before: Map<String, AppPoint>, after: Map<String, AppPoint>, now: Long): List<HistoryEvent> = buildList {
        (after.keys - before.keys).forEach { pkg -> val a = after.getValue(pkg); add(HistoryEvent(now, EventType.INSTALLED, pkg, a.label, score = a.score)) }
        (before.keys - after.keys).forEach { pkg -> val b = before.getValue(pkg); add(HistoryEvent(now, EventType.REMOVED, pkg, b.label, score = b.score)) }
        (after.keys intersect before.keys).forEach { pkg ->
            val b = before.getValue(pkg)
            val a = after.getValue(pkg)
            (a.granted - b.granted).forEach { add(HistoryEvent(now, EventType.PERMISSION_GRANTED, pkg, a.label, it, a.score)) }
            (b.granted - a.granted).forEach { add(HistoryEvent(now, EventType.PERMISSION_REVOKED, pkg, a.label, it, a.score)) }
        }
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        fun key(t: Long) = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        return key(a) == key(b)
    }

    private fun update(history: History) {
        _history.value = history
        store.save(history)
    }

    companion object {
        /** Deux ans à raison d'un point par jour. */
        const val MAX_SNAPSHOTS = 730
        const val MAX_EVENTS = 500

        /**
         * Apps qui pèsent le plus sur la moyenne du dernier point : (score − moyenne) / nombre d'apps, c'est-à-dire
         * de combien la moyenne bougerait sans elles. Les plus négatives d'abord.
         */
        fun influences(snapshot: ScoreSnapshot): List<Influence> {
            val n = snapshot.apps.size
            if (n < 2) return emptyList()
            return snapshot.apps.map { (pkg, app) ->
                Influence(pkg, app.label, app.score, (app.score - snapshot.average) / (n - 1))
            }.sortedBy { it.pull }
        }
    }
}
