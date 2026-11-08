package com.vylia.privacy.domain.usecase

import com.vylia.privacy.domain.history.AppPoint
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.TrackerAnalysis

/**
 * Audit complet des apps utilisateur (permissions puis traqueurs), d'un seul tenant : pour les tâches de fond
 * (scan programmé, notification d'installation), qui n'ont pas d'écran à mettre à jour au fil de l'analyse.
 */
class FullAuditUseCase(
    private val auditApps: AuditAppsUseCase,
    private val analyzeTrackers: AnalyzeTrackersUseCase,
) {
    /** @param only si non nul, seuls ces paquets sont audités. */
    suspend operator fun invoke(only: Set<String>? = null): List<AuditedApp> {
        val listed = auditApps(AppFilter.USER, only)
        val byPackage = listed.associateByTo(LinkedHashMap()) { it.app.packageName }
        analyzeTrackers(listed).collect { byPackage[it.app.packageName] = it }
        return byPackage.values.sortedWith(RiskFirstOrder)
    }
}

/** Point de suivi d'une app : son score et ses permissions notées accordées. */
fun AuditedApp.historyPoint(): AppPoint = AppPoint(
    label = app.label,
    score = score.value,
    granted = result.permissionFindings.filter { it.granted }.mapTo(HashSet()) { it.permission },
    // Traqueurs inconnus tant que l'analyse n'a pas eu lieu : rien à comparer au scan suivant.
    trackers = (trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.mapTo(HashSet()) { it.name },
    security = result.securityFindings.mapTo(HashSet()) { it.id },
    version = app.versionCode.takeIf { it > 0 },
)

/** Score de l'appareil : moyenne des scores des apps utilisateur, arrondie comme partout ([PrivacyScore.ofAverage]). */
fun List<AuditedApp>.deviceScore(): PrivacyScore? =
    PrivacyScore.average(filter { AppFilter.USER.matches(it.app) }.map { it.score })
