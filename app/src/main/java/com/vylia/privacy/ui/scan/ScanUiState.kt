package com.vylia.privacy.ui.scan

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.data.shield.ShieldExposureRepository
import com.vylia.privacy.domain.scoring.ShieldedScore
import com.vylia.privacy.domain.shield.TrackerCoverage

data class ScanUiState(
    val isLoading: Boolean = true,
    /** Mode Basique (`false`) : seules les apps utilisateur sont scannées et l'onglet Système est masqué. */
    val advancedMode: Boolean = false,
    val selectedFilter: AppFilter = AppFilter.USER,
    /** Apps de l'onglet sélectionné, de la plus à risque à la moins à risque. */
    val apps: List<AuditedApp> = emptyList(),
    /** Toutes les apps du scan (les deux onglets en mode Avancé) : écran Analyse et détail d'une app. */
    val scannedApps: List<AuditedApp> = emptyList(),
    /** Score moyen de l'onglet sélectionné ; `null` tant qu'il n'y a aucune app. */
    val averageScore: PrivacyScore? = null,
    /** Score moyen par type d'apps, quel que soit l'onglet (écran Analyse). */
    val userAverageScore: PrivacyScore? = null,
    val systemAverageScore: PrivacyScore? = null,
    /** Score moyen de toutes les apps scannées : celui de l'écran Analyse (apps système comprises en mode Avancé). */
    val allAverageScore: PrivacyScore? = null,
    val userAppCount: Int = 0,
    val systemAppCount: Int = 0,
    /** Analyse DEX des traqueurs encore en cours (les scores peuvent encore baisser). */
    val isAnalyzingTrackers: Boolean = false,
    /** Fin du dernier scan complet, conservée entre deux lancements ; `null` avant le premier. */
    val lastScanAt: Long? = null,
    val errorMessage: String? = null,
    /** Exposition de l'appareil avec le bouclier (apps utilisateur, puis toutes les apps) ; `null` sans bouclier. */
    val userShield: ShieldSummary? = null,
    val allShield: ShieldSummary? = null,
)

/**
 * Le bouclier pour un groupe d'apps, à côté de son score : bouclier actif, les scores sont déjà ceux avec lui et
 * [score] est la moyenne sans lui ; arrêté, [score] est la moyenne qu'il donnerait. Une app dont les traqueurs ne
 * sont pas analysés garde son score. Aussi : les traqueurs distincts que le bouclier bloque.
 */
data class ShieldSummary(
    /** Bouclier actif : score sans lui ; sinon : score avec lui. */
    val score: PrivacyScore,
    /** Traqueurs distincts bloqués en entier, en partie, et en tout dans ces apps. */
    val blocked: Int,
    val partial: Int,
    val trackers: Int,
    /** Le bouclier filtre en ce moment ; sinon [score] est ce qu'il permettrait. */
    val active: Boolean,
) {
    companion object {
        fun of(
            apps: List<AuditedApp>,
            exposure: ShieldExposureRepository.Exposure,
            shielded: (AuditedApp) -> ShieldedScore?,
        ): ShieldSummary? {
            if (apps.isEmpty()) return null
            val score = PrivacyScore.ofAverage(
                apps.map { if (exposure.active) it.result.scoreWithoutShield else shielded(it)?.score ?: it.score.value }.average(),
            )
            val names = apps.flatMapTo(HashSet()) { app -> app.result.trackerFindings.map { it.name } }
            val coverage = names.map(exposure::of)
            return ShieldSummary(
                score = score,
                blocked = coverage.count { it == TrackerCoverage.BLOCKED },
                partial = coverage.count { it == TrackerCoverage.PARTIAL },
                trackers = names.size,
                active = exposure.active,
            )
        }
    }
}
