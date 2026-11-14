package com.vylia.privacy.ui.preinstalled

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.preinstalled.AdbCommands
import com.vylia.privacy.domain.preinstalled.AppGroup
import com.vylia.privacy.domain.preinstalled.DisableRisk
import com.vylia.privacy.domain.preinstalled.PreinstalledInfo
import com.vylia.privacy.ui.components.VyliaCard

/**
 * Fiche d'une app système : « App préinstallée » ou « Composant d'Android », son origine, le risque à la désactiver,
 * ce qu'elle fait (UAD-ng), qui en dépend, et les actions possibles (seulement sans risque ou à vos risques).
 */
@Composable
fun PreinstalledCard(app: InstalledApp, info: PreinstalledInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var guide by remember { mutableStateOf(false) }
    var caution by remember { mutableStateOf(false) }
    if (guide) AdbGuideDialog { guide = false }
    if (caution) CautionDialog(onConfirm = { copyCommand(context, AdbCommands.disable(app.packageName)) }, onDismiss = { caution = false })

    val system = info.group == AppGroup.SYSTEM
    // Un composant d'Android absent de la liste reste « inconnu » ; dans la liste, il garde son niveau.
    val risk = if (system && info.entry == null) DisableRisk.UNKNOWN else info.risk
    VyliaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (system) R.string.pre_card_title_system else R.string.pre_card_title_preinstalled),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.pre_card_origin, resources.originLabel(info)), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(risk.color(), CircleShape))
                Text(
                    stringResource(R.string.pre_card_risk, stringResource(risk.label())),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(risk.explanation()), style = MaterialTheme.typography.bodyMedium)
            if (!app.enabled) {
                Text(stringResource(R.string.pre_card_disabled), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            info.entry?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(stringResource(R.string.pre_card_description_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            info.entry?.neededBy?.takeIf { it.isNotEmpty() }?.let {
                Text(stringResource(R.string.pre_card_needed_by, it.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.pre_card_why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openAppSettings(context, app.packageName) }) { Text(stringResource(R.string.pre_action_settings)) }
                when {
                    !app.enabled -> OutlinedButton(onClick = { copyCommand(context, AdbCommands.enable(app.packageName)) }) {
                        Text(stringResource(R.string.pre_action_copy_enable))
                    }
                    info.actionable -> OutlinedButton(onClick = {
                        if (risk == DisableRisk.CAUTION) caution = true else copyCommand(context, AdbCommands.disable(app.packageName))
                    }) { Text(stringResource(R.string.pre_action_copy)) }
                }
                if (info.actionable || !app.enabled) TextButton(onClick = { guide = true }) { Text(stringResource(R.string.pre_action_howto)) }
            }
        }
    }
}
