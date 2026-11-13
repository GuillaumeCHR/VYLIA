package com.vylia.privacy.domain.act

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.scoring.ShieldFactors
import com.vylia.privacy.domain.shield.TrackerCoverage

/** Traqueur qu'un bouclier actif ne bloque pas en entier, et la part de son poids qui passe encore. */
data class LeakingTracker(val name: String, val coverage: TrackerCoverage, val penalty: Double)

/**
 * App dont des traqueurs passent malgré le bouclier : seules la désinstallation ou une alternative les retirent.
 * [penalty] : somme de ce qui passe, avant le plafond des traqueurs (sert au tri, pas au score).
 */
data class ShieldLeak(val app: AuditedApp, val trackers: List<LeakingTracker>) {
    val penalty: Double get() = trackers.sumOf { it.penalty }
}

object ShieldLeaks {

    /**
     * Les apps dont au moins un traqueur n'est pas bloqué en entier (en partie, pas du tout, ou sans serveur connu),
     * de la plus exposée à la moins exposée. Un traqueur bloqué en entier n'y figure pas : le bouclier fait ce qu'il
     * peut pour lui. Les apps dont les traqueurs ne sont pas analysés sont écartées.
     */
    fun of(apps: List<AuditedApp>, factors: ShieldFactors, coverageOf: (String) -> TrackerCoverage): List<ShieldLeak> =
        apps.mapNotNull { app ->
            val trackers = app.result.trackerFindings.mapNotNull { finding ->
                val coverage = coverageOf(finding.name)
                val factor = when (coverage) {
                    TrackerCoverage.BLOCKED -> return@mapNotNull null
                    TrackerCoverage.PARTIAL -> factors.partialFactor
                    TrackerCoverage.NONE, TrackerCoverage.UNKNOWN -> 1.0
                }
                LeakingTracker(finding.name, coverage, finding.penalty * factor)
            }.sortedByDescending { it.penalty }
            trackers.takeIf { it.isNotEmpty() }?.let { ShieldLeak(app, it) }
        }.sortedWith(compareByDescending<ShieldLeak> { it.penalty }.thenBy { it.app.app.label })

    /** Traqueurs distincts qui passent, toutes apps confondues. */
    fun distinctTrackers(leaks: List<ShieldLeak>): Int = leaks.flatMapTo(HashSet()) { leak -> leak.trackers.map { it.name } }.size
}
