package com.vipacy.app.domain.usecase

import com.vipacy.app.domain.model.AppFilter
import com.vipacy.app.domain.model.AuditedApp
import com.vipacy.app.domain.model.TrackerAnalysis
import com.vipacy.app.domain.repository.AppRepository
import com.vipacy.app.domain.scoring.PrivacyScorer

/**
 * Premier passage, rapide : scanne les applications et les note sur leurs seules permissions.
 * Les traqueurs sont ajoutés ensuite par [AnalyzeTrackersUseCase].
 */
class AuditAppsUseCase(
    private val repository: AppRepository,
    private val scorer: PrivacyScorer,
) {
    suspend operator fun invoke(filter: AppFilter = AppFilter.ALL): List<AuditedApp> =
        repository.getInstalledApps(filter)
            .map { scorer.scoreApp(it, TrackerAnalysis.NotAnalyzed) }
            .sortedWith(RiskFirstOrder)
}
