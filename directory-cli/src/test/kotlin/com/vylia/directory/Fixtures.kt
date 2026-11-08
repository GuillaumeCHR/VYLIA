package com.vylia.directory

import com.vylia.privacy.domain.scoring.ReferenceApps

/** Réponses Exodus construites pour les tests, au format de l'API. */
object Fixtures {
    fun details(report: Long, updated: String, trackers: List<Int>, permissions: List<String>, version: String = "1.0") = """
        [{"handle":"x","version_name":"$version","version_code":"10","source":"google","updated":"${updated}T10:00:00Z",
          "report":$report,"trackers":${trackers},"permissions":[${permissions.joinToString(",") { "\"$it\"" }}]}]
    """.trimIndent()

    /** Traqueurs des apps de référence, numérotés dans l'ordre de première apparition. */
    val referenceTrackers: Map<Int, ExodusTracker> by lazy {
        ReferenceApps.apps.flatMap { it.trackers }.distinctBy { it.name }
            .mapIndexed { i, t -> ExodusTracker(i + 1, t.name, t.categories) }.associateBy { it.id }
    }

    fun referenceReport(app: ReferenceApps.RefApp): ExodusReport {
        val ids = app.trackers.map { t -> referenceTrackers.values.first { it.name == t.name }.id }
        return checkNotNull(ReportConverter.latestReport(details(100, "2026-10-30", ids, app.permissions)))
    }
}
