package com.vylia.privacy.ui.act

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.R
import com.vylia.privacy.domain.shield.ShieldStatus

/**
 * Sections de Se protéger. Une seule pour l'instant (le filtrage DNS : bouclier et DNS privé) : l'écran n'affiche pas
 * de barre d'onglets tant qu'il n'y en a qu'une, et le titre de l'écran suffit.
 */
private enum class ProtectSection(val title: Int) {
    DNS_FILTERING(R.string.protect_section_dns),
}

/**
 * Se protéger : mesures compensatoires, qui réduisent l'exposition sans modifier les apps. Aujourd'hui le filtrage DNS
 * (bouclier VYLIA et DNS privé). Toute la destination disparaît d'une variante sans le module :shield.
 */
@Composable
fun ProtectScreen(
    modifier: Modifier = Modifier,
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
) {
    // Au retour des réglages Android, le DNS privé ou le VPN ont pu changer.
    LifecycleResumeEffect(Unit) {
        actViewModel.refreshDnsStatus()
        actViewModel.refreshShieldContext()
        onPauseOrDispose { }
    }
    var section by rememberSaveable { mutableStateOf(ProtectSection.DNS_FILTERING) }
    Column(modifier = modifier.fillMaxSize()) {
        ActHeader(R.string.protect_title, R.string.protect_subtitle)
        if (ProtectSection.entries.size > 1) {
            SecondaryTabRow(selectedTabIndex = section.ordinal) {
                ProtectSection.entries.forEach { entry ->
                    Tab(selected = section == entry, onClick = { section = entry }, text = { Text(stringResource(entry.title)) })
                }
            }
        }
        when (section) {
            ProtectSection.DNS_FILTERING -> DnsFiltering(actViewModel)
        }
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
