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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Les trois modules pratiques de l'onglet Agir. */
private enum class ActTab(@param:StringRes val title: Int) {
    REDUCE(R.string.act_tab_reduce),
    ALTERNATIVES(R.string.act_tab_alternatives),
    REWARDS(R.string.act_tab_rewards),
}

/**
 * Agir : en tête, l'analyse globale (score moyen de vos apps et score atteignable en suivant les propositions), puis
 * trois modules : Réduire (autorisations à retirer, apps à supprimer), Alternatives, Récompenses et partage.
 */
@Composable
fun ActScreen(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedRules by viewModel.rules.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    val recentRewards by viewModel.recentRewards.collectAsStateWithLifecycle()
    val usage by actViewModel.usage.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ActTab.REDUCE) }

    // Au retour des réglages Android, l'accès aux données d'utilisation vient peut-être d'être accordé.
    LifecycleResumeEffect(Unit) {
        actViewModel.refreshUsage()
        actViewModel.refreshDnsStatus()
        actViewModel.refreshShieldContext()
        onPauseOrDispose { }
    }

    val userApps = remember(state.scannedApps) { state.scannedApps.filter { AppFilter.USER.matches(it.app) } }
    val scorer = loadedRules.scorer
    val revocations = remember(userApps, scorer) { ReductionAdvisor.revocations(userApps, scorer) }
    val removals = remember(userApps, usage) { ReductionAdvisor.removals(userApps, usage.lastUsed, System.currentTimeMillis()) }
    val projected = remember(userApps, revocations, removals, scorer) {
        ReductionAdvisor.projectedAverage(userApps, revocations, removals, scorer)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.act_title), style = MaterialTheme.typography.headlineMedium)
            if (!state.isLoading) SummaryCard(state.userAverageScore, projected, revocations.size, removals.size)
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            ActTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = {
                        Text(
                            stringResource(entry.title),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                        )
                    },
                )
            }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            tab == ActTab.REDUCE -> ReduceTab(revocations, removals, usage) {
                val dnsCatalogue by actViewModel.dnsCatalogue.collectAsStateWithLifecycle()
                val dnsStatus by actViewModel.dnsStatus.collectAsStateWithLifecycle()
                val dnsListUpdate by actViewModel.dnsListUpdate.collectAsStateWithLifecycle()
                val shield by actViewModel.shield.collectAsStateWithLifecycle()
                val blocklistSize by actViewModel.blocklistSize.collectAsStateWithLifecycle()
                val otherVpn by actViewModel.otherVpn.collectAsStateWithLifecycle()
                PrivateDnsTab(dnsCatalogue, dnsStatus, dnsListUpdate, actViewModel::updateDnsList, shieldOn = shield.status == ShieldStatus.ON) {
                    ShieldCard(
                        state = shield,
                        blocklistSize = blocklistSize,
                        privateDns = dnsStatus,
                        otherVpn = otherVpn,
                        consentIntent = actViewModel::shieldConsentIntent,
                        onStart = actViewModel::startShield,
                        onStop = actViewModel::stopShield,
                        onPause = actViewModel::pauseShield,
                        onResume = actViewModel::resumeShield,
                        onPauseMinutes = actViewModel::setShieldPauseMinutes,
                        onFilter = actViewModel::setShieldFilter,
                    )
                }
            }
            tab == ActTab.ALTERNATIVES -> AlternativesTab(userApps, state.scannedApps, actViewModel.catalogue)
            else -> RewardsTab(state.userAverageScore?.value, rewards, recentRewards, viewModel::dismissRecentRewards)
        }
    }
}

/** Analyse globale : score moyen actuel, score atteignable, et ce qu'il faut faire pour y arriver. */
@Composable
private fun SummaryCard(current: PrivacyScore?, projected: PrivacyScore?, revocations: Int, removals: Int) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            current?.let { ScoreBadge(it) }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.act_summary_title), fontWeight = FontWeight.Bold)
                val reachable = projected?.takeIf { current != null && it.value > current.value }
                Text(
                    if (reachable == null || revocations + removals == 0) stringResource(R.string.act_summary_nothing)
                    else stringResource(R.string.act_summary_reachable, reachable.value) + " · " +
                        pluralStringResource(R.plurals.act_summary_revocations, revocations, revocations) + " · " +
                        pluralStringResource(R.plurals.act_summary_removals, removals, removals),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
