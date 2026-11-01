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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.vylia.privacy.domain.shield.ShieldState
import com.vylia.privacy.domain.shield.ShieldStatus
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import java.text.NumberFormat

private val Green = Color(0xFF10B981)
private val Amber = Color(0xFFF59E0B)

/**
 * Réduire › Traçage pub : l'interrupteur du Bouclier VYLIA, le compteur de requêtes bloquées en direct, les derniers
 * blocages (en mémoire seulement), et ce qui peut l'empêcher de fonctionner (DNS privé réglé sur un service, autre VPN).
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
) {
    val context = LocalContext.current
    val format = NumberFormat.getIntegerInstance(currentLocale())
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onStart()
    }
    val active = state.status == ShieldStatus.ON || state.status == ShieldStatus.STARTING

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
            if (state.blockedTotal > 0) {
                Text(
                    stringResource(R.string.shield_blocked_total, format.format(state.blockedTotal)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
