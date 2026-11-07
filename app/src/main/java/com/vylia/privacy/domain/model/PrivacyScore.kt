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
            if (scores.isEmpty()) null else ofAverage(scores.map { it.value }.average())

        /**
         * La seule façon d'arrondir une moyenne de scores, pour que le score de l'appareil soit le même partout
         * (Célébrer, Réduire, Analyse, Suivi, partage, alerte de seuil) : à l'entier le plus proche, 62,5 donnant 63.
         */
        fun ofAverage(average: Double): PrivacyScore = PrivacyScore(average.coerceIn(0.0, 100.0).roundToInt())
    }
}

/**
 * Appréciation globale d'un score, traduite en couleur par la couche UI :
 * vert (>= 80), orange (50-79), rouge (< 50).
 *
 * À ne pas confondre avec [com.vylia.privacy.domain.scoring.RiskLevel], qui qualifie une permission.
 */
enum class ScoreRating { GOOD, FAIR, POOR }
