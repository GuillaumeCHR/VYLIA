package com.vipacy.app.domain.usecase

import com.vipacy.app.domain.model.AuditedApp
import com.vipacy.app.domain.model.TrackerAnalysis
import com.vipacy.app.domain.repository.TrackerAnalyzer
import com.vipacy.app.domain.scoring.PrivacyScorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Second passage, lent : analyse le DEX de chaque application et émet l'app re-notée
 * dès que son résultat est connu. N'émet rien si la base de signatures est vide.
 */
class AnalyzeTrackersUseCase(
    private val analyzer: TrackerAnalyzer,
    private val scorer: PrivacyScorer,
) {
    operator fun invoke(apps: List<AuditedApp>): Flow<AuditedApp> = flow {
        if (!analyzer.isAvailable()) return@flow
        for (audited in apps) {
            val analysis = try {
                TrackerAnalysis.Analyzed(analyzer.findTrackers(audited.app))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                TrackerAnalysis.Failed
            }
            emit(scorer.scoreApp(audited.app, analysis))
        }
    }
}
