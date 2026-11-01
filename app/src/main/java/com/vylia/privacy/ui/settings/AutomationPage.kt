package com.vylia.privacy.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vylia.privacy.R
import com.vylia.privacy.data.preferences.BackgroundScan
import com.vylia.privacy.ui.components.currentLocale
import java.text.DateFormat
import java.util.Date

/** Paramètres › Automatisations : notification à l'installation et scans de fond programmés, désactivés par défaut. */
@Composable
internal fun AutomationPage(modifier: Modifier, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.automation.collectAsStateWithLifecycle()
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
    val onInstallAlerts = { enabled: Boolean ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (enabled && needsPermission) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.setInstallAlerts(enabled)
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
