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
    val result = score(app.toAppScan(analysis))
    return AuditedApp(app = app, score = PrivacyScore(result.score), trackerAnalysis = analysis, result = result)
}

/** Entrée du moteur de score pour une app installée. */
internal fun InstalledApp.toAppScan(analysis: TrackerAnalysis): AppScan = AppScan(
    packageName = packageName,
    targetSdk = targetSdk,
    appInfoCategory = appInfoCategory,
    declaredPermissions = declaredPermissions,
    grantedPermissions = grantedPermissions,
    trackers = (analysis as? TrackerAnalysis.Analyzed)?.trackers?.map { ScoredTracker(it.name, it.categories.toSet()) },
    signals = signals,
)

/** Score qu'aurait l'app si les permissions [revoked] (noms courts) lui étaient retirées. */
fun PrivacyScorer.scoreWithout(audited: AuditedApp, revoked: Set<String>) =
    score(audited.app.toAppScan(audited.trackerAnalysis).let { it.copy(grantedPermissions = it.grantedPermissions - revoked) })

/** Ordre d'affichage : les plus à risque d'abord, puis par nom. */
internal val RiskFirstOrder: Comparator<AuditedApp> =
    compareBy<AuditedApp> { it.score.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
