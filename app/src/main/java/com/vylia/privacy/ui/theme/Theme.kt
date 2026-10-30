package com.vylia.privacy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val VyliaDarkColorScheme = darkColorScheme(
    primary = Emerald,
    secondary = CyanAccent,
    background = Slate900,
    surface = Slate800,
    onPrimary = Slate900,
    onSecondary = Slate900,
    onBackground = Slate300,
    onSurface = Slate300,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400
)

/** VYLIA Theme: Force dark mode with Slate/Emerald/Cyan branding */
@Composable
fun VyliaTheme(
    darkTheme: Boolean = true, // Force Dark Theme for VYLIA
    dynamicColor: Boolean = false, // Disable dynamic color to enforce brand identity
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRiskColors provides DarkRiskColors) {
        MaterialTheme(colorScheme = VyliaDarkColorScheme, content = content)
    }
}
