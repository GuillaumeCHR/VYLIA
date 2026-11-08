package com.vylia.privacy.ui.act

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.shield.ShieldStatus
import com.vylia.privacy.ui.scan.ScanViewModel

/** Onglets de Filtrer : le filtrage DNS (bouclier, DNS privé) et la version web des apps. Mémorisé pour la session. */
enum class FilterSection(@param:StringRes val title: Int) {
    DNS(R.string.protect_section_dns),
    WEBAPK(R.string.manage_tab_webapk),
}

/**
 * Filtrer : réduire l'exposition sans modifier les apps. Le filtrage DNS (bouclier VYLIA et DNS privé ; sans le
 * module :shield, seulement le DNS privé) et le remplacement d'une app par sa version web (WebAPK).
 */
@Composable
fun ProtectScreen(
    modifier: Modifier = Modifier,
    section: FilterSection = FilterSection.DNS,
    onSection: (FilterSection) -> Unit = {},
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
    scanViewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    // Au retour des réglages Android, le DNS privé ou le VPN ont pu changer.
    LifecycleResumeEffect(Unit) {
        actViewModel.refreshDnsStatus()
        actViewModel.refreshShieldContext()
        onPauseOrDispose { }
    }
    Column(modifier = modifier.fillMaxSize()) {
        ActHeader(R.string.protect_title, R.string.protect_subtitle)
        SecondaryTabRow(selectedTabIndex = section.ordinal) {
            FilterSection.entries.forEach { entry ->
                Tab(selected = section == entry, onClick = { onSection(entry) }, text = { Text(stringResource(entry.title)) })
            }
        }
        when (section) {
            FilterSection.DNS -> DnsFiltering(actViewModel)
            FilterSection.WEBAPK -> WebApk(scanViewModel)
        }
    }
}

@Composable
private fun WebApk(scanViewModel: ScanViewModel) {
    val context = LocalContext.current
    val state by scanViewModel.uiState.collectAsStateWithLifecycle()
    val webServices = remember(context) { (context.applicationContext as VyliaApplication).container.webServicesRepository.services }
    val userApps = remember(state.scannedApps) { state.scannedApps.filter { AppFilter.USER.matches(it.app) } }
    when {
        state.isLoading -> ActPlaceholder(null)
        else -> WebApkTab(userApps, webServices)
    }
}

@Composable
private fun DnsFiltering(actViewModel: ActViewModel) {
    val dnsCatalogue by actViewModel.dnsCatalogue.collectAsStateWithLifecycle()
    val dnsStatus by actViewModel.dnsStatus.collectAsStateWithLifecycle()
    val dnsListUpdate by actViewModel.dnsListUpdate.collectAsStateWithLifecycle()
    val shield by actViewModel.shield.collectAsStateWithLifecycle()
    val blocklistSize by actViewModel.blocklistSize.collectAsStateWithLifecycle()
    val otherVpn by actViewModel.otherVpn.collectAsStateWithLifecycle()
    PrivateDnsTab(dnsCatalogue, dnsStatus, dnsListUpdate, actViewModel::updateDnsList, shieldOn = shield.status == ShieldStatus.ON) {
        // Sans le module :shield, le DNS privé reste seul.
        if (BuildConfig.SHIELD_ENABLED) {
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
                company = actViewModel::company,
            )
        }
    }
}
