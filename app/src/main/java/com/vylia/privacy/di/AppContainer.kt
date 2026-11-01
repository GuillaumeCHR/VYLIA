package com.vylia.privacy.di

import android.content.Context
import com.vylia.privacy.data.apps.AppScanner
import com.vylia.privacy.data.apps.PackageRemovalMonitor
import com.vylia.privacy.data.apps.UsageStatsReader
import com.vylia.privacy.data.alternatives.AlternativesRepository
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rewards.PrefsRewardsStore
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.trackers.AssetTrackerSignatureRepository
import com.vylia.privacy.data.trackers.DexTrackerAnalyzer
import com.vylia.privacy.data.trackers.TrackerScanCache
import com.vylia.privacy.data.trackers.TrackerUpdater
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.data.history.FileHistoryStore
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
import java.io.File

/** Injection de dépendances manuelle : suffisante pour la V1, sans framework supplémentaire. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val trackerUpdater = TrackerUpdater(context, settingsRepository)
    val ruleMatrixRepository = RuleMatrixRepository(context)
    val badgesManager = BadgesManager(PrefsRewardsStore(context))
    val packageRemovalMonitor = PackageRemovalMonitor(context)
    val alternativesRepository = AlternativesRepository(context)
    val privateDnsRepository = PrivateDnsRepository(context)
    val usageStatsReader = UsageStatsReader(context)
    val historyRecorder = HistoryRecorder(FileHistoryStore(File(context.filesDir, "history.json")))

    private val scorer = { ruleMatrixRepository.current.value.scorer }
    private val appScanner = AppScanner(context)

    val trackerSignatureRepository = AssetTrackerSignatureRepository(context)
    private val trackerScanCache = TrackerScanCache(File(context.filesDir, "tracker_scan_cache.json"))
    private val trackerAnalyzer = DexTrackerAnalyzer(
        trackerSignatureRepository,
        trackerScanCache,
        tempDir = context.cacheDir,
    )

    val auditAppsUseCase = AuditAppsUseCase(appScanner, scorer)
    val analyzeTrackersUseCase = AnalyzeTrackersUseCase(trackerAnalyzer, scorer = scorer)

    private val fullAuditUseCase = FullAuditUseCase(auditAppsUseCase, analyzeTrackersUseCase)
    val installWatcher = InstallWatcher(context, settingsRepository, fullAuditUseCase, InstallNotifications(context), scorer)
    val backgroundScanner = BackgroundScanner(settingsRepository, fullAuditUseCase, historyRecorder)
    val automationScheduler = AutomationScheduler(context, settingsRepository)
    val backupRepository = BackupRepository(
        settingsRepository, historyRecorder, badgesManager, ruleMatrixRepository, trackerScanCache,
        language = { AppLanguage.current().tag },
    )
}
