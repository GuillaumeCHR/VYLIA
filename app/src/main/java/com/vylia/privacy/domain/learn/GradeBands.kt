package com.vylia.privacy.domain.learn

import androidx.annotation.StringRes
import com.vylia.privacy.R
import com.vylia.privacy.domain.scoring.Scoring
import kotlin.math.floor

/**
 * Tranche de score d'une note, avec ce que la formule impose pour y tomber.
 *
 * Les pénalités étant plafonnées, descendre sous un score donné exige un minimum de chaque source :
 * par exemple, les permissions seules (plafond 50) ne peuvent pas faire passer une app sous 50.
 */
data class GradeBand(
    val grade: String,
    val min: Int,
    val max: Int,
    /** Pour obtenir au plus [max] points, il faut plus de ce nombre de points de permissions ; `null` : aucune exigence. */
    val permissionPenaltyAbove: Int?,
    /** Idem pour les traqueurs : `0` signifie qu'au moins un point de traqueurs est indispensable. */
    val trackerPenaltyAbove: Int?,
)

/** Niveau de risque associé à une note. */
@StringRes
fun gradeRiskLabel(grade: String): Int = when (grade.removeSuffix("?")) {
    "A" -> R.string.risk_level_low
    "B" -> R.string.risk_level_moderate
    "C" -> R.string.risk_level_significant
    "D" -> R.string.risk_level_high
    "E" -> R.string.risk_level_critical
    else -> R.string.risk_level_unknown
}

object GradeBands {

    /** Tranches de la meilleure note à la moins bonne. */
    fun from(scoring: Scoring): List<GradeBand> {
        val grades = scoring.grades.sortedByDescending { it.min }
        return grades.mapIndexed { index, grade ->
            val upper = if (index == 0) scoring.base else grades[index - 1].min - 1
            // Score arrondi ≤ upper ⇔ pénalité totale > base − upper − 0,5 ; chaque source est plafonnée.
            val requiredPenalty = scoring.base - upper - 0.5
            GradeBand(
                grade = grade.grade,
                min = grade.min,
                max = upper,
                permissionPenaltyAbove = (requiredPenalty - scoring.trackerCap).takeIf { it >= 0 }?.let { floor(it).toInt() },
                trackerPenaltyAbove = (requiredPenalty - scoring.permissionCap).takeIf { it >= 0 }?.let { floor(it).toInt() },
            )
        }
    }
}
