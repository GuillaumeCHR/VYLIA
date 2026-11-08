package com.vylia.privacy.ui.act

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.data.dns.PrivateDnsStatus
import com.vylia.privacy.domain.shield.ShieldFilter
import com.vylia.privacy.domain.shield.ShieldState
import com.vylia.privacy.domain.shield.ShieldStatus
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import android.text.format.DateFormat
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Date

private val Green = Color(0xFF10B981)
private val Amber = Color(0xFFF59E0B)

/**
 * Réduire › Traçage pub : l'interrupteur du Bouclier VYLIA, le compteur de requêtes bloquées en direct, les derniers
 * blocages (en mémoire seulement), la pause réglable (5 min à 2 h) et quand s'en servir, le choix des filtrages
 * (web, mobile), et ce qui peut l'empêcher de fonctionner (DNS privé réglé sur un service, autre VPN).
 */
@Composable
internal fun ShieldCard(
    state: ShieldState,
    blocklistSize: Int?,
    privateDns: PrivateDnsStatus,
    otherVpn: Boolean,
    consentIntent: () -> Intent?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPauseMinutes: (Int) -> Unit,
    onFilter: (ShieldFilter, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val format = NumberFormat.getIntegerInstance(currentLocale())
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onStart()
    }
    val active = state.status == ShieldStatus.ON || state.status == ShieldStatus.STARTING
    // Horloge de l'écran, pour que la fin de la pause s'affiche sans attendre un autre événement.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.pausedUntil) {
        now = System.currentTimeMillis()
        val until = state.pausedUntil ?: return@LaunchedEffect
        while (now < until) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val paused = state.status == ShieldStatus.ON && state.isPaused(now)
    var pauseHelp by rememberSaveable { mutableStateOf(false) }

    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("🧿 " + stringResource(R.string.shield_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.shield_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val toggleLabel = stringResource(R.string.shield_title)
                Switch(
                    checked = active,
                    enabled = state.status != ShieldStatus.STARTING,
                    onCheckedChange = { on ->
                        when {
                            !on -> onStop()
                            else -> consentIntent()?.let(consent::launch) ?: onStart()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = toggleLabel },
                )
            }

            StatusLine(state.status)

            if (state.status == ShieldStatus.ON) {
                Text(
                    pluralStringResource(R.plurals.shield_blocked_session, state.blockedSession.toPluralCount(), format.format(state.blockedSession)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Green,
                )
            }
            if (state.status == ShieldStatus.ON) {
                if (paused) {
                    val until = DateFormat.getTimeFormat(context).format(Date(state.pausedUntil!!))
                    Text(stringResource(R.string.shield_paused_until, until), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Amber)
                }
                // − et + règlent la durée de la prochaine pause, ou prolongent / raccourcissent celle en cours.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onPauseMinutes(ShieldState.shorterPause(state.pauseMinutes)) }, enabled = state.pauseMinutes > ShieldState.PAUSE_STEPS.first()) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.shield_pause_shorter))
                    }
                    if (paused) {
                        Button(onClick = onResume) { Text(stringResource(R.string.shield_resume)) }
                    } else {
                        OutlinedButton(onClick = onPause) { Text(stringResource(R.string.shield_pause, state.pauseMinutes)) }
                    }
                    IconButton(onClick = { onPauseMinutes(ShieldState.longerPause(state.pauseMinutes)) }, enabled = state.pauseMinutes < ShieldState.PAUSE_STEPS.last()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shield_pause_longer))
                    }
                }
            }
            if (state.blockedTotal > 0) {
                Text(
                    stringResource(R.string.shield_blocked_total, format.format(state.blockedTotal)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FiltersSection(state.filters, onFilter)

            // Avertissements : ce qui empêche le bouclier de fonctionner, ou ce qu'il va remplacer.
            if (privateDns is PrivateDnsStatus.Hostname) {
                Text(stringResource(R.string.shield_private_dns_conflict, privateDns.hostname), style = MaterialTheme.typography.bodySmall, color = Amber)
            }
            if (otherVpn && !active) {
                Text(stringResource(R.string.shield_other_vpn), style = MaterialTheme.typography.bodySmall, color = Amber)
            }

            if (state.recent.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.shield_recent_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                state.recent.take(RECENT_SHOWN).forEach { blocked ->
                    Column {
                        Text(blocked.domain, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            blocked.trackerName ?: stringResource(R.string.shield_recent_easyprivacy),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Quand faire une pause : repliée par défaut, la carte est déjà longue.
            TextButton(onClick = { pauseHelp = !pauseHelp }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.shield_pause_title) + if (pauseHelp) "  ▴" else "  ▾")
            }
            if (pauseHelp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.shield_pause_intro), style = MaterialTheme.typography.bodySmall)
                    listOf(
                        R.string.shield_pause_offers,
                        R.string.shield_pause_diagnose,
                    ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.shield_pause_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.shield_how), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.shield_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.shield_always_on), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { openVpnSettings(context) }) { Text(stringResource(R.string.shield_vpn_settings)) }
            blocklistSize?.let {
                Text(
                    stringResource(R.string.shield_lists, format.format(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Que filtrer : web et mobile, avec ce que chacun bloque ; le dernier filtrage actif ne peut pas être coupé. */
@Composable
private fun FiltersSection(filters: Set<ShieldFilter>, onFilter: (ShieldFilter, Boolean) -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(stringResource(R.string.shield_filters_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    listOf(
        Triple(ShieldFilter.WEB, R.string.shield_filter_web, R.string.shield_filter_web_text),
        Triple(ShieldFilter.MOBILE, R.string.shield_filter_mobile, R.string.shield_filter_mobile_text),
    ).forEach { (filter, title, text) ->
        val on = filter in filters
        val label = stringResource(title)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold)
                Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = on,
                enabled = !on || filters.size > 1,
                onCheckedChange = { onFilter(filter, it) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun StatusLine(status: ShieldStatus) {
    val (text, color) = when (status) {
        ShieldStatus.OFF -> stringResource(R.string.shield_status_off) to MaterialTheme.colorScheme.onSurfaceVariant
        ShieldStatus.STARTING -> stringResource(R.string.shield_status_starting) to MaterialTheme.colorScheme.onSurfaceVariant
        ShieldStatus.ON -> stringResource(R.string.shield_status_on) to Green
        ShieldStatus.REVOKED -> stringResource(R.string.shield_status_revoked) to Amber
        ShieldStatus.FAILED -> stringResource(R.string.shield_status_failed) to Amber
    }
    Text(text, fontWeight = FontWeight.Bold, color = color)
}

/** Les règles de pluriel ne distinguent que de petits nombres : au-delà d'Int, « beaucoup » suffit. */
private fun Long.toPluralCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private const val RECENT_SHOWN = 5

private fun openVpnSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        // Écran absent sur certains appareils : l'explication reste affichée.
    }
}
