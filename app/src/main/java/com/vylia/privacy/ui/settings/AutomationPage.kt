package com.vylia.privacy.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vylia.privacy.R
import com.vylia.privacy.data.alert.ThresholdAlertRepository
import com.vylia.privacy.data.preferences.BackgroundScan
import com.vylia.privacy.domain.scoring.Grade
import com.vylia.privacy.ui.scan.ScanViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import com.vylia.privacy.ui.components.currentLocale
import java.text.DateFormat
import java.util.Date

/**
 * Paramètres › Automatisations : notification à l'installation, alerte stalkerware, alerte de seuil et scans de fond
 * programmés, tous désactivés par défaut.
 */
@Composable
internal fun AutomationPage(
    modifier: Modifier,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    homeViewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val state by viewModel.automation.collectAsStateWithLifecycle()
    val threshold by viewModel.thresholdAlert.collectAsStateWithLifecycle()
    val loadedRules by homeViewModel.rules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Relu au retour des réglages système : l'utilisateur a pu y couper les notifications de VYLIA.
    var notificationsAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        if (granted) viewModel.setInstallAlerts(true)
    }
    val needsPermission = {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val onInstallAlerts = { enabled: Boolean ->
        if (enabled && needsPermission()) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.setInstallAlerts(enabled)
    }
    // Alertes stalkerware et de seuil : actives tout de suite ; en cas de refus de notifier, elles le restent et une
    // ligne explique comment autoriser les notifications.
    val askForAlert = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    val withPermission = { enabled: Boolean, set: (Boolean) -> Unit ->
        set(enabled)
        if (enabled && needsPermission()) askForAlert.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val openNotificationSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    SubPage(modifier, R.string.settings_automation, onBack) {
        InfoCard(R.string.automation_install_title) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.installAlerts, role = Role.Switch, onValueChange = onInstallAlerts)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.automation_install_switch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Switch(checked = state.installAlerts, onCheckedChange = null)
            }
            Hint(stringResource(R.string.automation_install_hint))
            if (state.installAlerts && !notificationsAllowed) {
                Text(
                    stringResource(R.string.automation_notifications_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }) { Text(stringResource(R.string.automation_open_notification_settings)) }
            }
        }

        InfoCard(R.string.automation_stalkerware_title) {
            SwitchRow(stringResource(R.string.automation_stalkerware_switch), state.stalkerwareAlerts) {
                withPermission(it, viewModel::setStalkerwareAlerts)
            }
            Hint(stringResource(R.string.automation_stalkerware_hint))
            if (state.stalkerwareAlerts && !notificationsAllowed) NotificationsBlocked(openNotificationSettings)
        }

        ThresholdCard(
            state = threshold,
            score = viewModel.deviceScore,
            marks = loadedRules.rules.scoring.grades.filter { it.min > 0 }.sortedByDescending { it.min },
            notificationsAllowed = notificationsAllowed,
            onEnabled = { withPermission(it, viewModel::setThresholdAlert) },
            onThreshold = viewModel::setThreshold,
            onOpenNotificationSettings = openNotificationSettings,
        )

        InfoCard(R.string.automation_scan_title) {
            Hint(stringResource(R.string.automation_scan_hint))
            Column {
                BackgroundScan.entries.forEach { frequency ->
                    val selected = state.backgroundScan == frequency
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton) { viewModel.setBackgroundScan(frequency) }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            stringResource(
                                when (frequency) {
                                    BackgroundScan.OFF -> R.string.automation_scan_off
                                    BackgroundScan.WEEKLY -> R.string.automation_scan_weekly
                                    BackgroundScan.MONTHLY -> R.string.automation_scan_monthly
                                }
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            state.lastBackgroundScan?.let {
                Hint(stringResource(R.string.automation_scan_last, DateFormat.getDateInstance(DateFormat.MEDIUM, currentLocale()).format(Date(it))))
            }
        }
        InfoCard(R.string.automation_battery_title) {
            Text(stringResource(R.string.automation_battery_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NotificationsBlocked(onOpenSettings: () -> Unit) {
    Text(
        stringResource(R.string.automation_notifications_blocked),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.automation_open_notification_settings)) }
}

/**
 * Alerte de seuil : interrupteur, puis curseur de 0 à 100 et repères des notes (bornes de la grille en vigueur),
 * le score actuel à côté. Le seuil ne se règle qu'ici ; le Suivi y renvoie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThresholdCard(
    state: ThresholdAlertRepository.State,
    score: Int?,
    marks: List<Grade>,
    notificationsAllowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onThreshold: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val setting = state.setting
    InfoCard(R.string.automation_threshold_title) {
        SwitchRow(stringResource(R.string.automation_threshold_switch), setting.enabled, onEnabled)
        Hint(stringResource(R.string.automation_threshold_hint))
        if (!setting.enabled) return@InfoCard
        // Valeur suivie pendant le glissement, enregistrée au lâcher : l'état n'est recalculé qu'une fois.
        var dragged by remember(setting.threshold) { mutableFloatStateOf(setting.threshold.toFloat()) }
        val shown = dragged.roundToInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.automation_threshold_value, shown),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                score?.let { stringResource(R.string.automation_threshold_current, it) } ?: stringResource(R.string.automation_threshold_no_score),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val sliderLabel = stringResource(R.string.automation_threshold_slider)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onThreshold(dragged.roundToInt()) },
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = sliderLabel },
        )
        Text(stringResource(R.string.automation_threshold_marks), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            marks.forEach { grade ->
                // Le libellé du bouton est lu tel quel (« B · 70 ») avec son état choisi ou non : rien à y ajouter.
                FilterChip(
                    selected = shown == grade.min,
                    onClick = { dragged = grade.min.toFloat(); onThreshold(grade.min) },
                    label = { Text("${grade.grade} · ${grade.min}") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (score != null && score < shown) {
            Text(stringResource(R.string.automation_threshold_below), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!notificationsAllowed) NotificationsBlocked(onOpenNotificationSettings)
    }
}

