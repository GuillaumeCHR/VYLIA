package com.vylia.privacy.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.ui.detail.AppDetailScreen
import com.vylia.privacy.ui.home.HomeScreen
import com.vylia.privacy.ui.home.HomeViewModel

enum class AppTab(val title: String, val icon: ImageVector) {
    SCAN("Scan", Icons.Default.Search),
    ANALYZE("Analyse", Icons.Default.List),
    AGIR("Agir", Icons.Default.Star),
    LEARN("Apprendre", Icons.Default.Info),
    SETTINGS("Paramètres", Icons.Default.Settings)
}

@Composable
fun MainScreen(homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    var currentTab by remember { mutableStateOf(AppTab.SCAN) }
    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val rules by homeViewModel.rules.collectAsStateWithLifecycle()
    // Toujours la version la plus récente de l'app (le score évolue pendant l'analyse des traqueurs).
    val detailApp = detailPackage?.let { pkg -> homeState.scannedApps.find { it.app.packageName == pkg } }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {
                            currentTab = tab
                            detailPackage = null
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        if (detailApp != null) {
            AppDetailScreen(
                auditedApp = detailApp,
                categoryName = homeViewModel.categoryName(detailApp.result.categoryId),
                gradeBands = GradeBands.from(rules.rules.scoring),
                scoreBase = rules.rules.scoring.base,
                onBack = { detailPackage = null },
                modifier = modifier,
            )
            return@Scaffold
        }
        when (currentTab) {
            AppTab.SCAN -> HomeScreen(modifier, onAppClick = { detailPackage = it.app.packageName })
            AppTab.ANALYZE -> AnalyseScreen(modifier, onAppClick = { detailPackage = it.app.packageName })
            AppTab.AGIR -> AgirScreen(modifier)
            AppTab.LEARN -> ApprendreScreen(modifier)
            AppTab.SETTINGS -> SettingsScreen(modifier)
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
