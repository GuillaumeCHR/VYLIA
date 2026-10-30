package com.vylia.privacy.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.ui.home.HomeViewModel
import com.vylia.privacy.ui.home.components.ScoreHeader

@Composable
fun AnalyseScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Analyse", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Score de confidentialité de l'appareil (déplacé depuis l'écran Scan).
        ScoreHeader(
            averageScore = homeState.userAverageScore,
            appCount = homeState.userAppCount,
            filter = AppFilter.USER
        )
        if (homeState.advancedMode) {
            Spacer(modifier = Modifier.height(12.dp))
            ScoreHeader(
                averageScore = homeState.systemAverageScore,
                appCount = homeState.systemAppCount,
                filter = AppFilter.SYSTEM
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Analyse 100% locale basée sur le référentiel Exodus Privacy du ${settingsState.lastUpdateDate}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (settingsState.isObsolete) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠️ La base de données de traqueurs n'est plus à jour (>90 jours).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
