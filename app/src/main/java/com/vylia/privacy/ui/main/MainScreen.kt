package com.vylia.privacy.ui.main

import androidx.annotation.StringRes
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.ui.components.categoryName
import com.vylia.privacy.R
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.ui.detail.AppDetailScreen
import com.vylia.privacy.ui.home.HomeScreen
import com.vylia.privacy.ui.home.HomeViewModel

enum class AppTab(@param:StringRes val title: Int, val icon: ImageVector) {
    SCAN(R.string.tab_scan, Icons.Default.Search),
    ANALYZE(R.string.tab_analyze, Icons.Default.List),
    AGIR(R.string.tab_act, Icons.Default.Star),
    LEARN(R.string.tab_learn, Icons.Default.Info),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    // Survit à la recréation de l'activité (changement de langue) : on reste sur l'onglet en cours.
    var currentTab by rememberSaveable { mutableStateOf(AppTab.SCAN) }
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
                        icon = { Icon(tab.icon, contentDescription = null) },
                        // Une seule ligne : le libellé rétrécit plutôt que de se couper (« Comprendre », grande police).
                        label = {
                            Text(
                                stringResource(tab.title),
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        if (detailApp != null) {
            AppDetailScreen(
                auditedApp = detailApp,
                categoryName = LocalResources.current.categoryName(detailApp.result.categoryId, rules.rules),
                gradeBands = GradeBands.from(rules.rules.scoring),
                scoreBase = rules.rules.scoring.base,
                onBack = { detailPackage = null },
                modifier = modifier,
            )
            return@Scaffold
        }
        when (currentTab) {
            // L'écran Scan a sa propre barre d'en-tête (sous la barre d'état) : seule la marge du bas lui est utile.
            AppTab.SCAN -> HomeScreen(
                Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                onAppClick = { detailPackage = it.app.packageName },
            )
            AppTab.ANALYZE -> AnalyseScreen(modifier, onAppClick = { detailPackage = it.app.packageName })
            AppTab.AGIR -> AgirScreen(modifier)
            AppTab.LEARN -> ComprendreScreen(modifier)
            AppTab.SETTINGS -> SettingsScreen(modifier, onOpenLearn = { currentTab = AppTab.LEARN })
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
