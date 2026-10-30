package com.vipacy.app.domain.usecase

import com.vipacy.app.domain.model.AuditedApp
import com.vipacy.app.domain.model.InstalledApp
import com.vipacy.app.domain.model.PrivacyScore
import com.vipacy.app.domain.model.TrackerAnalysis
import com.vipacy.app.domain.scoring.PrivacyScorer

/** Point unique de calcul du score : permissions + traqueurs (0 tant que l'analyse DEX n'est pas faite). */
internal fun PrivacyScorer.scoreApp(app: InstalledApp, analysis: TrackerAnalysis): AuditedApp {
    val trackerCount = (analysis as? TrackerAnalysis.Analyzed)?.trackers?.size ?: 0
    val score = calculateScore(app.requestedPermissions, trackerCount, app.category, app.packageName, app.isSystemApp)
    return AuditedApp(app = app, score = PrivacyScore(score), trackerAnalysis = analysis)
}

/** Ordre d'affichage : les plus à risque d'abord, puis par nom. */
internal val RiskFirstOrder: Comparator<AuditedApp> =
    compareBy<AuditedApp> { it.score.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
