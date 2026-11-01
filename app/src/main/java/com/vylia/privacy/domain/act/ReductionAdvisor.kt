package com.vylia.privacy.domain.act

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.domain.scoring.BadgeDef
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.domain.scoring.PermissionFinding
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.usecase.scoreWithout

/** Autorisation à retirer : son effet exact sur le score et les badges de l'app, recalculés sans elle. */
data class RevokeSuggestion(
    val app: AuditedApp,
    /** La permission la plus pénalisante du groupe retiré, pour l'affichage. */
    val finding: PermissionFinding,
    /** Permissions retirées ensemble, comme le fait Android (ex. position précise, approximative et en arrière-plan). */
    val revoked: Set<String>,
    val newScore: Int,
    val badgesGained: List<BadgeDef>,
    val badgesLost: List<BadgeDef>,
) {
    val gain: Int get() = newScore - app.score.value
}

/** App à supprimer : inutilisée et/ou intrusive. */
data class RemovalSuggestion(
    val app: AuditedApp,
    /** Dernière utilisation connue (ms), ou `null` si l'accès aux données d'utilisation n'est pas accordé. */
    val lastUsedAt: Long?,
) {
    /** Points de confidentialité gagnés à la suppression : seules les apps intrusives en rapportent. */
    val points: Int get() = if (app.score.value < BadgesManager.INTRUSIVE_THRESHOLD) PrivacyScore.MAX - app.score.value else 0
}

/**
 * « Réduire » : les victoires faciles, triées par impact. Les retraits d'autorisation sont simulés avec le vrai moteur
 * de score (groupes, plafonds, capacités conditionnelles et badges compris) : le gain annoncé est exact.
 */
object ReductionAdvisor {

    /**
     * Groupes d'autorisations Android : retirer l'une retire les autres dans les réglages (même écran, même
     * interrupteur). Les permissions absentes de cette table se retirent seules.
     */
    private val ANDROID_GROUPS = listOf(
        setOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION"),
        setOf("READ_SMS", "RECEIVE_SMS", "SEND_SMS", "RECEIVE_MMS"),
        setOf("READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS"),
        setOf("READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE", "ANSWER_PHONE_CALLS"),
        setOf("READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS"),
        setOf("READ_CALENDAR", "WRITE_CALENDAR"),
        setOf("BODY_SENSORS", "BODY_SENSORS_BACKGROUND"),
        setOf("READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_VISUAL_USER_SELECTED"),
        setOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES"),
    )

    /** Seuil sous lequel une app inutilisée est proposée à la suppression (au-dessus, elle ne gêne guère). */
    const val REMOVAL_SCORE_THRESHOLD = 70

    /** Une app non ouverte depuis ce délai est considérée comme inutilisée. */
    const val UNUSED_AFTER_MS = 60L * 24 * 60 * 60 * 1000

    /**
     * Autorisations accordées qui coûtent des points sans être justifiées par la finalité de l'app, avec le score
     * qu'elle aurait sans elles. Les retraits sans effet sur le score ne sont pas proposés.
     */
    fun revocations(apps: List<AuditedApp>, scorer: PrivacyScorer): List<RevokeSuggestion> = apps.flatMap { audited ->
        val result = audited.result
        result.permissionFindings
            .filter { it.granted && it.counted > 0.0 && it.context != Context.ALLOWED }
            .groupBy { finding -> ANDROID_GROUPS.firstOrNull { finding.permission in it } ?: setOf(finding.permission) }
            .mapNotNull { (group, findings) ->
                val revoked = group.intersect(audited.app.grantedPermissions)
                val after = scorer.scoreWithout(audited, revoked)
                if (after.score <= audited.score.value) return@mapNotNull null
                val before = result.badges.map { it.id }.toSet()
                val now = after.badges.map { it.id }.toSet()
                RevokeSuggestion(
                    app = audited,
                    finding = findings.maxBy { it.counted },
                    revoked = revoked,
                    newScore = after.score,
                    badgesGained = after.badges.filter { it.id !in before && it.polarity == "positive" },
                    badgesLost = result.badges.filter { it.id !in now && it.polarity == "negative" },
                )
            }
    }.sortedWith(compareByDescending<RevokeSuggestion> { it.gain }.thenBy { it.app.app.label })

    /**
     * Apps à supprimer : les intrusives (zone rouge), et celles sous [REMOVAL_SCORE_THRESHOLD] inutilisées depuis
     * [UNUSED_AFTER_MS]. Les plus délaissées et les plus intrusives d'abord.
     *
     * @param lastUsed dernière utilisation par paquet, ou `null` sans accès aux données d'utilisation.
     */
    fun removals(apps: List<AuditedApp>, lastUsed: Map<String, Long>?, now: Long): List<RemovalSuggestion> {
        fun unused(app: AuditedApp): Boolean {
            val at = lastUsed?.get(app.app.packageName) ?: return lastUsed != null // jamais ouverte sur la période
            return now - at >= UNUSED_AFTER_MS
        }
        return apps
            .filter { it.score.value < BadgesManager.INTRUSIVE_THRESHOLD || (it.score.value < REMOVAL_SCORE_THRESHOLD && unused(it)) }
            .map { RemovalSuggestion(it, lastUsed?.get(it.app.packageName)) }
            .sortedWith(
                compareByDescending<RemovalSuggestion> { if (lastUsed == null) 0 else if (unused(it.app)) 1 else 0 }
                    .thenBy { it.app.score.value }
                    .thenBy { it.lastUsedAt ?: 0L },
            )
    }

    /**
     * Score moyen atteignable : chaque app est recalculée sans toutes ses autorisations à retirer (le moteur gère
     * plafonds et groupes, les gains ne s'additionnent pas simplement), et les apps à supprimer partent.
     */
    fun projectedAverage(
        apps: List<AuditedApp>,
        revocations: List<RevokeSuggestion>,
        removals: List<RemovalSuggestion>,
        scorer: PrivacyScorer,
    ): PrivacyScore? {
        val removed = removals.map { it.app.app.packageName }.toSet()
        val revokedByApp = revocations.groupBy { it.app.app.packageName }.mapValues { (_, list) -> list.flatMap { it.revoked }.toSet() }
        val scores = apps.filter { it.app.packageName !in removed }.map { audited ->
            revokedByApp[audited.app.packageName]?.let { PrivacyScore(scorer.scoreWithout(audited, it).score) } ?: audited.score
        }
        return PrivacyScore.average(scores)
    }
}
