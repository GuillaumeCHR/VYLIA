package com.vylia.privacy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.PackageRemovalMonitor
import com.vylia.privacy.data.export.CsvExporter
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.rewards.AppSnapshot
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.domain.rewards.RemovedApp
import com.vylia.privacy.domain.rewards.RewardsState
import com.vylia.privacy.domain.usecase.AnalyzeTrackersUseCase
import com.vylia.privacy.domain.usecase.AuditAppsUseCase
import com.vylia.privacy.domain.usecase.RiskFirstOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val auditApps: AuditAppsUseCase,
    private val analyzeTrackers: AnalyzeTrackersUseCase,
    private val settingsRepository: SettingsRepository,
    private val ruleMatrixRepository: RuleMatrixRepository,
    private val badgesManager: BadgesManager,
    packageRemovalMonitor: PackageRemovalMonitor,
) : ViewModel() {

    private sealed interface ScanState {
        data object Loading : ScanState
        data class Success(val apps: List<AuditedApp>) : ScanState
        data class Failure(val message: String) : ScanState
    }

    private val scanState = MutableStateFlow<ScanState>(ScanState.Loading)
    private val selectedFilter = MutableStateFlow(AppFilter.USER)
    private val isAnalyzingTrackers = MutableStateFlow(false)
    private var scanJob: Job? = null

    /**
     * Le scan complet n'est fait qu'une fois ; changer d'onglet filtre simplement
     * le résultat en mémoire, sans relancer de requête au PackageManager.
     */
    val uiState: StateFlow<HomeUiState> =
        combine(scanState, selectedFilter, settingsRepository.advancedMode, isAnalyzingTrackers, ::reduce)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val rewards: StateFlow<RewardsState> = badgesManager.state

    /** Suppressions récompensées depuis l'ouverture de l'app, pour l'animation de félicitations. */
    private val _recentRewards = MutableStateFlow<List<RemovedApp>>(emptyList())
    val recentRewards: StateFlow<List<RemovedApp>> = _recentRewards.asStateFlow()

    init {
        // Premier scan, puis nouveau scan à chaque changement de mode ou de matrice de règles.
        viewModelScope.launch {
            combine(settingsRepository.advancedMode, ruleMatrixRepository.current) { _, _ -> }.collect { refresh() }
        }
        viewModelScope.launch {
            packageRemovalMonitor.removals.collect(::onPackageRemoved)
        }
    }

    fun selectFilter(filter: AppFilter) {
        selectedFilter.value = filter
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanState.value = ScanState.Loading
            isAnalyzingTrackers.value = false
            val scope = if (settingsRepository.advancedMode.value) AppFilter.ALL else AppFilter.USER
            val apps = try {
                auditApps(scope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanState.value = ScanState.Failure(e.message ?: e::class.java.simpleName)
                return@launch
            }
            scanState.value = ScanState.Success(apps)

            val userApps = apps.filter { AppFilter.USER.matches(it.app) }
            reward(badgesManager.onAppsListed(userApps.mapTo(HashSet()) { it.app.packageName }))

            // Analyse DEX limitée aux apps utilisateur : les centaines d'APK système (souvent
            // volumineux) seront analysés à la demande dans une prochaine version.
            // Pas de finally : un scan annulé ne doit pas écraser l'indicateur du scan qui le remplace.
            isAnalyzingTrackers.value = true
            analyzeTrackers(userApps).collect(::replaceApp)
            isAnalyzingTrackers.value = false
            badgesManager.onScanCompleted(currentApps().filter { AppFilter.USER.matches(it.app) }.toSnapshots())
        }
    }

    fun categoryName(categoryId: String): String = ruleMatrixRepository.current.value.scorer.categoryDisplayName(categoryId)

    /** Toutes les apps du dernier scan (les deux onglets), pour l'export. */
    fun exportCsv(): String = CsvExporter.export(currentApps())

    fun dismissRecentRewards() {
        _recentRewards.value = emptyList()
    }

    private fun currentApps(): List<AuditedApp> = (scanState.value as? ScanState.Success)?.apps.orEmpty()

    /** Désinstallation en direct : l'app disparaît et le score moyen se met à jour (animé) sans nouveau scan. */
    private fun onPackageRemoved(packageName: String) {
        val current = scanState.value as? ScanState.Success ?: return
        val removed = current.apps.find { it.app.packageName == packageName } ?: return
        scanState.value = ScanState.Success(current.apps - removed)
        if (!removed.app.isSystemApp) reward(badgesManager.onAppsRemoved(listOf(removed).toSnapshots()))
    }

    private fun reward(rewarded: List<RemovedApp>) {
        if (rewarded.isNotEmpty()) _recentRewards.value = rewarded + _recentRewards.value
    }

    /** Remplace une app re-notée et conserve le tri "plus à risque d'abord". */
    private fun replaceApp(updated: AuditedApp) {
        val current = scanState.value as? ScanState.Success ?: return
        val apps = current.apps
            .map { if (it.app.packageName == updated.app.packageName) updated else it }
            .sortedWith(RiskFirstOrder)
        scanState.value = ScanState.Success(apps)
    }

    private fun reduce(scan: ScanState, selected: AppFilter, advanced: Boolean, analyzing: Boolean): HomeUiState {
        val filter = if (advanced) selected else AppFilter.USER
        return when (scan) {
            ScanState.Loading -> HomeUiState(isLoading = true, advancedMode = advanced, selectedFilter = filter)
            is ScanState.Failure -> HomeUiState(
                isLoading = false,
                advancedMode = advanced,
                selectedFilter = filter,
                errorMessage = scan.message,
            )
            is ScanState.Success -> {
                // Le tri par risque croissant est préservé par filter().
                val visible = scan.apps.filter { filter.matches(it.app) }
                HomeUiState(
                    isLoading = false,
                    advancedMode = advanced,
                    selectedFilter = filter,
                    apps = visible,
                    averageScore = PrivacyScore.average(visible.map { it.score }),
                    userAppCount = scan.apps.count { AppFilter.USER.matches(it.app) },
                    systemAppCount = scan.apps.count { AppFilter.SYSTEM.matches(it.app) },
                    isAnalyzingTrackers = analyzing,
                )
            }
        }
    }

    private fun List<AuditedApp>.toSnapshots(): Map<String, AppSnapshot> = associate {
        val trackerCount = (it.trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.size ?: 0
        it.app.packageName to AppSnapshot(it.app.label, it.score.value, trackerCount)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as VyliaApplication).container
                HomeViewModel(
                    container.auditAppsUseCase,
                    container.analyzeTrackersUseCase,
                    container.settingsRepository,
                    container.ruleMatrixRepository,
                    container.badgesManager,
                    container.packageRemovalMonitor,
                )
            }
        }
    }
}
