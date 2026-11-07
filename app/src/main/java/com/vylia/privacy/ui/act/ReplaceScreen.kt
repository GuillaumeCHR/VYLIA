package com.vylia.privacy.ui.act

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.ui.scan.ScanViewModel

/** Remplacer : vos apps intrusives et la catégorie qui les couvre, puis les alternatives par catégorie du Play Store. */
@Composable
fun ReplaceScreen(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userApps = remember(state.scannedApps) { state.scannedApps.filter { AppFilter.USER.matches(it.app) } }
    Column(modifier = modifier.fillMaxSize()) {
        ActHeader(R.string.replace_title, R.string.replace_subtitle)
        // Le catalogue se lit sans scan ; seule la section « vos apps » l'attend.
        if (state.isLoading) ActPlaceholder(null)
        else AlternativesTab(state.scannedApps, userApps, actViewModel.catalogue)
    }
}
