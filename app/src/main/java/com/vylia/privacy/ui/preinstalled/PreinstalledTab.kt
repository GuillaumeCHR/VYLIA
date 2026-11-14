package com.vylia.privacy.ui.preinstalled

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.preinstalled.AdbCommands
import com.vylia.privacy.domain.preinstalled.AppGroup
import com.vylia.privacy.domain.preinstalled.DisableRisk
import com.vylia.privacy.domain.preinstalled.PreinstalledInfo
import com.vylia.privacy.domain.preinstalled.PreinstalledOrigin
import com.vylia.privacy.ui.components.AppIcon
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.VyliaCard

/** Une app préinstallée de la liste, avec son classement. */
private data class PreRow(val app: AuditedApp, val info: PreinstalledInfo)

/** Ordre des groupes d'origine : ce qu'on désactive le plus volontiers d'abord. */
private val OriginOrder = listOf(
    PreinstalledOrigin.OEM, PreinstalledOrigin.CARRIER, PreinstalledOrigin.THIRD_PARTY, PreinstalledOrigin.GOOGLE, null,
)

/**
 * Gérer › Préinstallées (mode Avancé) : les apps livrées avec le téléphone qu'on peut désactiver, groupées par origine,
 * les moins bien notées d'abord. Une case à cocher par app, pour copier d'un coup leurs commandes ADB ; les apps déjà
 * désactivées à part, avec la commande pour les réactiver.
 */
@Composable
fun PreinstalledTab(apps: List<AuditedApp>, classification: Map<String, PreinstalledInfo>) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var risk by rememberSaveable { mutableStateOf<DisableRisk?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var guide by remember { mutableStateOf(false) }
    var caution by remember { mutableStateOf<String?>(null) }

    val rows = remember(apps, classification) {
        apps.mapNotNull { app -> classification[app.app.packageName]?.takeIf { it.group == AppGroup.PREINSTALLED }?.let { PreRow(app, it) } }
            .sortedBy { it.app.score.value }
    }
    val active = rows.filter { it.app.app.enabled }
    val disabled = rows.filterNot { it.app.app.enabled }
    val counts = active.groupingBy { it.info.risk }.eachCount()
    val visible = active.filter { risk == null || it.info.risk == risk }
    val groups = visible.groupBy { it.info.entry?.origin }.toList().sortedBy { OriginOrder.indexOf(it.first) }
    val chosen = active.filter { it.app.app.packageName in selected && it.info.actionable }

    if (guide) AdbGuideDialog { guide = false }
    caution?.let { pkg -> CautionDialog(onConfirm = { copyCommand(context, AdbCommands.disable(pkg)) }, onDismiss = { caution = null }) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.pre_tab_summary, active.size, counts[DisableRisk.SAFE] ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.pre_tab_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { guide = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pre_action_howto)) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = risk == null,
                        onClick = { risk = null },
                        label = { Text("${stringResource(R.string.pre_tab_all)} · ${active.size}") },
                    )
                    DisableRisk.entries.filter { it in counts }.forEach { level ->
                        FilterChip(
                            selected = risk == level,
                            onClick = { risk = if (risk == level) null else level },
                            leadingIcon = { Box(Modifier.size(8.dp).background(level.color(), CircleShape)) },
                            label = { Text("${stringResource(level.label())} · ${counts.getValue(level)}") },
                        )
                    }
                }
                if (chosen.isNotEmpty()) {
                    Button(onClick = { copyCommand(context, AdbCommands.disableAll(chosen.map { it.app.app.packageName })) }) {
                        Text(pluralStringResource(R.plurals.pre_select_copy, chosen.size, chosen.size))
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") { Text(stringResource(R.string.pre_tab_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        groups.forEach { (origin, list) ->
            item(key = "origin-$origin") {
                Text(
                    originHeader(resources, origin, list.first().info),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(list, key = { "pre-${it.app.app.packageName}" }) { row ->
                val pkg = row.app.app.packageName
                PreinstalledRow(
                    row = row,
                    checked = pkg in selected,
                    onChecked = { selected = if (it) selected + pkg else selected - pkg },
                    expanded = expanded == pkg,
                    onToggle = { expanded = if (expanded == pkg) null else pkg },
                    onCopy = { if (row.info.risk == DisableRisk.CAUTION) caution = pkg else copyCommand(context, AdbCommands.disable(pkg)) },
                )
            }
        }
        if (disabled.isNotEmpty()) {
            item(key = "disabled") {
                Text(
                    stringResource(R.string.pre_section_disabled, disabled.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(disabled, key = { "off-${it.app.app.packageName}" }) { row ->
                val pkg = row.app.app.packageName
                PreinstalledRow(
                    row = row,
                    checked = false,
                    onChecked = null,
                    expanded = expanded == pkg,
                    onToggle = { expanded = if (expanded == pkg) null else pkg },
                    onCopy = { copyCommand(context, AdbCommands.enable(pkg)) },
                )
            }
        }
        item(key = "source") {
            Text(stringResource(R.string.pre_source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun originHeader(resources: android.content.res.Resources, origin: PreinstalledOrigin?, sample: PreinstalledInfo): String = when (origin) {
    PreinstalledOrigin.OEM -> resources.getString(R.string.origin_oem)
    PreinstalledOrigin.CARRIER -> resources.getString(R.string.origin_carrier)
    null -> resources.getString(R.string.origin_unknown)
    else -> resources.originLabel(sample)
}

@Composable
private fun PreinstalledRow(
    row: PreRow,
    checked: Boolean,
    onChecked: ((Boolean) -> Unit)?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val app = row.app.app
    val info = row.info
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onToggle).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onChecked != null && info.actionable) Checkbox(checked = checked, onCheckedChange = onChecked)
                AppIcon(app.icon, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PreinstalledPill(info, app.enabled)
                }
                ScoreBadge(row.app.score)
            }
            info.entry?.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                Text(stringResource(info.risk.explanation()), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAppSettings(context, app.packageName) }) { Text(stringResource(R.string.pre_action_settings)) }
                    // Une app inconnue ou indispensable n'a pas de commande : seulement sa fiche Android.
                    if (info.actionable || !app.enabled) {
                        OutlinedButton(onClick = onCopy) {
                            Text(stringResource(if (app.enabled) R.string.pre_action_copy else R.string.pre_action_copy_enable))
                        }
                    }
                }
            }
        }
    }
}
