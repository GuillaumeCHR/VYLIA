package com.vipacy.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vipacy.app.VipacyApplication
import com.vipacy.app.data.preferences.SettingsRepository
import com.vipacy.app.data.trackers.AssetTrackerSignatureRepository
import com.vipacy.app.data.trackers.TrackerUpdater
import com.vipacy.app.domain.model.AppFilter
import com.vipacy.app.domain.model.TrackerAnalysis
import com.vipacy.app.domain.usecase.AuditAppsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val lastUpdateDate: String = "",
    val isObsolete: Boolean = false,
    val isUpdating: Boolean = false,
    val isExporting: Boolean = false,
    val updateError: String? = null,
    val updateSuccess: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val trackerUpdater: TrackerUpdater,
    private val trackerSignatureRepository: AssetTrackerSignatureRepository,
    private val auditApps: AuditAppsUseCase,
    private val analyzeTrackers: com.vipacy.app.domain.usecase.AnalyzeTrackersUseCase
) : ViewModel() {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val _isUpdating = MutableStateFlow(false)
    private val _isExporting = MutableStateFlow(false)
    private val _updateError = MutableStateFlow<String?>(null)
    private val _updateSuccess = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.lastUpdateTimestamp,
        _isUpdating,
        _isExporting,
        _updateError,
        _updateSuccess
    ) { timestamp, isUpdating, isExporting, updateError, updateSuccess ->
        SettingsUiState(
            lastUpdateDate = dateFormatter.format(Date(timestamp)),
            isObsolete = trackerUpdater.isDatabaseObsolete(timestamp),
            isUpdating = isUpdating,
            isExporting = isExporting,
            updateError = updateError,
            updateSuccess = updateSuccess
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
                    _updateError.value = error.localizedMessage ?: "Erreur"
                }
            )
            _isUpdating.value = false
        }
    }

    suspend fun generateExportData(): String = withContext(Dispatchers.IO) {
        _isExporting.value = true
        try {
            val initialApps = auditApps(AppFilter.ALL)
            val userApps = initialApps.filter { AppFilter.USER.matches(it.app) }
            val systemApps = initialApps.filter { AppFilter.SYSTEM.matches(it.app) }
            val analyzedUserApps = mutableListOf<com.vipacy.app.domain.model.AuditedApp>()
            
            analyzeTrackers(userApps).collect { app ->
                analyzedUserApps.add(app)
            }
            val apps = if (analyzedUserApps.isNotEmpty()) analyzedUserApps + systemApps else initialApps

            val sb = java.lang.StringBuilder()
            sb.append("Nom de l'application,Package,Type,Score,Nombre de traqueurs,Permissions a risque\n")
            for (auditedApp in apps) {
                val type = if (auditedApp.app.isSystemApp) "Systeme" else "Utilisateur"
                val trackerCount = (auditedApp.trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.size ?: 0
                val riskyPerms = auditedApp.app.permissions
                    .filter { it.isGranted }
                    .joinToString(" | ") { it.risk.title }
                
                sb.append("\"").append(auditedApp.app.label).append("\",")
                sb.append("\"").append(auditedApp.app.packageName).append("\",")
                sb.append("\"").append(type).append("\",")
                sb.append(auditedApp.score.value.toString()).append(",")
                sb.append(trackerCount.toString()).append(",")
                sb.append("\"").append(riskyPerms).append("\"\n")
            }
            sb.toString()
        } finally {
            _isExporting.value = false
        }
    }

    fun resetSuccessState() {
        _updateSuccess.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VipacyApplication
                val container = app.container
                SettingsViewModel(
                    container.settingsRepository,
                    container.trackerUpdater,
                    container.trackerSignatureRepository,
                    container.auditAppsUseCase,
                    container.analyzeTrackersUseCase
                )
            }
        }
    }
}
