package com.vylia.privacy.ui.settings

import android.content.ContentResolver
import android.net.Uri
import com.vylia.privacy.data.backup.BackupRepository
import com.vylia.privacy.domain.backup.RestoreError
import com.vylia.privacy.domain.backup.RestoreException
import com.vylia.privacy.domain.backup.RestoreReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.alert.ThresholdAlertRepository
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.data.automation.AutomationScheduler
import com.vylia.privacy.data.automation.InstallWatcher
import com.vylia.privacy.data.preferences.BackgroundScan
import com.vylia.privacy.data.preferences.CardStyle
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.databases.DatabaseCenter
import com.vylia.privacy.data.trackers.TrackerUpdater
import com.vylia.privacy.domain.databases.DatabaseRow
import com.vylia.privacy.domain.databases.DatabaseRows
import com.vylia.privacy.domain.databases.DatabaseUpdater
import com.vylia.privacy.domain.databases.UpdateSummary
import com.vylia.privacy.data.rules.RuleMatrixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Résultat d'une action sur les règles de score, traduit par l'écran. */
sealed interface RulesMessage {
    data class Imported(val categoryCount: Int) : RulesMessage
    /** [detail] : cause technique, affichée telle quelle (peut être absente). */
    data class Rejected(val detail: String?) : RulesMessage
    data object Reset : RulesMessage
    data object Exported : RulesMessage
    data class ExportFailed(val detail: String?) : RulesMessage
}

data class SettingsUiState(
    /** Date de la base Exodus utilisée (téléchargée, ou première installation), affichée par l'Analyse. */
    val lastUpdateTimestamp: Long = 0L,
    val isObsolete: Boolean = false,
    val advancedMode: Boolean = false,
    val rulesImported: Boolean = false,
    val ruleCategoryCount: Int = 0,
    /** Résultat du dernier import de règles, affiché sous le bouton. */
    val rulesMessage: RulesMessage? = null,
)

/** Résultat d'une action de Paramètres › Gestion des données, traduit par l'écran. */
sealed interface DataMessage {
    data object Exported : DataMessage
    data object ExportFailed : DataMessage
    data class Restored(val report: RestoreReport) : DataMessage
    data class RestoreFailed(val error: RestoreError) : DataMessage
    data object Deleted : DataMessage
}

/** Paramètres › Automatisations. */
data class AutomationUiState(
    val installAlerts: Boolean = false,
    val backgroundScan: BackgroundScan = BackgroundScan.OFF,
    val lastBackgroundScan: Long? = null,
    val stalkerwareAlerts: Boolean = false,
    val updateAlerts: Boolean = false,
    val updateBeforeScan: Boolean = true,
    val updateWifiOnly: Boolean = true,
)

/** Carte « Bases de données » : une ligne par base, progression d'une mise à jour en cours, dernier bilan. */
data class DatabasesUiState(
    val rows: List<DatabaseRow> = emptyList(),
    val progress: DatabaseUpdater.Progress? = null,
    val summary: UpdateSummary? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val databaseCenter: DatabaseCenter,
    private val ruleMatrixRepository: RuleMatrixRepository,
    private val installWatcher: InstallWatcher,
    private val automationScheduler: AutomationScheduler,
    private val thresholdAlertRepository: ThresholdAlertRepository,
    private val historyRecorder: HistoryRecorder,
    private val backupRepository: BackupRepository,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    /** Bases de données : relu à chaque changement d'état ou de progression, sans appel réseau. */
    private val enabledTick = MutableStateFlow(0)

    val databases: StateFlow<DatabasesUiState> = combine(
        databaseCenter.updater.meta,
        databaseCenter.updater.progress,
        databaseCenter.lastSummary,
        enabledTick,
    ) { meta, progress, summary, _ ->
        DatabasesUiState(DatabaseRows.rows(databaseCenter.sources(), meta, progress?.running.orEmpty()), progress, summary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatabasesUiState())

    /** Une mise à jour à la fois : le bouton est désactivé pendant la progression. */
    fun updateAllDatabases() {
        if (databaseCenter.updater.progress.value != null) return
        viewModelScope.launch { databaseCenter.updateAll() }
    }

    fun updateDatabase(id: String) {
        if (databaseCenter.updater.progress.value != null) return
        viewModelScope.launch { databaseCenter.updateOne(id) }
    }

    /** Ajoute (et télécharge) ou retire une base proposée. */
    fun setProposedDatabase(id: String, enabled: Boolean) {
        viewModelScope.launch {
            databaseCenter.setProposedEnabled(id, enabled)
            enabledTick.value++
        }
    }

    fun dismissDatabaseSummary() = databaseCenter.clearSummary()

    fun setUpdateBeforeScan(enabled: Boolean) = settingsRepository.setUpdateBeforeScan(enabled)

    fun setUpdateWifiOnly(enabled: Boolean) = settingsRepository.setUpdateWifiOnly(enabled)

    private val _dataMessage = MutableStateFlow<DataMessage?>(null)
    val dataMessage: StateFlow<DataMessage?> = _dataMessage

    private val _dataBusy = MutableStateFlow(false)
    val dataBusy: StateFlow<Boolean> = _dataBusy

    private fun dataAction(block: suspend () -> DataMessage) {
        viewModelScope.launch {
            _dataBusy.value = true
            _dataMessage.value = null
            _dataMessage.value = block()
            _dataBusy.value = false
        }
    }

    fun exportBackup(uri: Uri) = dataAction {
        try {
            withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri) }?.use { backupRepository.export(it) }
                ?: return@dataAction DataMessage.ExportFailed
            DataMessage.Exported
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DataMessage.ExportFailed
        }
    }

    fun restoreBackup(uri: Uri) = dataAction {
        try {
            val input = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri) }
                ?: return@dataAction DataMessage.RestoreFailed(RestoreError.UNREADABLE)
            input.use { DataMessage.Restored(backupRepository.restore(it)) }
        } catch (e: RestoreException) {
            DataMessage.RestoreFailed(e.error)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DataMessage.RestoreFailed(RestoreError.UNREADABLE)
        }
    }

    fun deleteAllData() = dataAction {
        backupRepository.deleteAll()
        DataMessage.Deleted
    }

    val automation: StateFlow<AutomationUiState> = combine(
        combine(
            settingsRepository.installAlerts,
            settingsRepository.backgroundScan,
            settingsRepository.lastBackgroundScan,
            settingsRepository.stalkerwareAlerts,
            ::AutomationUiState,
        ),
        settingsRepository.updateBeforeScan,
        settingsRepository.updateWifiOnly,
        settingsRepository.updateAlerts,
    ) { state, before, wifi, updates -> state.copy(updateBeforeScan = before, updateWifiOnly = wifi, updateAlerts = updates) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationUiState())

    /** Le contrôle des installations repart de maintenant s'il ne tournait pas déjà pour une autre alerte. */
    private fun startWatchingInstalls() {
        val watching = settingsRepository.installAlerts.value || settingsRepository.updateAlerts.value || settingsRepository.stalkerwareAlerts.value
        if (!watching) installWatcher.start()
    }

    /** Seules les apps installées après l'activation seront signalées. */
    fun setInstallAlerts(enabled: Boolean) {
        if (enabled) startWatchingInstalls()
        settingsRepository.setInstallAlerts(enabled)
        automationScheduler.sync()
    }

    /** Seules les mises à jour qui suivent l'activation seront signalées, comparées à la dernière analyse de l'app. */
    fun setUpdateAlerts(enabled: Boolean) {
        if (enabled) startWatchingInstalls()
        settingsRepository.setUpdateAlerts(enabled)
        automationScheduler.sync()
    }

    /** Alerte stalkerware : à l'installation d'une app reconnue, et lors des scans de fond. */
    fun setStalkerwareAlerts(enabled: Boolean) {
        if (enabled) startWatchingInstalls()
        settingsRepository.setStalkerwareAlerts(enabled)
        automationScheduler.sync()
    }

    /** Alerte de seuil : réglage, état et franchissements ; le score de référence est le dernier point du Suivi. */
    val thresholdAlert: StateFlow<ThresholdAlertRepository.State> = thresholdAlertRepository.current

    val deviceScore: Int? get() = historyRecorder.history.value.snapshots.lastOrNull()?.score

    fun setThresholdAlert(enabled: Boolean) =
        thresholdAlertRepository.setSetting(thresholdAlert.value.setting.copy(enabled = enabled), deviceScore)

    fun setThreshold(threshold: Int) {
        val setting = thresholdAlert.value.setting
        if (setting.threshold != threshold) thresholdAlertRepository.setSetting(setting.copy(threshold = threshold), deviceScore)
    }

    fun setBackgroundScan(frequency: BackgroundScan) {
        settingsRepository.setBackgroundScan(frequency)
        automationScheduler.sync()
    }

    private val _rulesMessage = MutableStateFlow<RulesMessage?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.lastUpdateTimestamp,
        settingsRepository.advancedMode,
        ruleMatrixRepository.current,
        _rulesMessage
    ) { timestamp, advancedMode, rules, rulesMessage ->
        SettingsUiState(
            lastUpdateTimestamp = timestamp,
            isObsolete = System.currentTimeMillis() - timestamp > TrackerUpdater.OBSOLESCENCE_THRESHOLD_MS,
            advancedMode = advancedMode,
            rulesImported = rules.source == RuleMatrixRepository.Source.IMPORTED,
            ruleCategoryCount = rules.rules.categories.size,
            rulesMessage = rulesMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    val cardStyle: StateFlow<CardStyle> = settingsRepository.cardStyle

    fun setCardStyle(style: CardStyle) = settingsRepository.setCardStyle(style)

    /** Le changement de mode relance automatiquement le scan (observé par ScanViewModel). */
    fun setAdvancedMode(enabled: Boolean) {
        settingsRepository.setAdvancedMode(enabled)
    }

    fun importRules(uri: Uri) {
        viewModelScope.launch {
            _rulesMessage.value = ruleMatrixRepository.import(uri).fold(
                onSuccess = { count -> RulesMessage.Imported(count) },
                onFailure = { error -> RulesMessage.Rejected(error.localizedMessage) }
            )
        }
    }

    fun exportRules(uri: Uri) {
        viewModelScope.launch {
            _rulesMessage.value = ruleMatrixRepository.export(uri).fold(
                onSuccess = { RulesMessage.Exported },
                onFailure = { error -> RulesMessage.ExportFailed(error.localizedMessage) },
            )
        }
    }

    fun resetRules() {
        ruleMatrixRepository.resetToBundled()
        _rulesMessage.value = RulesMessage.Reset
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VyliaApplication
                val container = app.container
                SettingsViewModel(
                    container.settingsRepository,
                    container.databaseCenter,
                    container.ruleMatrixRepository,
                    container.installWatcher,
                    container.automationScheduler,
                    container.thresholdAlertRepository,
                    container.historyRecorder,
                    container.backupRepository,
                    app.contentResolver,
                )
            }
        }
    }
}
