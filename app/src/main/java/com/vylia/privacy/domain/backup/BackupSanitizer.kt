package com.vylia.privacy.domain.backup

import com.vylia.privacy.domain.history.AppPoint
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryEvent
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.history.ScoreSnapshot
import com.vylia.privacy.domain.rewards.RemovedApp
import com.vylia.privacy.domain.rewards.RewardsState
import com.vylia.privacy.domain.security.Untrusted

/**
 * Contrôle d'une sauvegarde avant fusion : un fichier venu d'ailleurs est traité comme hostile. Les textes sont
 * nettoyés et tronqués, les identifiants vérifiés, les listes et les compteurs bornés ; un élément douteux est
 * écarté (et compté), jamais corrigé à l'aveugle.
 */
object BackupSanitizer {
    const val MAX_LABEL = 100
    const val MAX_APPS_PER_SNAPSHOT = 3_000
    const val MAX_PERMISSIONS_PER_APP = 500
    const val MAX_REMOVED_APPS = 2_000
    const val MAX_SWAPS = 1_000
    const val MAX_COUNTER = 1_000_000

    data class Cleaned(val file: BackupFile, val invalid: Int)

    fun clean(file: BackupFile): Cleaned {
        var invalid = 0
        fun <T> keep(items: List<T>, max: Int, transform: (T) -> T?): List<T> {
            invalid += (items.size - max).coerceAtLeast(0)
            return items.take(max).mapNotNull { item -> transform(item) ?: null.also { invalid++ } }
        }

        val history = History(
            snapshots = keep(file.history.snapshots, HistoryRecorder.MAX_SNAPSHOTS, ::snapshot),
            events = keep(file.history.events, HistoryRecorder.MAX_EVENTS, ::event),
        )
        val r = file.rewards
        val removed = keep(r.removedApps, MAX_REMOVED_APPS, ::removedApp)
        val swaps = r.swaps.filter(::isPackage).take(MAX_SWAPS).toSet()
        invalid += r.swaps.size - swaps.size
        val rewards = RewardsState(
            // L'état des apps n'est jamais repris d'une sauvegarde (voir BackupMerge.rewards).
            removedApps = removed,
            unlockedBadges = r.unlockedBadges.filterValues { it > 0 },
            completedScans = r.completedScans.coerceIn(0, MAX_COUNTER),
            revokedPermissions = r.revokedPermissions.coerceIn(0, MAX_COUNTER),
            swaps = swaps,
        )
        // Réglages et règles ne sont jamais appliqués : inutile de garder plus que l'information.
        return Cleaned(file.copy(history = history, rewards = rewards), invalid)
    }

    private fun snapshot(s: ScoreSnapshot): ScoreSnapshot? {
        if (s.apps.size > MAX_APPS_PER_SNAPSHOT || !s.average.isFinite() || !s.stdDev.isFinite()) return null
        val apps = s.apps.mapValues { (pkg, app) -> if (isPackage(pkg)) appPoint(app) else null }
        if (apps.values.any { it == null }) return null
        @Suppress("UNCHECKED_CAST")
        return s.copy(apps = apps as Map<String, AppPoint>)
    }

    private fun appPoint(a: AppPoint): AppPoint? =
        if (a.granted.size > MAX_PERMISSIONS_PER_APP || !a.granted.all(::isPermission)) null
        else a.copy(label = label(a.label))

    private fun event(e: HistoryEvent): HistoryEvent? =
        if (!isPackage(e.packageName) || (e.permission != null && !isPermission(e.permission))) null
        else e.copy(label = label(e.label))

    private fun removedApp(a: RemovedApp): RemovedApp? =
        if (!isPackage(a.packageName) || a.trackerCount !in 0..1_000) null else a.copy(label = label(a.label))

    private fun isPackage(s: String) = Untrusted.isPackage(s)

    private fun isPermission(s: String) = Untrusted.isPermission(s)

    private fun label(s: String) = Untrusted.text(s, MAX_LABEL)
}
