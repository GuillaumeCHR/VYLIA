package com.vylia.privacy.ui.act

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.data.dns.PrivateDnsStatus
import com.vylia.privacy.domain.act.DnsResolver
import com.vylia.privacy.domain.act.PrivateDnsCatalogue
import com.vylia.privacy.domain.act.localized
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.settings.openUrl
import java.util.Locale

/**
 * Réduire › DNS privé : ce que c'est (et ce que ce n'est pas), les services proposés (liste de données, modifiable sans
 * mise à jour de l'app), comment l'activer dans Android, et comment revenir en arrière.
 */
@Composable
internal fun PrivateDnsTab(
    catalogue: PrivateDnsCatalogue,
    status: PrivateDnsStatus,
    listUpdate: DnsListUpdate?,
    onUpdateList: () -> Unit,
    /** Bouclier VYLIA actif : c'est lui qui reçoit les requêtes DNS, l'état « Désactivé » d'Android serait trompeur. */
    shieldOn: Boolean,
    shield: @Composable () -> Unit,
) {
    val language = currentLocale().language
    // Région du téléphone (réglage de langue et région), jamais sa position.
    val resolvers = catalogue.availableIn(Locale.getDefault().country)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // D'abord la différence entre les deux outils, puis le bouclier (un geste suffit), puis le DNS privé.
        item(key = "intro") { IntroCard() }
        item(key = "shield") { shield() }
        item { StatusCard(status, catalogue, shieldOn) }
        item { WhyCard() }
        item { ExplanationCard() }
        if (status != PrivateDnsStatus.Unsupported) {
            item { SectionHeader(stringResource(R.string.dns_services_title), stringResource(R.string.dns_services_hint), Modifier.padding(top = 8.dp)) }
            items(resolvers, key = { "dns-${it.id}" }) { ResolverCard(it, language) }
            if (resolvers.isEmpty()) item { Text(stringResource(R.string.dns_services_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { StepsCard() }
        }
        item { FallbackCard() }
        item { ListCard(catalogue, listUpdate, onUpdateList) }
    }
}

@Composable
private fun StatusCard(status: PrivateDnsStatus, catalogue: PrivateDnsCatalogue, shieldOn: Boolean) {
    val (text, color) = when {
        // Le réseau vu par Android est alors le tunnel du bouclier, sans DNS privé : afficher qui traite les requêtes.
        shieldOn && status !is PrivateDnsStatus.Hostname -> stringResource(R.string.dns_status_shield) to Color(0xFF10B981)
        else -> statusText(status, catalogue)
    }
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.dns_status_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun statusText(status: PrivateDnsStatus, catalogue: PrivateDnsCatalogue): Pair<String, Color> =
    when (status) {
        PrivateDnsStatus.Unsupported -> stringResource(R.string.dns_status_unsupported) to MaterialTheme.colorScheme.onSurfaceVariant
        PrivateDnsStatus.NoNetwork -> stringResource(R.string.dns_status_no_network) to MaterialTheme.colorScheme.onSurfaceVariant
        PrivateDnsStatus.Off -> stringResource(R.string.dns_status_off) to Color(0xFFF59E0B)
        PrivateDnsStatus.Automatic -> stringResource(R.string.dns_status_automatic) to Color(0xFFF59E0B)
        is PrivateDnsStatus.Hostname -> {
            val known = catalogue.resolvers.firstOrNull { r ->
                r.hostname == status.hostname || r.hostnameTemplate?.let { status.hostname.endsWith(it.substringAfter("{id}")) } == true
            }
            stringResource(R.string.dns_status_hostname, known?.name ?: status.hostname) to Color(0xFF10B981)
        }
    }

/** Bouclier VYLIA ou DNS privé : ce que fait chacun, et pourquoi c'est l'un ou l'autre. */
@Composable
private fun IntroCard() {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.adtrack_intro_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.adtrack_intro_text), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.adtrack_intro_shield), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.adtrack_intro_private_dns), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.adtrack_intro_choice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Le lien avec le reste de VYLIA : les traqueurs détectés dans vos apps, et ce qu'un DNS filtrant y change. */
@Composable
private fun WhyCard() {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dns_why_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.dns_why_block), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.dns_why_encrypt), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.dns_why_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExplanationCard() {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dns_what_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            listOf(
                R.string.dns_what_directory,
                R.string.dns_what_clear,
                R.string.dns_what_private,
                R.string.dns_what_once,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            // Ce que le DNS privé ne fait pas : ne jamais laisser croire à un VPN.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.dns_what_not_vpn),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolverCard(resolver: DnsResolver, language: String) {
    val context = LocalContext.current
    var accountId by rememberSaveable(resolver.id) { mutableStateOf("") }
    val hostname = resolver.hostnameFor(accountId)
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(resolver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (resolver.recommended) Tag(stringResource(R.string.dns_recommended), Color(0xFF10B981))
                if (resolver.requiresAccount) Tag(stringResource(R.string.dns_account_required), Color(0xFF94A3B8))
            }
            Text(resolver.summary.localized(language), style = MaterialTheme.typography.bodyMedium)
            resolver.localizedPoints(language).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }

            if (resolver.hostname == null) {
                // Nom d'hôte propre au compte : l'utilisateur colle son identifiant ou son nom d'hôte.
                Text(stringResource(R.string.dns_account_hint, resolver.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = accountId,
                    onValueChange = { accountId = it.take(80) },
                    label = { Text(stringResource(R.string.dns_account_field)) },
                    singleLine = true,
                    isError = accountId.isNotBlank() && hostname == null,
                    supportingText = if (accountId.isNotBlank() && hostname == null) {
                        { Text(stringResource(R.string.dns_account_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            hostname?.let { name ->
                Text(stringResource(R.string.dns_hostname_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { copy(context, name) }) { Text(stringResource(R.string.dns_copy)) }
            }
            Text(
                stringResource(R.string.dns_privacy_policy_note, resolver.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openUrl(context, resolver.website) }) { Text(stringResource(R.string.dns_website)) }
                resolver.pricing?.let { url -> TextButton(onClick = { openUrl(context, url) }) { Text(stringResource(R.string.dns_pricing)) } }
                TextButton(onClick = { openUrl(context, resolver.privacyPolicy) }) { Text(stringResource(R.string.dns_privacy_policy)) }
            }
        }
    }
}

@Composable
private fun StepsCard() {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dns_steps_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            listOf(R.string.dns_step_1, R.string.dns_step_2, R.string.dns_step_3, R.string.dns_step_4).forEachIndexed { i, step ->
                Text("${i + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { openNetworkSettings(context) }) { Text(stringResource(R.string.dns_open_settings)) }
            Text(stringResource(R.string.dns_path_varies), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FallbackCard() {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dns_fallback_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dns_fallback_message), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
            listOf(R.string.dns_back_1, R.string.dns_back_2, R.string.dns_back_3).forEachIndexed { i, step ->
                Text("${i + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.dns_back_modes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { openNetworkSettings(context) }) { Text(stringResource(R.string.dns_open_settings)) }
        }
    }
}

@Composable
private fun ListCard(catalogue: PrivateDnsCatalogue, listUpdate: DnsListUpdate?, onUpdateList: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.dns_list_info, catalogue.version, catalogue.updated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onUpdateList, enabled = listUpdate != DnsListUpdate.RUNNING) { Text(stringResource(R.string.dns_list_update)) }
        listUpdate?.let { state ->
            val text = when (state) {
                DnsListUpdate.RUNNING -> stringResource(R.string.dns_list_running)
                DnsListUpdate.UPDATED -> stringResource(R.string.dns_list_updated)
                DnsListUpdate.UNCHANGED -> stringResource(R.string.dns_list_unchanged)
                DnsListUpdate.FAILED -> stringResource(R.string.dns_list_failed)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (state == DnsListUpdate.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.dns_list_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun copy(context: Context, hostname: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.dns_hostname_label), hostname))
    Toast.makeText(context, context.getString(R.string.dns_copied, hostname), Toast.LENGTH_SHORT).show()
}

/**
 * Android n'a pas de raccourci public vers le réglage « DNS privé » : on ouvre « Réseau et Internet », où il se trouve
 * sur la plupart des téléphones, sinon les Paramètres.
 */
private fun openNetworkSettings(context: Context) {
    for (action in listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(action))
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // Réglage absent sur ce téléphone : on essaie le suivant.
        }
    }
}
