package com.vylia.privacy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vylia.privacy.domain.security.Untrusted
import com.vylia.privacy.ui.act.ManageSection
import com.vylia.privacy.ui.analysis.AnalyzeSection
import com.vylia.privacy.ui.main.AppTab
import com.vylia.privacy.ui.scan.components.LocalCardStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.ui.scan.ScanViewModel
import com.vylia.privacy.ui.settings.AppLanguage
import com.vylia.privacy.ui.main.MainScreen
import com.vylia.privacy.ui.splash.VyliaSplashScreen
import com.vylia.privacy.ui.theme.VyliaTheme

/** Activité unique. `AppCompatActivity` pour la langue par application sur les versions d'Android antérieures à 13. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as VyliaApplication).container.settingsRepository
        AppLanguage.applyDefault(settings)
        enableEdgeToEdge()
        setContent {
            val cardStyle by settings.cardStyle.collectAsStateWithLifecycle()
            VyliaTheme { CompositionLocalProvider(LocalCardStyle provides cardStyle) {
                // Instancié dès le splash : le scan démarre pendant l'animation. Même instance que l'écran Scan.
                viewModel<ScanViewModel>(factory = ScanViewModel.Factory)
                // Ouverte depuis une notification d'installation : directement la fiche de l'app, sans splash.
                val openPackage = intent.getStringExtra(EXTRA_OPEN_PACKAGE)?.takeIf(Untrusted::isPackage)
                // Ouverte depuis une alerte de seuil : Gérer, l'app en cause en tête. L'activité est exportée :
                // seuls un onglet connu et un nom de paquet valide sont repris. « reduce » : notifications
                // envoyées avant que Réduire devienne Gérer.
                val tabExtra = intent.getStringExtra(EXTRA_OPEN_TAB)
                val openTab = when (tabExtra) {
                    TAB_MANAGE, "reduce" -> AppTab.MANAGE
                    TAB_TRACKING -> AppTab.ANALYZE
                    else -> null
                }
                val manageSection = when (intent.getStringExtra(EXTRA_OPEN_SECTION)) {
                    SECTION_APPS -> ManageSection.REMOVALS
                    SECTION_PERMISSIONS -> ManageSection.PERMISSIONS
                    else -> null
                }
                val analyzeSection = if (tabExtra == TAB_TRACKING) AnalyzeSection.TRACKING else null
                val pinPackage = intent.getStringExtra(EXTRA_PIN_PACKAGE)?.takeIf(Untrusted::isPackage)
                var showSplash by rememberSaveable { mutableStateOf(openPackage == null && openTab == null) }
                // Fondu court : le logotype de l'intro est déjà à la place du titre de l'écran Scan.
                Crossfade(targetState = showSplash, animationSpec = tween(250), label = "splash") { splash ->
                    if (splash) VyliaSplashScreen(onFinished = { showSplash = false }) else MainScreen(
                        initialPackage = openPackage,
                        initialTab = openTab,
                        initialManageSection = manageSection,
                        initialAnalyzeSection = analyzeSection,
                        pinnedPackage = pinPackage,
                    )
                }
            } }
        }
    }

    companion object {
        /** Paquet dont la fiche s'ouvre au lancement (notification d'installation). */
        const val EXTRA_OPEN_PACKAGE = "com.vylia.privacy.OPEN_PACKAGE"
        /** Onglet ouvert au lancement ([TAB_MANAGE] seulement) et app mise en tête de Gérer (alerte de seuil). */
        const val EXTRA_OPEN_TAB = "com.vylia.privacy.OPEN_TAB"
        const val EXTRA_PIN_PACKAGE = "com.vylia.privacy.PIN_PACKAGE"
        const val TAB_MANAGE = "manage"
        /** Analyser › Suivi. */
        const val TAB_TRACKING = "tracking"
        /** Sous-onglet de Gérer : [SECTION_APPS] ou [SECTION_PERMISSIONS]. */
        const val EXTRA_OPEN_SECTION = "com.vylia.privacy.OPEN_SECTION"
        const val SECTION_APPS = "apps"
        const val SECTION_PERMISSIONS = "permissions"
    }
}
