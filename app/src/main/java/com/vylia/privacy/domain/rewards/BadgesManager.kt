package com.vylia.privacy.domain.rewards

import androidx.annotation.StringRes
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.PrivacyScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Badges débloqués définitivement, une fois la condition remplie. */
enum class Badge(val emoji: String, @param:StringRes val title: Int, @param:StringRes val description: Int) {
    FIRST_AUDIT("🔍", R.string.badge_first_audit, R.string.badge_first_audit_description),
    FIRST_CLEANUP("🧹", R.string.badge_first_cleanup, R.string.badge_first_cleanup_description),
    BIG_CLEANUP("🗑️", R.string.badge_big_cleanup, R.string.badge_big_cleanup_description),
    TRACKER_HUNTER("🎯", R.string.badge_tracker_hunter, R.string.badge_tracker_hunter_description),
    NO_RED_ZONE("🛡️", R.string.badge_no_red_zone, R.string.badge_no_red_zone_description),
    GREEN_DEVICE("🌿", R.string.badge_green_device, R.string.badge_green_device_description),
}

/** Dernier état connu d'une app utilisateur, référence pour détecter sa suppression. */
@Serializable
data class AppSnapshot(val label: String, val score: Int, val trackerCount: Int)

/** App intrusive supprimée par l'utilisateur : plus elle était intrusive, plus elle rapporte de points. */
@Serializable
data class RemovedApp(
    val packageName: String,
    val label: String,
    val score: Int,
    val trackerCount: Int,
    val removedAt: Long,
) {
    val points: Int get() = PrivacyScore.MAX - score
}

@Serializable
data class RewardsState(
    val snapshot: Map<String, AppSnapshot> = emptyMap(),
    /** Du plus récent au plus ancien. */
    val removedApps: List<RemovedApp> = emptyList(),
    val unlockedBadges: Map<Badge, Long> = emptyMap(),
    val completedScans: Int = 0,
) {
    val points: Int get() = removedApps.sumOf { it.points }
}

interface RewardsStore {
    fun load(): RewardsState?
    fun save(state: RewardsState)
}

/**
 * Gamification 100 % locale : compare les apps utilisateur d'un scan à l'autre (ou en direct, à la
 * désinstallation) pour récompenser la suppression des apps intrusives (score < [INTRUSIVE_THRESHOLD]).
 */
class BadgesManager(
    private val store: RewardsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(store.load() ?: RewardsState())
    val state: StateFlow<RewardsState> = _state.asStateFlow()

    /** Scan rapide : toute app du dernier scan complet absente de [installedPackages] a été supprimée. */
    @Synchronized
    fun onAppsListed(installedPackages: Set<String>): List<RemovedApp> =
        onAppsRemoved(_state.value.snapshot.filterKeys { it !in installedPackages })

    /**
     * Désinstallations constatées, avec l'état connu le plus récent de chaque app.
     * Renvoie les suppressions récompensées (apps intrusives uniquement).
     */
    @Synchronized
    fun onAppsRemoved(removed: Map<String, AppSnapshot>): List<RemovedApp> {
        if (removed.isEmpty()) return emptyList()
        val current = _state.value
        val now = clock()
        val rewarded = removed
            .filterValues { it.score < INTRUSIVE_THRESHOLD }
            .map { (packageName, app) -> RemovedApp(packageName, app.label, app.score, app.trackerCount, now) }
            .sortedBy { it.score }
        val next = current.copy(
            snapshot = current.snapshot - removed.keys,
            removedApps = rewarded + current.removedApps,
        )
        update(next.copy(unlockedBadges = unlock(next, userApps = null)))
        return rewarded
    }

    /** Scan complet terminé : [userApps] devient la référence du prochain diff. */
    @Synchronized
    fun onScanCompleted(userApps: Map<String, AppSnapshot>) {
        val current = _state.value
        val next = current.copy(snapshot = userApps, completedScans = current.completedScans + 1)
        update(next.copy(unlockedBadges = unlock(next, userApps.values)))
    }

    /** [userApps] `null` : seuls les badges liés aux suppressions sont évalués. */
    private fun unlock(state: RewardsState, userApps: Collection<AppSnapshot>?): Map<Badge, Long> {
        val earned = buildSet {
            if (state.completedScans >= 1) add(Badge.FIRST_AUDIT)
            if (state.removedApps.isNotEmpty()) add(Badge.FIRST_CLEANUP)
            if (state.removedApps.size >= 5) add(Badge.BIG_CLEANUP)
            if (state.removedApps.sumOf { it.trackerCount } >= 20) add(Badge.TRACKER_HUNTER)
            if (!userApps.isNullOrEmpty()) {
                if (userApps.none { it.score < INTRUSIVE_THRESHOLD }) add(Badge.NO_RED_ZONE)
                if (userApps.map { it.score }.average() >= 80) add(Badge.GREEN_DEVICE)
            }
        }
        val now = clock()
        return state.unlockedBadges + (earned - state.unlockedBadges.keys).associateWith { now }
    }

    private fun update(state: RewardsState) {
        _state.value = state
        store.save(state)
    }

    companion object {
        /** Même seuil que la zone rouge du score. */
        const val INTRUSIVE_THRESHOLD = 50
    }
}
