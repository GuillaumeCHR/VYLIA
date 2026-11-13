package com.vylia.privacy.ui.scan

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.apps.PackageRemovalMonitor
import com.vylia.privacy.data.alternatives.AlternativesRepository
import com.vylia.privacy.data.export.CsvExporter
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryRecorder
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
import com.vylia.privacy.domain.usecase.deviceScore
import com.vylia.privacy.data.alert.ScanCompletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.vylia.privacy.data.shield.ShieldExposureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanViewModel(
    private val auditApps: AuditAppsUseCase,
    private val analyzeTrackers: AnalyzeTrackersUseCase,
    private val settingsRepository: SettingsRepository,
    private val ruleMatrixRepository: RuleMatrixRepository,
    private val badgesManager: BadgesManager,
    packageRemovalMonitor: PackageRemovalMonitor,
    private val alternatives: AlternativesRepository,
    private val historyRecorder: HistoryRecorder,
    private val scanCompletion: ScanCompletion,
    shieldExposureRepository: ShieldExposureRepository,
) : ViewModel() {

    /** Ce que le bouclier bloque des traqueurs, `null` tant que ce n'est pas calculé (fiche d'une app). */
    val shieldExposure: StateFlow<ShieldExposureRepository.Exposure?> = shieldExposureRepository.exposure

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
    val uiState: StateFlow<ScanUiState> = combine(
        combine(scanState, selectedFilter, settingsRepository.advancedMode, isAnalyzingTrackers, settingsRepository.lastScanTimestamp, ::reduce),
        shieldExposure,
        ruleMatrixRepository.current,
    ) { state, exposure, rules -> if (exposure == null || !BuildConfig.SHIELD_ENABLED) state else withShield(state, exposure, rules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

    /** Exposition de l'appareil avec le bouclier, à côté de son score (en-tête d'Analyse). */
    private fun withShield(
        state: ScanUiState,
        exposure: ShieldExposureRepository.Exposure,
        rules: RuleMatrixRepository.LoadedRules,
    ): ScanUiState {
        val shielded = { app: AuditedApp -> rules.scorer.shielded(app.result, exposure::forScore) }
        return state.copy(
            userShield = ShieldSummary.of(state.scannedApps.filter { AppFilter.USER.matches(it.app) }, exposure, shielded),
            allShield = ShieldSummary.of(state.scannedApps, exposure, shielded),
        )
    }

    val rewards: StateFlow<RewardsState> = badgesManager.state

    /** Suivi longitudinal : courbe du score moyen et événements, enregistrés à chaque scan complet. */
    val history: StateFlow<History> = historyRecorder.history

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
            val startedAt = SystemClock.elapsedRealtime()
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
            val listedAt = SystemClock.elapsedRealtime()
            analyzeTrackers(userApps).collect(::replaceApp)
            isAnalyzingTrackers.value = false
            settingsRepository.setLastScan(System.currentTimeMillis())
            val unanalyzed = currentApps().count { AppFilter.USER.matches(it.app) && it.trackerAnalysis !is TrackerAnalysis.Analyzed }
            Log.i(
                TAG,
                "Scan : ${apps.size} apps listées en ${listedAt - startedAt} ms, traqueurs de ${userApps.size} apps " +
                    "en ${SystemClock.elapsedRealtime() - listedAt} ms ($unanalyzed non analysées)",
            )
            if (BuildConfig.DEBUG) logScores()
            scanCompletion.onFullScan(currentApps(), background = false)
            badgesManager.onScanCompleted(
                currentApps().filter { AppFilter.USER.matches(it.app) }.toSnapshots(),
                alternatives.catalogue.packages,
            )
        }
    }

    /** Règles de score en vigueur (intégrées ou importées), pour les explications de l'onglet Comprendre. */
    val rules: StateFlow<RuleMatrixRepository.LoadedRules> = ruleMatrixRepository.current


    /** Toutes les apps du dernier scan (les deux onglets), pour l'export. */
    fun exportCsv(labels: CsvExporter.Labels): String = CsvExporter.export(currentApps(), labels)

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

    private fun reduce(scan: ScanState, selected: AppFilter, advanced: Boolean, analyzing: Boolean, lastScanAt: Long?): ScanUiState {
        val filter = if (advanced) selected else AppFilter.USER
        return when (scan) {
            ScanState.Loading -> ScanUiState(isLoading = true, advancedMode = advanced, selectedFilter = filter, lastScanAt = lastScanAt)
            is ScanState.Failure -> ScanUiState(
                isLoading = false,
                advancedMode = advanced,
                selectedFilter = filter,
                errorMessage = scan.message,
                lastScanAt = lastScanAt,
            )
            is ScanState.Success -> {
                // Le tri par risque croissant est préservé par filter().
                val visible = scan.apps.filter { filter.matches(it.app) }
                ScanUiState(
                    isLoading = false,
                    advancedMode = advanced,
                    selectedFilter = filter,
                    apps = visible,
                    scannedApps = scan.apps,
                    averageScore = PrivacyScore.average(visible.map { it.score }),
                    userAverageScore = scan.apps.deviceScore(),
                    systemAverageScore = PrivacyScore.average(scan.apps.filter { AppFilter.SYSTEM.matches(it.app) }.map { it.score }),
                    allAverageScore = PrivacyScore.average(scan.apps.map { it.score }),
                    userAppCount = scan.apps.count { AppFilter.USER.matches(it.app) },
                    systemAppCount = scan.apps.count { AppFilter.SYSTEM.matches(it.app) },
                    isAnalyzingTrackers = analyzing,
                    lastScanAt = lastScanAt,
                )
            }
        }
    }

    /**
     * Version de développement : une ligne par app utilisateur (paquet, score, note, badges), pour comparer deux
     * versions des règles sur un vrai téléphone (`adb logcat -s VyliaScores`).
     */
    private fun logScores() {
        currentApps().filter { AppFilter.USER.matches(it.app) }.forEach { audited ->
            val r = audited.result
            Log.d(SCORES_TAG, "${audited.app.packageName}\t${r.score}\t${r.grade}\t${r.categoryId}\t" +
                "${"%.1f".format(java.util.Locale.ROOT, r.permissionPenalty)}\t${r.trackerPenalty}\t${r.badges.joinToString(",") { it.id }}")
        }
    }

    private fun List<AuditedApp>.toSnapshots(): Map<String, AppSnapshot> = associate {
        val trackerCount = (it.trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.size ?: 0
        val sensitive = it.result.permissionFindings.filter { f -> f.granted && f.penalty > 0.0 }.map { f -> f.permission }.toSet()
        it.app.packageName to AppSnapshot(it.app.label, it.score.value, trackerCount, sensitive)
    }

    companion object {
        private const val TAG = "VyliaScan"
        private const val SCORES_TAG = "VyliaScores"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as VyliaApplication).container
                ScanViewModel(
                    container.auditAppsUseCase,
                    container.analyzeTrackersUseCase,
                    container.settingsRepository,
                    container.ruleMatrixRepository,
                    container.badgesManager,
                    container.packageRemovalMonitor,
                    container.alternativesRepository,
                    container.historyRecorder,
                    container.scanCompletion,
                    container.shieldExposureRepository,
                )
            }
        }
    }
}
