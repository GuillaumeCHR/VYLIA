package com.vylia.privacy.domain.model

import kotlin.math.roundToInt

/** Score de confidentialité de 0 (très intrusif) à 100 (irréprochable). */
@JvmInline
value class PrivacyScore(val value: Int) {
    init {
        require(value in MIN..MAX) { "Score hors bornes : $value" }
    }

    val rating: ScoreRating
        get() = when {
            value >= 80 -> ScoreRating.GOOD
            value >= 50 -> ScoreRating.FAIR
            else -> ScoreRating.POOR
        }

    companion object {
        const val MIN = 0
        const val MAX = 100

        /** Moyenne arrondie des scores, ou `null` s'il n'y a rien à moyenner. */
        fun average(scores: Collection<PrivacyScore>): PrivacyScore? =
            if (scores.isEmpty()) null else PrivacyScore(scores.map { it.value }.average().roundToInt())
    }
}

/**
 * Appréciation globale d'un score, traduite en couleur par la couche UI :
 * vert (>= 80), orange (50-79), rouge (< 50).
 *
 * À ne pas confondre avec [com.vylia.privacy.domain.scoring.RiskLevel], qui qualifie une permission.
 */
enum class ScoreRating { GOOD, FAIR, POOR }
