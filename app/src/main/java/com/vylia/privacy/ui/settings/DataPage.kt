package com.vylia.privacy.ui.settings

import android.content.res.Resources
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vylia.privacy.R
import com.vylia.privacy.data.export.CsvExporter
import com.vylia.privacy.domain.backup.RestoreError
import com.vylia.privacy.ui.scan.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Paramètres › Gestion des données, sur le modèle de Molkky Board : exporter, importer (sans rien écraser) et
 * supprimer les données de VYLIA ; le rapport d'audit du scan (CSV) reste un export à part.
 */
@Composable
internal fun DataPage(modifier: Modifier, viewModel: SettingsViewModel, homeViewModel: ScanViewModel, onBack: () -> Unit) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.dataMessage.collectAsStateWithLifecycle()
    val busy by viewModel.dataBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val done = try {
                val csv = homeViewModel.exportCsv(csvLabels(resources))
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } } != null
            } catch (_: Exception) {
                false
            }
            Toast.makeText(context, resources.getString(if (done) R.string.data_export_done else R.string.data_export_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let(viewModel::exportBackup)
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::restoreBackup)
    }

    SubPage(modifier, R.string.settings_data_entry, onBack) {
        InfoCard(R.string.data_backup_title) {
            Hint(stringResource(R.string.data_backup_hint))
            Button(
                onClick = { exportBackup.launch(resources.getString(R.string.data_backup_file_name, today())) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.data_backup_button)) }
            Hint(stringResource(R.string.data_restore_hint))
            OutlinedButton(
                onClick = { restoreBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.data_restore_button)) }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            message?.let { m ->
                val failed = m is DataMessage.ExportFailed || m is DataMessage.RestoreFailed
                Text(
                    resources.dataMessage(m),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        InfoCard(R.string.data_export_title) {
            Text(stringResource(R.string.data_export_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { exportCsv.launch(resources.getString(R.string.data_export_file_name)) },
                enabled = !homeState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.data_export_button)) }
            if (homeState.isAnalyzingTrackers) Hint(stringResource(R.string.data_export_partial))
        }

        InfoCard(R.string.data_delete_title) {
            Hint(stringResource(R.string.data_delete_hint))
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.data_delete_button)) }
        }

        InfoCard(R.string.data_local_title) {
            Text(stringResource(R.string.data_local_text), style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.data_delete_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteAllData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.data_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

private fun Resources.dataMessage(message: DataMessage): String = when (message) {
    DataMessage.Exported -> getString(R.string.data_backup_done)
    DataMessage.ExportFailed -> getString(R.string.data_backup_failed)
    DataMessage.Deleted -> getString(R.string.data_delete_done)
    is DataMessage.RestoreFailed -> getString(
        when (message.error) {
            RestoreError.NOT_A_BACKUP -> R.string.data_restore_not_backup
            RestoreError.NEWER_VERSION -> R.string.data_restore_newer
            RestoreError.TOO_LARGE -> R.string.data_restore_too_large
            RestoreError.UNREADABLE -> R.string.data_restore_unreadable
        }
    )
    is DataMessage.Restored -> {
        val r = message.report
        val added = listOfNotNull(
            r.snapshotsAdded.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_restore_snapshots, it, it) },
            r.eventsAdded.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_restore_events, it, it) },
            r.rewardsAdded.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_restore_rewards, it, it) },
        )
        listOfNotNull(
            if (added.isEmpty()) getString(R.string.data_restore_nothing) else getString(R.string.data_restore_added, added.joinToString(", ")),
            r.alreadyThere.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_restore_present, it, it) },
            r.invalid.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_restore_invalid, it, it) },
            if (r.rulesRestored) getString(R.string.data_restore_rules) else null,
        ).joinToString(" ")
    }
}

/** En-têtes et valeurs du rapport CSV, dans la langue de l'app. */
private fun csvLabels(resources: Resources) = CsvExporter.Labels(
    header = listOf(
        R.string.csv_app_name, R.string.csv_package, R.string.csv_type, R.string.csv_score, R.string.csv_grade,
        R.string.csv_confidence, R.string.csv_tracker_count, R.string.csv_trackers, R.string.csv_permissions,
    ).map(resources::getString),
    userApp = resources.getString(R.string.csv_user_app),
    systemApp = resources.getString(R.string.csv_system_app),
    notAnalyzed = resources.getString(R.string.csv_not_analyzed),
    analysisFailed = resources.getString(R.string.csv_analysis_failed),
    permissionTitle = resources::getString,
)
