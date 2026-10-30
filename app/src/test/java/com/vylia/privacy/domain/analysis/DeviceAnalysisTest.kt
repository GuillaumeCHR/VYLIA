package com.vylia.privacy.domain.analysis

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.domain.scoring.PermissionFinding
import com.vylia.privacy.domain.scoring.ScoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAnalysisTest {

    private val ads = Tracker(1, "AdMob", null, listOf("Advertisement"))
    private val crash = Tracker(2, "Sentry", null, listOf("Crash reporting"))
    private val unknown = Tracker(3, "Mystery", null, emptyList())

    private fun app(
        name: String,
        score: Int,
        category: String = "DEFAULT",
        findings: List<PermissionFinding> = emptyList(),
        declared: Set<String> = emptySet(),
        granted: Set<String> = emptySet(),
        trackers: List<Tracker>? = emptyList(),
    ) = AuditedApp(
        app = InstalledApp(
            label = name,
            packageName = "org.example.$name",
            // Jamais lu par l'analyse : un stub Android suffit.
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
            declaredPermissions = declared,
            grantedPermissions = granted,
            requestedPermissions = emptyList(),
            permissions = emptyList(),
        ),
        score = PrivacyScore(score),
        trackerAnalysis = trackers?.let { TrackerAnalysis.Analyzed(it) } ?: TrackerAnalysis.NotAnalyzed,
        result = ScoreResult(score, "C", Confidence.FULL, category, 0.0, 0, findings, emptyList()),
    )

    @Test
    fun `repartit les apps par zone de risque, plus risquee d abord`() {
        val analysis = DeviceAnalysis.from(listOf(app("a", 90), app("b", 30), app("c", 60), app("d", 10)))

        assertEquals(listOf("d", "b"), analysis.zones.getValue(ScoreRating.POOR).map { it.app.label })
        assertEquals(listOf("c"), analysis.zones.getValue(ScoreRating.FAIR).map { it.app.label })
        assertEquals(listOf("a"), analysis.zones.getValue(ScoreRating.GOOD).map { it.app.label })
    }

    @Test
    fun `anomalies - interdites toujours, inattendues seulement pour une categorie connue`() {
        val forbidden = PermissionFinding("READ_CONTACTS", true, Context.FORBIDDEN, 16.0)
        val unexpected = PermissionFinding("CAMERA", true, Context.UNEXPECTED, 4.0)
        val tolerated = PermissionFinding("ACCESS_FINE_LOCATION", true, Context.TOLERATED, 3.2)
        val analysis = DeviceAnalysis.from(
            listOf(
                app("torch", 60, "UTILITY", listOf(unexpected, tolerated)),
                app("unknown", 70, "DEFAULT", listOf(unexpected)),
                app("shop", 40, "ECOMMERCE", listOf(forbidden)),
            )
        )

        assertEquals(listOf("shop", "torch"), analysis.anomalies.map { it.app.app.label })
        assertEquals(listOf(unexpected), analysis.anomalies[1].findings)
    }

    @Test
    fun `apps par permission, accordees separees des seulement declarees`() {
        val analysis = DeviceAnalysis.from(
            listOf(
                app("a", 50, declared = setOf("RECORD_AUDIO", "CAMERA"), granted = setOf("RECORD_AUDIO")),
                app("b", 60, declared = setOf("RECORD_AUDIO")),
            )
        )

        val micro = analysis.permissions.first()
        assertEquals(WatchedPermission.MICROPHONE, micro.permission)
        assertEquals(listOf("a"), micro.granted.map { it.app.label })
        assertEquals(listOf("b"), micro.declaredOnly.map { it.app.label })
        assertEquals(listOf(WatchedPermission.MICROPHONE, WatchedPermission.CAMERA), analysis.permissions.map { it.permission })
    }

    @Test
    fun `cartographie des traqueurs et de leurs categories`() {
        val analysis = DeviceAnalysis.from(
            listOf(
                app("a", 40, trackers = listOf(ads, crash)),
                app("b", 50, trackers = listOf(ads, unknown)),
                app("c", 90, trackers = emptyList()),
                app("d", 80, trackers = null),
            )
        )

        assertEquals(listOf("AdMob" to 2, "Mystery" to 1, "Sentry" to 1), analysis.trackers.map { it.tracker.name to it.apps.size })
        assertEquals(
            setOf("Advertisement" to 2, "Crash reporting" to 1, DeviceAnalysis.UNCATEGORIZED to 1),
            analysis.trackerCategories.toSet(),
        )
        assertEquals(3, analysis.analyzedAppCount)
        assertEquals(2, analysis.appsWithTrackers)
        assertTrue(analysis.anomalies.isEmpty())
    }
}
