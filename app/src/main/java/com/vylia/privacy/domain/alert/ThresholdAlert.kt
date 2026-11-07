package com.vylia.privacy.domain.alert

import kotlinx.serialization.Serializable

/** `ARMED` : le score est au-dessus du seuil, une baisse sous le seuil alertera. `TRIGGERED` : il est dessous. */
enum class AlertState { ARMED, TRIGGERED }

/** Réglage de l'alerte, choisi dans Paramètres › Automatisations (seul endroit où il se modifie). */
data class ThresholdSetting(val enabled: Boolean = false, val threshold: Int = DEFAULT_THRESHOLD) {
    companion object {
        /** La borne de la note B : en dessous, l'appareil passe en C. */
        const val DEFAULT_THRESHOLD = 70
    }
}

/** Ce qui fait le plus baisser le score de l'appareil d'un scan complet au précédent. */
@Serializable
data class DropCause(
    val type: Type,
    val packageName: String? = null,
    val label: String? = null,
    /** Score de l'app concernée au moment du franchissement. */
    val appScore: Int? = null,
    /** Permission, traqueurs (séparés par des virgules) ou identifiant du motif de sécurité. */
    val detail: String? = null,
) {
    enum class Type { INSTALLED, REMOVED, PERMISSION_GRANTED, TRACKER_ADDED, SECURITY_PATTERN, RULES_UPDATED, SPREAD }

    /** Une app à mettre en tête de Réduire : celle en cause, sauf si elle n'est plus installée. */
    val pinnedPackage: String? get() = packageName.takeIf { type != Type.REMOVED && type != Type.RULES_UPDATED && type != Type.SPREAD }
}

/** Où ouvrir l'app depuis la notification d'un franchissement, selon sa cause. */
enum class AlertTarget { MANAGE_APPS, MANAGE_PERMISSIONS, APP_DETAIL, TRACKING }

/**
 * Apps pour une app installée ou désinstallée, Autorisations pour une permission accordée, la fiche de l'app pour un
 * traqueur ou un motif de sécurité (ce qu'on peut y faire est sur sa fiche), le Suivi pour une mise à jour des règles
 * ou une baisse répartie (aucune app à montrer).
 */
val DropCause.target: AlertTarget
    get() = when (type) {
        DropCause.Type.INSTALLED, DropCause.Type.REMOVED -> AlertTarget.MANAGE_APPS
        DropCause.Type.PERMISSION_GRANTED -> AlertTarget.MANAGE_PERMISSIONS
        DropCause.Type.TRACKER_ADDED, DropCause.Type.SECURITY_PATTERN ->
            if (packageName != null) AlertTarget.APP_DETAIL else AlertTarget.TRACKING
        DropCause.Type.RULES_UPDATED, DropCause.Type.SPREAD -> AlertTarget.TRACKING
    }

/** Un franchissement qui a déclenché une notification ; les 50 derniers alimentent les marqueurs du Suivi. */
@Serializable
data class ThresholdCrossing(val at: Long, val score: Int, val threshold: Int, val cause: DropCause)

/**
 * Logique de l'alerte de seuil, sans Android. Le score est celui de l'appareil, arrondi comme partout
 * ([com.vylia.privacy.domain.model.PrivacyScore.ofAverage]).
 *
 * Sous le seuil = strictement inférieur. Le réarmement attend seuil + [REARM_MARGIN] : à apps et règles égales le
 * score ne bouge pas, mais l'analyse des traqueurs d'une app qui échoue puis réussit (pénalité forfaitaire) déplace
 * la moyenne d'environ 0,1 point, assez pour faire basculer l'arrondi juste au seuil. Un point de marge évite qu'un tel
 * va-et-vient renvoie la même alerte, sans retarder le réarmement après une vraie amélioration.
 */
object ThresholdAlert {
    const val REARM_MARGIN = 1
    const val MAX_CROSSINGS = 50

    /** [state] : nouvel état (`null` : rien à enregistrer) ; [notify] : envoyer la notification. */
    data class Decision(val state: AlertState?, val notify: Boolean)

    /**
     * Fin d'un scan complet. [state] `null` : pas encore de référence (premier scan, ou alerte tout juste activée sans
     * score connu) : l'état est fixé sans notification.
     */
    fun onScan(setting: ThresholdSetting, state: AlertState?, score: Int): Decision {
        if (!setting.enabled) return Decision(state = null, notify = false)
        return when {
            state == null -> Decision(settle(setting, score), notify = false)
            state == AlertState.ARMED && score < setting.threshold -> Decision(AlertState.TRIGGERED, notify = true)
            state == AlertState.TRIGGERED && score >= setting.threshold + REARM_MARGIN -> Decision(AlertState.ARMED, notify = false)
            else -> Decision(state, notify = false)
        }
    }

    /** Activation ou changement de seuil : l'état est recalculé en silence. `null` sans score connu. */
    fun settle(setting: ThresholdSetting, score: Int?): AlertState? = when {
        !setting.enabled || score == null -> null
        score < setting.threshold -> AlertState.TRIGGERED
        else -> AlertState.ARMED
    }

    /** Ajoute un franchissement en gardant les [MAX_CROSSINGS] plus récents. */
    fun append(crossings: List<ThresholdCrossing>, crossing: ThresholdCrossing): List<ThresholdCrossing> =
        (crossings + crossing).sortedBy { it.at }.takeLast(MAX_CROSSINGS)
}
