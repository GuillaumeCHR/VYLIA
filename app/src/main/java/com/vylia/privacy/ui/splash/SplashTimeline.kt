package com.vylia.privacy.ui.splash

import kotlin.math.cos
import kotlin.math.exp

/**
 * Découpage de l'intro « scan » (millisecondes), sur le modèle de l'intro de Molkky Board : mise en place,
 * action, image forte, signature Yogi Studio, puis le logotype qui claque et rejoint l'en-tête.
 */
internal object SplashTimeline {
    /** 1. Mise en place : la grille d'applis apparaît rangée par rangée, avec un rebond. */
    const val SETUP_END = 600L

    /** 2. Le scan : la loupe parcourt la grille en serpentin ; chaque appli révèle sa vraie nature au passage. */
    const val SCAN_END = 2_000L

    /** 3. Les applis intrusives (petits espions) s'éclipsent en flottant, les saines restent. */
    const val EJECT = 2_050L
    const val EJECT_END = 2_600L

    /** 4. La signature Yogi Studio. */
    const val SIGNATURE_END = 3_600L

    /** 5. Le logo VYLIA (l'icône de l'app) claque au centre, puis le logotype glisse vers l'en-tête. */
    const val LOGO_IN = 3_600L
    const val LOGO_SHOWN = 3_950L
    const val MOVE_START = 4_150L
    const val END = 4_600L

    /** Version « Réduire les animations » : simple fondu du logo. */
    const val REDUCED_END = 1_000L
}

internal fun progress(time: Long, from: Long, to: Long): Float =
    ((time - from).toFloat() / (to - from)).coerceIn(0f, 1f)

/** Courbe de ressort amorti (0 → 1 avec léger dépassement), calculée sans allocation. */
internal fun springCurve(p: Float): Float = if (p <= 0f) 0f else 1f - exp(-6f * p) * cos(10f * p)

internal fun easeOutCubic(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

internal fun easeInOutCubic(p: Float): Float =
    if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).let { it * it * it } / 2f
