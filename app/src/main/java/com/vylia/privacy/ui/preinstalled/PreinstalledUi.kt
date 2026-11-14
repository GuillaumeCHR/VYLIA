package com.vylia.privacy.ui.preinstalled

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.preinstalled.AdbCommands
import com.vylia.privacy.domain.preinstalled.AppGroup
import com.vylia.privacy.domain.preinstalled.DisableRisk
import com.vylia.privacy.domain.preinstalled.PreinstalledInfo
import com.vylia.privacy.domain.preinstalled.PreinstalledOrigin

/** Classement des apps système (paquet → groupe, niveau, origine), fourni par Scanner en mode Avancé aux cartes d'apps. */
val LocalPreinstalled = staticCompositionLocalOf<Map<String, PreinstalledInfo>> { emptyMap() }

@StringRes
fun DisableRisk.label(): Int = when (this) {
    DisableRisk.SAFE -> R.string.risk_safe
    DisableRisk.CAUTION -> R.string.risk_caution
    DisableRisk.ESSENTIAL -> R.string.risk_essential
    DisableRisk.UNKNOWN -> R.string.risk_unknown
}

@StringRes
fun DisableRisk.explanation(): Int = when (this) {
    DisableRisk.SAFE -> R.string.pre_risk_safe_text
    DisableRisk.CAUTION -> R.string.pre_risk_caution_text
    DisableRisk.ESSENTIAL -> R.string.pre_risk_essential_text
    DisableRisk.UNKNOWN -> R.string.pre_risk_unknown_text
}

/** Couleur de la pastille : la même dans les deux thèmes, lisible sur une carte claire comme sombre. */
fun DisableRisk.color(): Color = when (this) {
    DisableRisk.SAFE -> Color(0xFF2E9E5B)
    DisableRisk.CAUTION -> Color(0xFFE08A1E)
    DisableRisk.ESSENTIAL -> Color(0xFFD64545)
    DisableRisk.UNKNOWN -> Color(0xFF8A8F98)
}

/** « Constructeur · Samsung », « Google », « Android (AOSP) »… ; « Origine inconnue » hors de la liste UAD-ng. */
fun Resources.originLabel(info: PreinstalledInfo): String {
    info.chipset?.let { return getString(R.string.origin_chipset, it) }
    val brand = info.brand
    return when (info.entry?.origin) {
        PreinstalledOrigin.OEM -> if (brand != null) getString(R.string.origin_oem_brand, brand) else getString(R.string.origin_oem)
        PreinstalledOrigin.CARRIER -> if (brand != null) getString(R.string.origin_carrier_brand, brand) else getString(R.string.origin_carrier)
        PreinstalledOrigin.GOOGLE -> getString(R.string.origin_google)
        PreinstalledOrigin.THIRD_PARTY -> getString(R.string.origin_third_party)
        PreinstalledOrigin.ANDROID -> getString(R.string.origin_android)
        null -> brand?.let { getString(R.string.origin_oem_brand, it) } ?: getString(R.string.origin_unknown)
    }
}

/**
 * Pastille d'une app système : son niveau de risque si elle est préinstallée, « Composant d'Android » sinon ; avec
 * « désactivée » si elle l'est.
 */
@Composable
fun PreinstalledPill(info: PreinstalledInfo, enabled: Boolean, modifier: Modifier = Modifier) {
    val color = if (info.group == AppGroup.SYSTEM) DisableRisk.ESSENTIAL.color() else info.risk.color()
    val label = if (info.group == AppGroup.SYSTEM) stringResource(R.string.group_system_component) else stringResource(info.risk.label())
    val text = if (enabled) label else stringResource(R.string.pre_pill_disabled, label)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Fiche Android de l'app : « Désactiver » quand Android le permet, et ses autorisations. */
fun openAppSettings(context: Context, packageName: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Copie une ou plusieurs commandes ADB, et le confirme d'un toast. */
fun copyCommand(context: Context, command: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.pre_clip_label), command))
    Toast.makeText(context, R.string.pre_copied, Toast.LENGTH_SHORT).show()
}

/** Commande de désactivation, ou de réactivation pour une app déjà désactivée. */
fun commandFor(packageName: String, enabled: Boolean): String =
    if (enabled) AdbCommands.disable(packageName) else AdbCommands.enable(packageName)

/** Guide ADB pas à pas : options pour les développeurs, débogage USB, commande, retour en arrière. */
@Composable
fun AdbGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adb_guide_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.adb_guide_intro), style = MaterialTheme.typography.bodyMedium)
                listOf(R.string.adb_guide_step1, R.string.adb_guide_step2, R.string.adb_guide_step3, R.string.adb_guide_step4)
                    .forEachIndexed { i, step -> Text("${i + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.adb_guide_undo), style = MaterialTheme.typography.bodyMedium)
                Text(
                    AdbCommands.enable("com.example.app"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.adb_guide_end), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adb_guide_close)) } },
    )
}

/** Avertissement avant de copier la commande d'une app « à vos risques ». */
@Composable
fun CautionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.risk_caution)) },
        text = { Text(stringResource(R.string.pre_caution_confirm_text)) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(stringResource(R.string.pre_caution_confirm_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
