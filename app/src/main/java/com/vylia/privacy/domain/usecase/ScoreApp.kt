package com.vylia.privacy.domain.usecase

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.AppScan
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.Tracker as ScoredTracker

/**
 * Point unique de calcul du score (règle v2). Traqueurs `null` (non analysés ou analyse en échec) :
 * pénalité forfaitaire et confiance PARTIAL ; liste vide : analysée, aucun traqueur.
 */
internal fun PrivacyScorer.scoreApp(app: InstalledApp, analysis: TrackerAnalysis): AuditedApp {
    val trackers = (analysis as? TrackerAnalysis.Analyzed)?.trackers?.map { ScoredTracker(it.name, it.categories.toSet()) }
    val result = score(
        AppScan(
            packageName = app.packageName,
            targetSdk = app.targetSdk,
            appInfoCategory = app.appInfoCategory,
            declaredPermissions = app.declaredPermissions,
            grantedPermissions = app.grantedPermissions,
            trackers = trackers,
        )
    )
    return AuditedApp(app = app, score = PrivacyScore(result.score), trackerAnalysis = analysis, result = result)
}

/** Ordre d'affichage : les plus à risque d'abord, puis par nom. */
internal val RiskFirstOrder: Comparator<AuditedApp> =
    compareBy<AuditedApp> { it.score.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
