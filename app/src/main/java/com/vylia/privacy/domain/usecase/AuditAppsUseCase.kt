package com.vylia.privacy.domain.usecase

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.repository.AppRepository
import com.vylia.privacy.domain.scoring.PrivacyScorer

/**
 * Premier passage, rapide : scanne les applications et les note sur leurs seules permissions.
 * Les traqueurs sont ajoutés ensuite par [AnalyzeTrackersUseCase].
 */
class AuditAppsUseCase(
    private val repository: AppRepository,
    /** Lu à chaque scan : des règles importées s'appliquent sans redémarrage. */
    private val scorer: () -> PrivacyScorer,
) {
    suspend operator fun invoke(filter: AppFilter = AppFilter.ALL, only: Set<String>? = null): List<AuditedApp> {
        val scorer = scorer()
        return repository.getInstalledApps(filter, only)
            .filterNot { scorer.isExcluded(it.packageName) }
            .map { scorer.scoreApp(it, TrackerAnalysis.NotAnalyzed) }
            .sortedWith(RiskFirstOrder)
    }
}
