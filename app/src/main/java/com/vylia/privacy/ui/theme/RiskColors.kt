package com.vylia.privacy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vylia.privacy.domain.model.ScoreRating

/** Paire couleur d'accent / conteneur, sur le modèle des rôles Material 3 (`primary` / `primaryContainer`). */
@Immutable
data class RiskColor(val accent: Color, val container: Color, val onContainer: Color)

@Immutable
data class RiskColors(val low: RiskColor, val medium: RiskColor, val high: RiskColor)

internal val LightRiskColors = RiskColors(
    low = RiskColor(RiskLowLight, RiskLowContainerLight, Color(0xFF00210E)),
    medium = RiskColor(RiskMediumLight, RiskMediumContainerLight, Color(0xFF2C1700)),
    high = RiskColor(RiskHighLight, RiskHighContainerLight, Color(0xFF410002)),
)

internal val DarkRiskColors = RiskColors(
    low = RiskColor(RiskLowDark, RiskLowContainerDark, Color(0xFFA6F6BA)),
    medium = RiskColor(RiskMediumDark, RiskMediumContainerDark, Color(0xFFFFDDB5)),
    high = RiskColor(RiskHighDark, RiskHighContainerDark, Color(0xFFFFDAD6)),
)

internal val LocalRiskColors = staticCompositionLocalOf { LightRiskColors }

/** Vert (>= 80), Orange (50-79), Rouge (< 50). */
val ScoreRating.color: RiskColor
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ScoreRating.GOOD -> LocalRiskColors.current.low
        ScoreRating.FAIR -> LocalRiskColors.current.medium
        ScoreRating.POOR -> LocalRiskColors.current.high
    }
