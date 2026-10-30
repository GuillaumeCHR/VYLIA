package com.vylia.privacy.data.export

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun audited(label: String, analysis: TrackerAnalysis, isSystemApp: Boolean = false) = AuditedApp(
        app = InstalledApp(
            label = label,
            packageName = "com.example.app",
            // Jamais lu par l'export : un stub Android suffit.
            icon = object : Drawable() {
                override fun draw(canvas: android.graphics.Canvas) = Unit
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
                @Deprecated("Deprecated in Java")
                override fun getOpacity() = 0
            },
            isSystemApp = isSystemApp,
            category = null,
            apkPaths = emptyList(),
            lastUpdateTime = 0L,
            requestedPermissions = emptyList(),
            permissions = emptyList(),
        ),
        score = PrivacyScore(42),
        trackerAnalysis = analysis,
    )

    private fun rows(csv: String) = csv.removePrefix("﻿").trimEnd().split("\r\n")

    @Test
    fun `exporte la taille reelle de la liste des traqueurs et leurs noms`() {
        val trackers = List(3) { Tracker(it, "Tracker $it", null, emptyList()) }
        val row = rows(CsvExporter.export(listOf(audited("App", TrackerAnalysis.Analyzed(trackers)))))[1]

        assertEquals(
            "\"App\",\"com.example.app\",\"Utilisateur\",\"42\",\"3\",\"Tracker 0 | Tracker 1 | Tracker 2\",\"\"",
            row,
        )
    }

    @Test
    fun `distingue zero traqueur d une app non analysee ou en echec`() {
        assertEquals("0", CsvExporter.trackerCount(TrackerAnalysis.Analyzed(emptyList())))
        assertEquals("Non analysé", CsvExporter.trackerCount(TrackerAnalysis.NotAnalyzed))
        assertEquals("Échec de l'analyse", CsvExporter.trackerCount(TrackerAnalysis.Failed))
    }

    @Test
    fun `echappe les guillemets et commence par un BOM UTF-8`() {
        val csv = CsvExporter.export(listOf(audited("Super \"App\", v2", TrackerAnalysis.NotAnalyzed, isSystemApp = true)))

        assertTrue(csv.startsWith("﻿\"Nom de l'application\""))
        assertTrue(rows(csv)[1].startsWith("\"Super \"\"App\"\", v2\",\"com.example.app\",\"Système\""))
    }
}
