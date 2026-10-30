package com.vylia.privacy.data.export

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis

/** Rapport d'audit au format CSV (RFC 4180), encodé en UTF-8 avec BOM pour une ouverture correcte dans Excel. */
object CsvExporter {

    private val header = listOf(
        "Nom de l'application",
        "Package",
        "Type",
        "Score",
        "Nombre de traqueurs",
        "Traqueurs détectés",
        "Permissions à risque accordées",
    )

    fun export(apps: List<AuditedApp>): String = buildString {
        append('﻿')
        appendRow(header)
        apps.forEach { appendRow(row(it)) }
    }

    private fun row(audited: AuditedApp): List<String> {
        val app = audited.app
        val analysis = audited.trackerAnalysis
        return listOf(
            app.label,
            app.packageName,
            if (app.isSystemApp) "Système" else "Utilisateur",
            audited.score.value.toString(),
            trackerCount(analysis),
            (analysis as? TrackerAnalysis.Analyzed)?.trackers?.joinToString(" | ") { it.name }.orEmpty(),
            app.permissions.filter { it.isGranted }.joinToString(" | ") { it.risk.title },
        )
    }

    /** Taille réelle de la liste des traqueurs ; distingue "0 traqueur" de "pas (encore) analysé". */
    internal fun trackerCount(analysis: TrackerAnalysis): String = when (analysis) {
        is TrackerAnalysis.Analyzed -> analysis.trackers.size.toString()
        TrackerAnalysis.NotAnalyzed -> "Non analysé"
        TrackerAnalysis.Failed -> "Échec de l'analyse"
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.joinTo(this, separator = ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        append("\r\n")
    }
}
