package com.vylia.privacy.domain.backup

import com.vylia.privacy.domain.alert.ThresholdCrossing
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.rewards.RewardsState
import kotlinx.serialization.Serializable

/**
 * Sauvegarde complète de VYLIA, en JSON lisible : Suivi, récompenses, réglages et règles importées. Sert aussi
 * d'export RGPD : c'est tout ce que l'app conserve sur vous.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    /** Version de l'app qui a écrit le fichier, pour information. */
    val app: String,
    val history: History = History(),
    val rewards: RewardsState = RewardsState(),
    val settings: BackupSettings = BackupSettings(),
    /** Règles de score importées (fichier JSON d'origine), `null` si ce sont les règles intégrées. */
    val importedRules: String? = null,
    /** Franchissements de l'alerte de seuil (50 au plus). */
    val thresholdCrossings: List<ThresholdCrossing> = emptyList(),
) {
    companion object {
        const val FORMAT = "vylia-backup"
        const val VERSION = 1

        /** Au-delà, le fichier est refusé avant d'être lu en entier. */
        const val MAX_BYTES = 10 * 1024 * 1024
    }
}

/** Réglages au moment de l'export : exportés pour être complets, jamais réappliqués (vos réglages actuels priment). */
@Serializable
data class BackupSettings(
    val advancedMode: Boolean = false,
    val installAlerts: Boolean = false,
    val backgroundScan: String = "OFF",
    val language: String? = null,
)

/** Bilan d'une restauration. */
data class RestoreReport(
    val snapshotsAdded: Int,
    val eventsAdded: Int,
    val rewardsAdded: Int,
    /** Éléments déjà présents sur le téléphone, ignorés. */
    val alreadyThere: Int,
    /** Éléments incohérents (dates ou scores hors bornes), écartés. */
    val invalid: Int,
    /** La sauvegarde contenait des règles de score : jamais appliquées d'office, à importer soi-même. */
    val rulesIgnored: Boolean,
)

/** Fichier refusé, sans rien avoir écrit. */
enum class RestoreError { NOT_A_BACKUP, NEWER_VERSION, TOO_LARGE, UNREADABLE }

class RestoreException(val error: RestoreError) : Exception(error.name)

/**
 * Fusion d'une sauvegarde avec les données du téléphone : on ajoute, on n'écrase jamais. Les points de courbe et
 * les événements déjà présents (même date) sont ignorés, les incohérents écartés.
 */
object BackupMerge {

    /** @param added éléments ajoutés ; pour le suivi, [addedEvents] en détaille les événements. */
    data class Merged<T>(val value: T, val added: Int, val alreadyThere: Int, val invalid: Int, val addedEvents: Int = 0)

    fun history(current: History, incoming: History, now: Long): Merged<History> {
        val horizon = now + DAY
        val knownSnapshots = current.snapshots.mapTo(HashSet()) { it.at }
        val (validSnapshots, badSnapshots) = incoming.snapshots.partition { s ->
            s.at in 1..horizon && s.average in 0.0..PrivacyScore.MAX.toDouble() && s.stdDev >= 0.0 &&
                s.apps.values.all { it.score in 0..PrivacyScore.MAX }
        }
        val newSnapshots = validSnapshots.filter { it.at !in knownSnapshots }.distinctBy { it.at }

        fun key(e: com.vylia.privacy.domain.history.HistoryEvent) = listOf(e.at, e.type, e.packageName, e.permission)
        val knownEvents = current.events.mapTo(HashSet(), ::key)
        val (validEvents, badEvents) = incoming.events.partition { e ->
            e.at in 1..horizon && e.packageName.isNotBlank() && (e.score == null || e.score in 0..PrivacyScore.MAX)
        }
        val newEvents = validEvents.filter { key(it) !in knownEvents }.distinctBy(::key)

        val merged = History(
            snapshots = (current.snapshots + newSnapshots).sortedBy { it.at }.takeLast(HistoryRecorder.MAX_SNAPSHOTS),
            events = (current.events + newEvents).sortedByDescending { it.at }.take(HistoryRecorder.MAX_EVENTS),
        )
        return Merged(
            merged,
            added = newSnapshots.size,
            addedEvents = newEvents.size,
            alreadyThere = (validSnapshots.size - newSnapshots.size) + (validEvents.size - newEvents.size),
            invalid = badSnapshots.size + badEvents.size,
        )
    }

    /**
     * Récompenses : apps supprimées et trophées s'ajoutent (le trophée garde sa date la plus ancienne), les compteurs
     * gardent la plus grande valeur. L'état des apps reste celui du téléphone : repris d'une sauvegarde, il ferait
     * passer les apps absentes d'ici pour des suppressions récompensées.
     */
    fun rewards(current: RewardsState, incoming: RewardsState, now: Long): Merged<RewardsState> {
        val horizon = now + DAY
        val known = current.removedApps.mapTo(HashSet()) { it.packageName to it.removedAt }
        val (valid, bad) = incoming.removedApps.partition { it.removedAt in 1..horizon && it.score in 0..PrivacyScore.MAX }
        val fresh = valid.filter { (it.packageName to it.removedAt) !in known }.distinctBy { it.packageName to it.removedAt }
        val badges = (current.unlockedBadges.keys + incoming.unlockedBadges.keys).associateWith { badge ->
            listOfNotNull(current.unlockedBadges[badge], incoming.unlockedBadges[badge]).min()
        }
        val newBadges = incoming.unlockedBadges.keys.count { it !in current.unlockedBadges }
        val merged = current.copy(
            removedApps = (current.removedApps + fresh).sortedByDescending { it.removedAt },
            unlockedBadges = badges,
            completedScans = maxOf(current.completedScans, incoming.completedScans),
            revokedPermissions = maxOf(current.revokedPermissions, incoming.revokedPermissions),
            swaps = current.swaps + incoming.swaps,
            shieldBlocked = current.shieldBlocked || incoming.shieldBlocked,
        )
        return Merged(merged, fresh.size + newBadges, valid.size - fresh.size, bad.size)
    }

    private const val DAY = 24L * 60 * 60 * 1000
}
