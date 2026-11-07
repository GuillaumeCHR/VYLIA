package com.vylia.privacy.ui.act

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.R
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.trackerCategoryName
import com.vylia.privacy.ui.scan.ScanViewModel
import com.vylia.privacy.ui.share.ShareContent
import kotlin.math.abs

/** Célébrer : le score de l'appareil et son évolution, les trophées, puis le partage (score et progrès seulement). */
@Composable
fun CelebrateScreen(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    val recentRewards by viewModel.recentRewards.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val shield by actViewModel.shield.collectAsStateWithLifecycle()
    val userApps = remember(state.scannedApps) { state.scannedApps.filter { AppFilter.USER.matches(it.app) } }
    val resources = LocalResources.current

    Column(modifier = modifier.fillMaxSize()) {
        ActHeader(R.string.celebrate_title, null)
        when {
            state.isLoading -> ActPlaceholder(null)
            else -> {
                // Calculé à l'ouverture du partage seulement (analyse des traqueurs de toutes les apps).
                val shareContent = {
                    state.userAverageScore?.value?.let { score ->
                        ShareContent.from(score, userApps, history, rewards, shield.blockedTotal) { resources.trackerCategoryName(it) }
                    }
                }
                RewardsTab(shareContent, rewards, recentRewards, viewModel::dismissRecentRewards) {
                    DeviceScoreCard(state.userAverageScore, history)
                }
            }
        }
    }
}

/** Score moyen des apps utilisateur et son évolution sur trente jours (ou depuis le premier scan). */
@Composable
private fun DeviceScoreCard(score: PrivacyScore?, history: History) {
    val progress = remember(history) { history.progress() }
    val progressText = when {
        score == null -> stringResource(R.string.act_empty_scan)
        progress == null -> stringResource(R.string.celebrate_progress_none)
        progress.points == 0 -> stringResource(
            if (progress.sinceStart) R.string.celebrate_progress_stable_start else R.string.celebrate_progress_stable_month
        )
        else -> {
            val n = abs(progress.points)
            val sign = if (progress.points > 0) "+" else "−"
            pluralStringResource(
                if (progress.sinceStart) R.plurals.celebrate_progress_start else R.plurals.celebrate_progress_month, n, sign, n,
            )
        }
    }
    val title = stringResource(R.string.celebrate_device_score)
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            score?.let { ScoreBadge(it) }
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        progress == null || progress.points == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                        progress.points > 0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
