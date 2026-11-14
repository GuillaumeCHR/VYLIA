package com.vylia.privacy.di

import android.content.Context
import com.vylia.privacy.data.web.WebServicesRepository
import com.vylia.privacy.data.alert.ScanCompletion
import com.vylia.privacy.data.alert.StalkerwareAlerts
import com.vylia.privacy.ui.notifications.StalkerwareNotifications
import com.vylia.privacy.data.alert.ThresholdAlertRepository
import com.vylia.privacy.ui.notifications.ThresholdNotifications
import com.vylia.privacy.data.apps.AppScanner
import com.vylia.privacy.data.apps.PackageRemovalMonitor
import com.vylia.privacy.data.apps.UsageStatsReader
import com.vylia.privacy.data.alternatives.AlternativesRepository
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rewards.PrefsRewardsStore
import com.vylia.privacy.data.security.MvtRepository
import com.vylia.privacy.data.security.StalkerwareRepository
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.trackers.AssetTrackerSignatureRepository
import com.vylia.privacy.data.trackers.DexTrackerAnalyzer
import com.vylia.privacy.data.trackers.TrackerScanCache
import com.vylia.privacy.data.trackers.TrackerUpdater
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.data.history.FileHistoryStore
import com.vylia.privacy.data.automation.FileBaselineStore
import com.vylia.privacy.data.automation.UpdateBaselines
import com.vylia.privacy.domain.usecase.AnalyzeTrackersUseCase
import com.vylia.privacy.domain.usecase.AuditAppsUseCase
import com.vylia.privacy.data.automation.AutomationScheduler
import com.vylia.privacy.data.automation.BackgroundScanner
import com.vylia.privacy.data.automation.InstallWatcher
import com.vylia.privacy.domain.usecase.FullAuditUseCase
import com.vylia.privacy.ui.notifications.InstallNotifications
import com.vylia.privacy.data.backup.BackupRepository
import com.vylia.privacy.ui.settings.AppLanguage
import com.vylia.privacy.data.dns.PrivateDnsRepository
import com.vylia.privacy.data.shield.ShieldController
import com.vylia.privacy.data.databases.AlternativesSource
import com.vylia.privacy.data.databases.CompaniesRepository
import com.vylia.privacy.data.databases.CompaniesSource
import com.vylia.privacy.data.databases.DatabaseCenter
import com.vylia.privacy.data.databases.EchapSource
import com.vylia.privacy.data.databases.ExodusCategoriesSource
import com.vylia.privacy.data.databases.ExodusSource
import com.vylia.privacy.data.databases.MvtSource
import com.vylia.privacy.data.databases.PrivateDnsSource
import com.vylia.privacy.data.databases.RulesSource
import com.vylia.privacy.data.databases.ShieldListSource
import com.vylia.privacy.data.databases.SupportResourcesSource
import com.vylia.privacy.data.safety.SupportResourcesRepository
import com.vylia.privacy.data.databases.WebServicesSource
import com.vylia.privacy.data.shield.ShieldListStore
import com.vylia.privacy.domain.shield.ExodusNetworkTracker
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.data.shield.ShieldExposureRepository
import com.vylia.privacy.data.databases.FileDatabaseMetaStore
import com.vylia.privacy.data.databases.HttpsClient
import com.vylia.privacy.data.trackers.ExodusCategoryOverrides
import com.vylia.privacy.domain.databases.DatabaseUpdater
import java.io.File

/** Injection de dépendances manuelle : suffisante pour la V1, sans framework supplémentaire. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val trackerUpdater = TrackerUpdater(context, settingsRepository)
    val stalkerwareRepository = StalkerwareRepository(context)
    val mvtRepository = MvtRepository(context)
    val ruleMatrixRepository = RuleMatrixRepository(context, stalkerwareRepository, mvtRepository)
    val badgesManager = BadgesManager(PrefsRewardsStore(context))
    val packageRemovalMonitor = PackageRemovalMonitor(context)
    val alternativesRepository = AlternativesRepository(context)
    val privateDnsRepository = PrivateDnsRepository(context)
    val shieldController = ShieldController(context, onFirstBlock = badgesManager::onShieldBlocked).apply {
        // Indicateurs MVT mis à jour depuis les Paramètres : leurs domaines remplacent ceux de la liste embarquée.
        mvtRepository.current.value.takeIf { it.downloadedAt != null }?.let { setSpywareDomains(it.database.domains) }
    }
    val usageStatsReader = UsageStatsReader(context)
    val historyRecorder = HistoryRecorder(FileHistoryStore(File(context.filesDir, "history.json")))
    val thresholdAlertRepository = ThresholdAlertRepository(context)
    /** Fin de tout scan complet : point du Suivi, puis alerte de seuil. */
    private val stalkerwareAlerts = StalkerwareAlerts(settingsRepository, StalkerwareNotifications(context))
    val scanCompletion = ScanCompletion(
        historyRecorder, thresholdAlertRepository, ThresholdNotifications(context), stalkerwareAlerts,
        rules = { ruleMatrixRepository.current.value },
        databases = { databaseCenter.scoreVersions() },
        scorer = { scorer() },
        onApps = { updateBaselines.replaceAll(it) },
    )

    /** Derniers états connus des apps : l'alerte de mise à jour dit ce qui a changé depuis. */
    private val updateBaselines by lazy {
        UpdateBaselines(
            FileBaselineStore(File(context.filesDir, "app_baselines.json")),
            databases = { databaseCenter.scoreVersions() + ("rules" to ruleMatrixRepository.current.value.fingerprint) },
            shield = { scorer().hasShield },
        )
    }

    /** Moteur de score en vigueur : les règles, et le bouclier s'il filtre en ce moment (scores avec lui). */
    val scorer: () -> PrivacyScorer = {
        val base = ruleMatrixRepository.current.value.scorer
        if (BuildConfig.SHIELD_ENABLED) shieldExposureRepository.apply(base) else base
    }
    val webServicesRepository = WebServicesRepository(context)
    private val appScanner = AppScanner(context, webServices = { webServicesRepository.services })

    val exodusCategories = ExodusCategoryOverrides(context)
    val trackerSignatureRepository = AssetTrackerSignatureRepository(context, overrides = { exodusCategories.categories })

    /** Ce que le bouclier bloque des traqueurs (fiche d'une app : exposition avec le bouclier). */
    val shieldExposureRepository = ShieldExposureRepository(shieldController) {
        trackerSignatureRepository.networkTrackersOrBundled()
            ?.map { ExodusNetworkTracker(it.name, it.categories, it.networkSignature) }
    }

    val companiesRepository = CompaniesRepository(context)
    /** Apps préinstallées (UAD-ng) : lue à la première demande, en mode Avancé. */
    val preinstalledRepository = com.vylia.privacy.data.preinstalled.PreinstalledRepository(context)
    val supportResourcesRepository = SupportResourcesRepository(context)

    /**
     * Bases téléchargeables (Paramètres › Bases de données). Les listes du bouclier viennent du module du bouclier,
     * par [shieldBridge] : une variante sans bouclier n'aura qu'à le retirer.
     */
    private val shieldBridge = object : DatabaseCenter.ShieldBridge {
        override fun sources() = ShieldListStore.LISTS.map { ShieldListSource(it, shieldController) }
        override fun setListEnabled(id: String, enabled: Boolean) {
            ShieldListStore.LISTS.firstOrNull { it.id == id }?.let { shieldController.setListEnabled(it, enabled) }
        }
        override fun rebuild() {
            val exodus = trackerSignatureRepository.networkTrackers()
                ?.map { ExodusNetworkTracker(it.name, it.categories, it.networkSignature) }
            shieldController.rebuild(exodus)
        }
        override fun setSpywareDomains(domains: Map<String, String>) = shieldController.setSpywareDomains(domains)
    }

    private val databaseUpdater = DatabaseUpdater(
        sources = {
            listOf(
                ExodusSource(trackerUpdater) { null },
                ExodusCategoriesSource(exodusCategories),
                EchapSource(stalkerwareRepository),
                MvtSource(mvtRepository),
                RulesSource(ruleMatrixRepository),
            ) + shieldBridge.sources() + listOf(
                AlternativesSource(alternativesRepository),
                WebServicesSource(webServicesRepository),
                PrivateDnsSource(privateDnsRepository),
                SupportResourcesSource(supportResourcesRepository),
                CompaniesSource(companiesRepository),
                com.vylia.privacy.data.databases.PreinstalledSource(preinstalledRepository),
                com.vylia.privacy.data.databases.PreinstalledTranslationsSource(preinstalledRepository),
            )
        },
        http = HttpsClient(),
        metaStore = FileDatabaseMetaStore(File(context.filesDir, "databases.json")),
        tempDir = context.cacheDir,
    )

    val databaseCenter = DatabaseCenter(
        databaseUpdater, settingsRepository, trackerSignatureRepository, ruleMatrixRepository, mvtRepository, shieldBridge,
    )
    private val trackerScanCache = TrackerScanCache(File(context.filesDir, "tracker_scan_cache.json"))
    private val trackerAnalyzer = DexTrackerAnalyzer(
        trackerSignatureRepository,
        trackerScanCache,
        tempDir = context.cacheDir,
    )

    val auditAppsUseCase = AuditAppsUseCase(appScanner, scorer)
    val analyzeTrackersUseCase = AnalyzeTrackersUseCase(trackerAnalyzer, scorer = scorer)

    private val fullAuditUseCase = FullAuditUseCase(auditAppsUseCase, analyzeTrackersUseCase)
    val installWatcher = InstallWatcher(context, settingsRepository, fullAuditUseCase, InstallNotifications(context), updateBaselines, stalkerwareAlerts, scorer)
    val backgroundScanner = BackgroundScanner(
        settingsRepository, fullAuditUseCase, scanCompletion,
        beforeScan = { if (BuildConfig.SHIELD_ENABLED) shieldExposureRepository.awaitReady() },
    )
    val automationScheduler = AutomationScheduler(context, settingsRepository)
    val backupRepository = BackupRepository(
        settingsRepository, historyRecorder, badgesManager, ruleMatrixRepository, trackerScanCache,
        language = { AppLanguage.current().tag },
        clearShieldCounter = shieldController::clear,
        alerts = thresholdAlertRepository,
        clearBaselines = { updateBaselines.clear() },
    )
}
