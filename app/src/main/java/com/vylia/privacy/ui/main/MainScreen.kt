package com.vylia.privacy.ui.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.ui.act.CelebrateScreen
import com.vylia.privacy.ui.act.ManageScreen
import com.vylia.privacy.ui.act.ManageSection
import com.vylia.privacy.ui.analysis.AnalyzeSection
import com.vylia.privacy.ui.act.ProtectScreen
import com.vylia.privacy.ui.shield.LocalOpenProtect
import com.vylia.privacy.ui.analysis.AnalysisScreen
import com.vylia.privacy.ui.components.categoryName
import com.vylia.privacy.ui.detail.AppDetailScreen
import com.vylia.privacy.ui.scan.ScanScreen
import com.vylia.privacy.ui.scan.ScanViewModel
import com.vylia.privacy.ui.search.GlobalSearchScreen
import com.vylia.privacy.ui.settings.SettingsScreen
import com.vylia.privacy.ui.help.HelpSheet

/** Les cinq destinations de la barre du bas (icônes Material Symbols embarquées). */
enum class AppTab(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    SCAN(R.string.tab_scan, R.drawable.ic_sym_radar),
    ANALYZE(R.string.tab_analyze, R.drawable.ic_sym_bar_chart),
    MANAGE(R.string.tab_manage, R.drawable.ic_sym_tune),
    PROTECT(R.string.tab_protect, R.drawable.ic_sym_shield),
    CELEBRATE(R.string.tab_celebrate, R.drawable.ic_sym_celebration);

    companion object {
        /** Les destinations de la barre : sans le bouclier, Se protéger disparaît et il en reste quatre. */
        fun destinations(shieldEnabled: Boolean): List<AppTab> = entries.filter { shieldEnabled || it != PROTECT }
    }
}

/** Écrans ouverts par-dessus l'onglet en cours depuis la barre du haut. */
private enum class Overlay { SEARCH, SETTINGS }

@Composable
fun MainScreen(
    initialPackage: String? = null,
    initialTab: AppTab? = null,
    initialManageSection: ManageSection? = null,
    initialAnalyzeSection: AnalyzeSection? = null,
    pinnedPackage: String? = null,
    homeViewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    // Survit à la recréation de l'activité (changement de langue) : on reste sur l'onglet en cours.
    var currentTab by rememberSaveable {
        mutableStateOf(initialTab?.takeIf { it in AppTab.destinations(BuildConfig.SHIELD_ENABLED) } ?: AppTab.SCAN)
    }
    // Dernier sous-onglet ouvert de Gérer et d'Analyser : gardé pour la session, même en changeant de destination.
    var manageSection by rememberSaveable { mutableStateOf(initialManageSection ?: ManageSection.PERMISSIONS) }
    var analyzeSection by rememberSaveable { mutableStateOf(initialAnalyzeSection ?: AnalyzeSection.OVERVIEW) }
    // L'app mise en tête de Gérer ne vaut que pour l'ouverture depuis la notification.
    var pinned by rememberSaveable { mutableStateOf(pinnedPackage) }
    // Paramètres ouverts directement sur Automatisations (lien « Modifier » du Suivi).
    var openAutomation by rememberSaveable { mutableStateOf(false) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // L'Aide, en feuille par-dessus l'écran affiché, qui reste en place dessous.
    var help by rememberSaveable { mutableStateOf(false) }
    var detailPackage by rememberSaveable { mutableStateOf(initialPackage) }
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val rules by homeViewModel.rules.collectAsStateWithLifecycle()
    // Toujours la version la plus récente de l'app (le score évolue pendant l'analyse des traqueurs).
    val detailApp = detailPackage?.let { pkg -> homeState.scannedApps.find { it.app.packageName == pkg } }

    val closeOverlay = {
        overlay = null
        query = ""
        openAutomation = false
    }
    // Fiche d'une app d'abord (son propre BackHandler), puis l'écran superposé (les sous-pages des paramètres ont le leur).
    BackHandler(enabled = overlay != null && detailApp == null) { closeOverlay() }
    // La flèche de la barre fait exactement comme le geste retour d'Android.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val back: () -> Unit = { dispatcher?.onBackPressed() }
    // La feuille « Bouclier contourné » de la pastille renvoie à Se protéger, d'où qu'on l'ouvre.
    val openProtect = {
        detailPackage = null
        closeOverlay()
        currentTab = AppTab.PROTECT
    }

    CompositionLocalProvider(LocalOpenProtect provides openProtect) {
    Scaffold(
        topBar = {
            when {
                detailApp != null -> BackTopBar(R.string.detail_title, back)
                overlay == Overlay.SEARCH -> SearchTopBar(query, { query = it }, back)
                overlay == Overlay.SETTINGS -> BackTopBar(R.string.settings_title, back)
                else -> MainTopBar(
                    onSearch = { overlay = Overlay.SEARCH },
                    onHelp = { help = true },
                    onSettings = { overlay = Overlay.SETTINGS },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.destinations(BuildConfig.SHIELD_ENABLED).forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab && overlay == null,
                        onClick = {
                            currentTab = tab
                            pinned = null
                            detailPackage = null
                            closeOverlay()
                        },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        // Une seule ligne : le libellé rétrécit plutôt que de se couper (« Remplacer », grande police).
                        label = {
                            Text(
                                stringResource(tab.title),
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (help) HelpSheet(onDismiss = { help = false })
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
        when (overlay) {
            Overlay.SEARCH -> GlobalSearchScreen(homeState.scannedApps, query, onAppClick = { detailPackage = it.app.packageName }, modifier)
            Overlay.SETTINGS -> SettingsScreen(modifier, openAutomation = openAutomation)
            null -> when (currentTab) {
                AppTab.SCAN -> ScanScreen(modifier, onAppClick = { detailPackage = it.app.packageName })
                AppTab.ANALYZE -> AnalysisScreen(
                    modifier,
                    onAppClick = { detailPackage = it.app.packageName },
                    onOpenAutomation = { openAutomation = true; overlay = Overlay.SETTINGS },
                    onOpenReduce = { currentTab = AppTab.MANAGE },
                    tab = analyzeSection,
                    onTab = { analyzeSection = it },
                )
                AppTab.MANAGE -> ManageScreen(modifier, pinnedPackage = pinned, section = manageSection, onSection = { manageSection = it })
                AppTab.PROTECT -> ProtectScreen(modifier)
                AppTab.CELEBRATE -> CelebrateScreen(modifier)
            }
        }
    }
    }
}
