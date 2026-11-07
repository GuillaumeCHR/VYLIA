package com.vylia.privacy.ui.act

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.R
import com.vylia.privacy.domain.act.ReductionAdvisor
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.shield.ShieldStatus
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.scan.ScanViewModel

import androidx.compose.ui.text.style.TextAlign

/** En-tête des écrans Réduire, Remplacer et Célébrer : un titre et ce que l'écran propose. */
@Composable
internal fun ActHeader(@StringRes title: Int, @StringRes subtitle: Int?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Centré dans la zone libre : en attendant le scan, ou quand l'écran n'a rien à montrer. */
@Composable
internal fun ActPlaceholder(text: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (text == null) CircularProgressIndicator()
        else Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Gérer : les sous-onglets pour réduire votre exposition — autorisations à retirer, apps à désinstaller,
 * alternatives. Le score de l'appareil est dans Célébrer, la protection réseau dans Se protéger.
 */
@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    /** App mise en tête des listes (cause d'une alerte de seuil). */
    pinnedPackage: String? = null,
    /** Sous-onglet affiché, gardé par l'écran principal pour toute la session. */
    section: ManageSection = ManageSection.PERMISSIONS,
    onSection: (ManageSection) -> Unit = {},
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedRules by viewModel.rules.collectAsStateWithLifecycle()
    val usage by actViewModel.usage.collectAsStateWithLifecycle()

    // Au retour des réglages Android, l'accès aux données d'utilisation vient peut-être d'être accordé.
    LifecycleResumeEffect(Unit) {
        actViewModel.refreshUsage()
        onPauseOrDispose { }
    }

    val userApps = remember(state.scannedApps) { state.scannedApps.filter { AppFilter.USER.matches(it.app) } }
    val scorer = loadedRules.scorer
    val revocations = remember(userApps, scorer, pinnedPackage) {
        ReductionAdvisor.revocations(userApps, scorer).pinnedFirst(pinnedPackage) { it.app.app.packageName }
    }
    val removals = remember(userApps, usage, pinnedPackage) {
        ReductionAdvisor.removals(userApps, usage.lastUsed, System.currentTimeMillis()).pinnedFirst(pinnedPackage) { it.app.app.packageName }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ActHeader(R.string.manage_title, R.string.manage_subtitle)
        when {
            state.isLoading -> ActPlaceholder(null)
            userApps.isEmpty() -> ActPlaceholder(stringResource(R.string.act_empty_scan))
            else -> ReduceTab(revocations, removals, usage, section, onSection) {
                AlternativesTab(state.scannedApps, userApps, actViewModel.catalogue)
            }
        }
    }
}

/** Les éléments de l'app [pinned] d'abord, dans leur ordre ; le reste ensuite, inchangé. */
private fun <T> List<T>.pinnedFirst(pinned: String?, packageOf: (T) -> String): List<T> =
    if (pinned == null) this else sortedBy { if (packageOf(it) == pinned) 0 else 1 }
