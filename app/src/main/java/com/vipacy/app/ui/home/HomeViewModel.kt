package com.vipacy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vipacy.app.VipacyApplication
import com.vipacy.app.domain.model.AppFilter
import com.vipacy.app.domain.model.AuditedApp
import com.vipacy.app.domain.model.PrivacyScore
import com.vipacy.app.domain.usecase.AnalyzeTrackersUseCase
import com.vipacy.app.domain.usecase.AuditAppsUseCase
import com.vipacy.app.domain.usecase.RiskFirstOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val auditApps: AuditAppsUseCase,
    private val analyzeTrackers: AnalyzeTrackersUseCase,
) : ViewModel() {

    private sealed interface ScanState {
        data object Loading : ScanState
        data class Success(val apps: List<AuditedApp>) : ScanState
        data class Failure(val message: String) : ScanState
    }

    private val scanState = MutableStateFlow<ScanState>(ScanState.Loading)
    private val selectedFilter = MutableStateFlow(AppFilter.USER)
    private var scanJob: Job? = null

    /**
     * Le scan complet n'est fait qu'une fois ; changer d'onglet filtre simplement
     * le résultat en mémoire, sans relancer de requête au PackageManager.
     */
    val uiState: StateFlow<HomeUiState> = combine(scanState, selectedFilter, ::reduce)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun selectFilter(filter: AppFilter) {
        selectedFilter.value = filter
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanState.value = ScanState.Loading
            val apps = try {
                auditApps(AppFilter.ALL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanState.value = ScanState.Failure(e.message ?: e::class.java.simpleName)
                return@launch
            }
            scanState.value = ScanState.Success(apps)

            // Analyse DEX limitée aux apps utilisateur : les centaines d'APK système (souvent
            // volumineux) seront analysés à la demande dans une prochaine version.
            analyzeTrackers(apps.filter { AppFilter.USER.matches(it.app) }).collect(::replaceApp)
        }
    }

    /** Remplace une app re-notée et conserve le tri "plus à risque d'abord". */
    private fun replaceApp(updated: AuditedApp) {
        val current = scanState.value as? ScanState.Success ?: return
        val apps = current.apps
            .map { if (it.app.packageName == updated.app.packageName) updated else it }
            .sortedWith(RiskFirstOrder)
        scanState.value = ScanState.Success(apps)
    }

    private fun reduce(scan: ScanState, filter: AppFilter): HomeUiState = when (scan) {
        ScanState.Loading -> HomeUiState(isLoading = true, selectedFilter = filter)
        is ScanState.Failure -> HomeUiState(isLoading = false, selectedFilter = filter, errorMessage = scan.message)
        is ScanState.Success -> {
            // Le tri par risque croissant est préservé par filter().
            val visible = scan.apps.filter { filter.matches(it.app) }
            HomeUiState(
                isLoading = false,
                selectedFilter = filter,
                apps = visible,
                averageScore = PrivacyScore.average(visible.map { it.score }),
                userAppCount = scan.apps.count { AppFilter.USER.matches(it.app) },
                systemAppCount = scan.apps.count { AppFilter.SYSTEM.matches(it.app) },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as VipacyApplication).container
                HomeViewModel(container.auditAppsUseCase, container.analyzeTrackersUseCase)
            }
        }
    }
}
