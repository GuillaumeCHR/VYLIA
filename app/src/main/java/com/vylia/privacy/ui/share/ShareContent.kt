package com.vylia.privacy.ui.share

import com.vylia.privacy.domain.analysis.DeviceAnalysis
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.rewards.Badge
import com.vylia.privacy.domain.rewards.RewardsState

/** Formats d'image : story (par défaut), fil Instagram, carré. Largeur 1 080 px dans tous les cas. */
enum class ShareFormat(val width: Int, val height: Int) {
    STORY(1_080, 1_920),
    PORTRAIT(1_080, 1_350),
    SQUARE(1_080, 1_080),
}

/** Modèles d'image. Chacun n'est proposé que s'il a quelque chose à montrer ([ShareContent.templates]). */
enum class ShareTemplate { REPORT, TRACKERS, SHIELD, PROGRESS, TROPHIES }

/**
 * Tout ce qu'une image de partage affiche : des chiffres, jamais le nom d'une app ni d'une donnée personnelle.
 * Calculé une fois depuis le dernier scan, l'historique, les récompenses et le compteur du bouclier.
 */
data class ShareContent(
    /** Score moyen des apps de l'utilisateur. */
    val score: Int,
    val appCount: Int,
    /** Score moyen au premier scan enregistré, et sa date ; `null` sans historique. */
    val firstScore: Int?,
    val firstAt: Long?,
    /** Scores moyens successifs (un par jour de scan), du plus ancien au plus récent. */
    val curve: List<Float>,
    val trackers: Int,
    val appsWithTrackers: Int,
    val analyzedApps: Int,
    /** Les familles de traqueurs les plus répandues (nom déjà traduit, nombre d'apps), 4 au plus. */
    val categories: List<Pair<String, Int>>,
    val shieldBlocked: Long,
    val points: Int,
    val unlockedBadges: List<Badge>,
    val removedApps: Int,
) {
    val progress: Int get() = firstScore?.let { score - it } ?: 0

    /** Modèles qui ont de quoi s'afficher, le bilan d'abord. */
    val templates: List<ShareTemplate>
        get() = buildList {
            add(ShareTemplate.REPORT)
            if (trackers > 0) add(ShareTemplate.TRACKERS)
            if (shieldBlocked > 0) add(ShareTemplate.SHIELD)
            if (curve.size >= 2 && progress != 0) add(ShareTemplate.PROGRESS)
            if (unlockedBadges.isNotEmpty()) add(ShareTemplate.TROPHIES)
        }

    companion object {
        const val MAX_CATEGORIES = 4

        fun from(
            score: Int,
            userApps: List<AuditedApp>,
            history: History,
            rewards: RewardsState,
            shieldBlocked: Long,
            categoryName: (String) -> String,
        ): ShareContent {
            val analysis = DeviceAnalysis.from(userApps)
            val snapshots = history.snapshots
            return ShareContent(
                score = score,
                appCount = userApps.size,
                firstScore = snapshots.firstOrNull()?.score,
                firstAt = snapshots.firstOrNull()?.at,
                curve = snapshots.map { it.average.toFloat() },
                trackers = analysis.trackers.size,
                appsWithTrackers = analysis.appsWithTrackers,
                analyzedApps = analysis.analyzedAppCount,
                categories = analysis.trackerCategories.take(MAX_CATEGORIES).map { (id, count) -> categoryName(id) to count },
                shieldBlocked = shieldBlocked,
                points = rewards.points,
                unlockedBadges = Badge.entries.filter { it in rewards.unlockedBadges },
                removedApps = rewards.removedApps.size,
            )
        }
    }
}
