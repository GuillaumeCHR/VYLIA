package com.vipacy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val VipacyDarkColorScheme = darkColorScheme(
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

/** VIPACY Theme: Force dark mode with Slate/Emerald/Cyan branding */
@Composable
fun VipacyTheme(
    darkTheme: Boolean = true, // Force Dark Theme for VIPACY
    dynamicColor: Boolean = false, // Disable dynamic color to enforce brand identity
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRiskColors provides DarkRiskColors) {
        MaterialTheme(colorScheme = VipacyDarkColorScheme, content = content)
    }
}
