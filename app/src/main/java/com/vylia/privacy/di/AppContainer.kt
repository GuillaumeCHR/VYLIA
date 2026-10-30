package com.vylia.privacy.di

import android.content.Context
import com.vylia.privacy.data.AppScanner
import com.vylia.privacy.data.PackageRemovalMonitor
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rewards.PrefsRewardsStore
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.trackers.AssetTrackerSignatureRepository
import com.vylia.privacy.data.trackers.DexTrackerAnalyzer
import com.vylia.privacy.data.trackers.TrackerUpdater
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.domain.usecase.AnalyzeTrackersUseCase
import com.vylia.privacy.domain.usecase.AuditAppsUseCase

/** Injection de dépendances manuelle : suffisante pour la V1, sans framework supplémentaire. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val trackerUpdater = TrackerUpdater(context, settingsRepository)
    val ruleMatrixRepository = RuleMatrixRepository(context)
    val badgesManager = BadgesManager(PrefsRewardsStore(context))
    val packageRemovalMonitor = PackageRemovalMonitor(context)

    private val scorer = { ruleMatrixRepository.current.value.scorer }
    private val appScanner = AppScanner(context)

    val trackerSignatureRepository = AssetTrackerSignatureRepository(context)
    private val trackerAnalyzer = DexTrackerAnalyzer(trackerSignatureRepository)

    val auditAppsUseCase = AuditAppsUseCase(appScanner, scorer)
    val analyzeTrackersUseCase = AnalyzeTrackersUseCase(trackerAnalyzer, scorer)
}
