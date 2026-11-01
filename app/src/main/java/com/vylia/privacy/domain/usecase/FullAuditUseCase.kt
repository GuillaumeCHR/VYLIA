package com.vylia.privacy.domain.usecase

import com.vylia.privacy.domain.history.AppPoint
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp

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
fun AuditedApp.historyPoint(): AppPoint =
    AppPoint(app.label, score.value, result.permissionFindings.filter { it.granted }.mapTo(HashSet()) { it.permission })
