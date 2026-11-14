package com.vylia.privacy.domain.widget

import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.shield.ShieldIndicator
import com.vylia.privacy.domain.shield.ShieldIndicatorState

/** État du bouclier tel que le montre le widget. */
enum class WidgetShield { ACTIVE, PAUSED, STARTING, OFF, BYPASSED }

/**
 * Effet du bouton du widget. [OPEN_APP] : l'action a besoin d'un écran (accord VPN d'Android, motif d'un
 * contournement), le bouton ouvre alors VYLIA sur Filtrer.
 */
enum class WidgetAction { PAUSE, RESUME, ACTIVATE, OPEN_APP, NONE }

/**
 * Ce qu'affiche le widget d'écran d'accueil : le score du téléphone et sa note, l'état du bouclier et les requêtes
 * bloquées aujourd'hui. Jamais de nom d'app : l'écran d'accueil est vu par l'entourage.
 *
 * @param score score du téléphone au dernier scan, `null` avant le premier (ou après « Supprimer mes données »).
 */
data class WidgetState(
    val score: Int?,
    val grade: String?,
    val shield: WidgetShield,
    /** Fin de la pause en cours, pour l'afficher (« jusqu'à 20:45 »). */
    val pausedUntil: Long?,
    val blockedToday: Long,
    val action: WidgetAction,
) {
    val rating: ScoreRating? get() = score?.let { PrivacyScore(it).rating }

    companion object {
        /**
         * @param needsConsent Android n'a pas encore donné l'accord VPN : l'activation passe par l'app.
         */
        fun of(
            score: Int?,
            grade: String?,
            indicator: ShieldIndicator,
            pausedUntil: Long?,
            blockedToday: Long,
            needsConsent: Boolean,
            now: Long,
        ): WidgetState {
            val paused = pausedUntil?.takeIf { it > now }
            val shield = when (indicator.state) {
                ShieldIndicatorState.ACTIVE -> if (paused != null) WidgetShield.PAUSED else WidgetShield.ACTIVE
                ShieldIndicatorState.STARTING -> WidgetShield.STARTING
                ShieldIndicatorState.INACTIVE -> WidgetShield.OFF
                ShieldIndicatorState.BYPASSED -> WidgetShield.BYPASSED
            }
            val action = when (shield) {
                WidgetShield.ACTIVE -> WidgetAction.PAUSE
                WidgetShield.PAUSED -> WidgetAction.RESUME
                WidgetShield.STARTING -> WidgetAction.NONE
                WidgetShield.OFF -> if (needsConsent) WidgetAction.OPEN_APP else WidgetAction.ACTIVATE
                WidgetShield.BYPASSED -> WidgetAction.OPEN_APP
            }
            return WidgetState(score, grade, shield, paused, blockedToday, action)
        }
    }
}
