package com.vylia.privacy.data.shield

import com.vylia.privacy.domain.scoring.ShieldCoverage
import com.vylia.privacy.domain.shield.ExodusNetworkTracker
import com.vylia.privacy.domain.shield.ShieldIndicatorState
import com.vylia.privacy.domain.shield.TrackerCoverage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Ce que le bouclier bloque des traqueurs, pour la fiche d'une app : recalculé quand la liste ou les filtrages
 * changent, jamais à chaque requête. [trackers] : la base Exodus et ses domaines (téléchargée, sinon celle de l'app).
 */
class ShieldExposureRepository(
    private val controller: ShieldController,
    private val trackers: () -> List<ExodusNetworkTracker>?,
) {
    /** [coverage] par nom de traqueur ; [active] : le bouclier filtre en ce moment (ni arrêté, ni contourné). */
    data class Exposure(val coverage: Map<String, TrackerCoverage>, val active: Boolean) {
        fun of(tracker: String): TrackerCoverage = coverage[tracker] ?: TrackerCoverage.UNKNOWN

        /** Vu du score : un traqueur sans domaine connu passe, comme un traqueur non bloqué. */
        fun forScore(tracker: String): ShieldCoverage = when (of(tracker)) {
            TrackerCoverage.BLOCKED -> ShieldCoverage.BLOCKED
            TrackerCoverage.PARTIAL -> ShieldCoverage.PARTIAL
            TrackerCoverage.NONE, TrackerCoverage.UNKNOWN -> ShieldCoverage.NONE
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coverage = combine(
        controller.state.map { it.filters }.distinctUntilChanged(),
        controller.listVersion,
    ) { _, _ -> }
        .mapLatest { trackers()?.let { runCatching { controller.coverage(it) }.getOrNull() } }

    val exposure: StateFlow<Exposure?> = combine(
        coverage,
        controller.indicator.map { it.state == ShieldIndicatorState.ACTIVE }.distinctUntilChanged(),
    ) { coverage, active -> coverage?.let { Exposure(it, active) } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
