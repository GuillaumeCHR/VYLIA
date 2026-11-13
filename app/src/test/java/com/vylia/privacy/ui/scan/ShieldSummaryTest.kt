package com.vylia.privacy.ui.scan

import android.graphics.drawable.Drawable
import com.vylia.privacy.data.shield.ShieldExposureRepository
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.ScoreResult
import com.vylia.privacy.domain.scoring.ShieldedScore
import com.vylia.privacy.domain.scoring.TrackerFinding
import com.vylia.privacy.domain.shield.TrackerCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShieldSummaryTest {

    private fun audited(score: Int, vararg trackers: String) = AuditedApp(
        app = InstalledApp(
            label = "App",
            packageName = "com.example.app$score",
            icon = object : Drawable() {
                override fun draw(canvas: android.graphics.Canvas) = Unit
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
                @Deprecated("Deprecated in Java")
                override fun getOpacity() = 0
            },
            isSystemApp = false,
            category = null,
            apkPaths = emptyList(),
            lastUpdateTime = 0L,
            requestedPermissions = emptyList(),
            permissions = emptyList(),
        ),
        score = PrivacyScore(score),
        trackerAnalysis = TrackerAnalysis.Analyzed(emptyList()),
        result = ScoreResult(score, "C", Confidence.FULL, "DEFAULT", 0.0, 0, emptyList(), trackers.map { TrackerFinding(it, 5) }),
    )

    private val exposure = ShieldExposureRepository.Exposure(
        mapOf("Ads" to TrackerCoverage.BLOCKED, "Stats" to TrackerCoverage.PARTIAL),
        active = true,
    )

    @Test
    fun `moyenne des expositions, score de l'app si non calculable, traqueurs distincts`() {
        val a = audited(50, "Ads", "Stats")
        val b = audited(60, "Ads", "Maps")
        val c = audited(90)
        val shielded = mapOf(a to 70, b to 66)
        val summary = ShieldSummary.of(listOf(a, b, c), exposure) { app ->
            shielded[app]?.let { ShieldedScore(it, "B", 0.0, emptyMap()) }
        }!!
        assertEquals(PrivacyScore.ofAverage((70 + 66 + 90) / 3.0), summary.score)
        assertEquals(1, summary.blocked)
        assertEquals(1, summary.partial)
        assertEquals(3, summary.trackers)
    }

    @Test
    fun `aucune app, rien a afficher`() {
        assertNull(ShieldSummary.of(emptyList(), exposure) { null })
    }
}
