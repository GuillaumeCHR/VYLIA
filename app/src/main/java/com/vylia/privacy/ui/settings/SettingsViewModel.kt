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
import com.vylia.privacy.data.automation.AutomationScheduler
import com.vylia.privacy.data.automation.InstallWatcher
import com.vylia.privacy.data.preferences.BackgroundScan
import com.vylia.privacy.data.preferences.CardStyle
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.trackers.AssetTrackerSignatureRepository
import com.vylia.privacy.data.trackers.TrackerUpdater
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
}

data class SettingsUiState(
    /** Date de la base Exodus utilisée (téléchargée, ou première installation). */
    val lastUpdateTimestamp: Long = 0L,
    val isObsolete: Boolean = false,
    val isUpdating: Boolean = false,
    val updateError: String? = null,
    val updateSuccess: Boolean = false,
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
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val trackerUpdater: TrackerUpdater,
    private val trackerSignatureRepository: AssetTrackerSignatureRepository,
    private val ruleMatrixRepository: RuleMatrixRepository,
    private val installWatcher: InstallWatcher,
    private val automationScheduler: AutomationScheduler,
    private val backupRepository: BackupRepository,
    private val contentResolver: ContentResolver,
) : ViewModel() {

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
        settingsRepository.installAlerts,
        settingsRepository.backgroundScan,
        settingsRepository.lastBackgroundScan,
        ::AutomationUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationUiState())

    /** Seules les apps installées après l'activation seront signalées. */
    fun setInstallAlerts(enabled: Boolean) {
        if (enabled && !settingsRepository.installAlerts.value) installWatcher.start()
        settingsRepository.setInstallAlerts(enabled)
        automationScheduler.sync()
    }

    fun setBackgroundScan(frequency: BackgroundScan) {
        settingsRepository.setBackgroundScan(frequency)
        automationScheduler.sync()
    }

    private val _isUpdating = MutableStateFlow(false)
    private val _updateError = MutableStateFlow<String?>(null)
    private val _updateSuccess = MutableStateFlow(false)
    private val _rulesMessage = MutableStateFlow<RulesMessage?>(null)

    private val updateState = combine(
        settingsRepository.lastUpdateTimestamp,
        _isUpdating,
        _updateError,
        _updateSuccess
    ) { timestamp, isUpdating, updateError, updateSuccess ->
        SettingsUiState(
            lastUpdateTimestamp = timestamp,
            isObsolete = trackerUpdater.isDatabaseObsolete(timestamp),
            isUpdating = isUpdating,
            updateError = updateError,
            updateSuccess = updateSuccess
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        updateState,
        settingsRepository.advancedMode,
        ruleMatrixRepository.current,
        _rulesMessage
    ) { state, advancedMode, rules, rulesMessage ->
        state.copy(
            advancedMode = advancedMode,
            rulesImported = rules.source == RuleMatrixRepository.Source.IMPORTED,
            ruleCategoryCount = rules.rules.categories.size,
            rulesMessage = rulesMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateDatabase() {
        viewModelScope.launch {
            _isUpdating.value = true
            _updateError.value = null
            _updateSuccess.value = false

            trackerUpdater.downloadUpdate().fold(
                onSuccess = {
                    trackerSignatureRepository.invalidateCache()
                    _updateSuccess.value = true
                },
                onFailure = { error ->
                    _updateError.value = error.localizedMessage ?: error.javaClass.simpleName
                }
            )
            _isUpdating.value = false
        }
    }

    fun resetSuccessState() {
        _updateSuccess.value = false
    }

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
                    container.trackerUpdater,
                    container.trackerSignatureRepository,
                    container.ruleMatrixRepository,
                    container.installWatcher,
                    container.automationScheduler,
                    container.backupRepository,
                    app.contentResolver,
                )
            }
        }
    }
}
