package com.vylia.privacy.data.export

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis

/** Rapport d'audit au format CSV (RFC 4180), encodé en UTF-8 avec BOM pour une ouverture correcte dans Excel. */
object CsvExporter {

    /** Textes du rapport, dans la langue de l'app ; [permissionTitle] traduit le titre (ressource) d'une permission. */
    data class Labels(
        val header: List<String>,
        val userApp: String,
        val systemApp: String,
        val notAnalyzed: String,
        val analysisFailed: String,
        val permissionTitle: (Int) -> String,
    )

    fun export(apps: List<AuditedApp>, labels: Labels): String = buildString {
        append('\uFEFF')
        appendRow(labels.header)
        apps.forEach { appendRow(row(it, labels)) }
    }

    private fun row(audited: AuditedApp, labels: Labels): List<String> {
        val app = audited.app
        val analysis = audited.trackerAnalysis
        return listOf(
            app.label,
            app.packageName,
            if (app.isSystemApp) labels.systemApp else labels.userApp,
            audited.score.value.toString(),
            audited.result.grade,
            audited.result.confidence.name,
            trackerCount(analysis, labels),
            (analysis as? TrackerAnalysis.Analyzed)?.trackers?.joinToString(" | ") { it.name }.orEmpty(),
            app.permissions.filter { it.isGranted }.joinToString(" | ") { labels.permissionTitle(it.risk.title) },
        )
    }

    /** Taille réelle de la liste des traqueurs ; distingue "0 traqueur" de "pas (encore) analysé". */
    internal fun trackerCount(analysis: TrackerAnalysis, labels: Labels): String = when (analysis) {
        is TrackerAnalysis.Analyzed -> analysis.trackers.size.toString()
        TrackerAnalysis.NotAnalyzed -> labels.notAnalyzed
        TrackerAnalysis.Failed -> labels.analysisFailed
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.joinTo(this, separator = ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        append("\r\n")
    }
}
