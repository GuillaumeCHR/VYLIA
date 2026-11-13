package com.vylia.privacy.domain.act

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.ScoreResult
import com.vylia.privacy.domain.scoring.ShieldFactors
import com.vylia.privacy.domain.scoring.TrackerFinding
import com.vylia.privacy.domain.shield.TrackerCoverage
import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldLeaksTest {

    private fun audited(label: String, vararg trackers: Pair<String, Int>) = AuditedApp(
        app = InstalledApp(
            label = label,
            packageName = "com.example.$label",
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
        score = PrivacyScore(50),
        trackerAnalysis = TrackerAnalysis.Analyzed(emptyList()),
        result = ScoreResult(50, "C", Confidence.FULL, "DEFAULT", 0.0, 0, emptyList(), trackers.map { TrackerFinding(it.first, it.second) }),
    )

    private val coverage = mapOf(
        "Ads" to TrackerCoverage.BLOCKED,
        "Stats" to TrackerCoverage.PARTIAL,
        "Firebase" to TrackerCoverage.NONE,
    )

    private val leaks = ShieldLeaks.of(
        listOf(
            audited("Clean", "Ads" to 12),
            audited("Small", "Stats" to 5),
            audited("Big", "Ads" to 12, "Firebase" to 5, "Mystery" to 10),
        ),
        ShieldFactors(blockedFactor = 0.3, partialFactor = 0.7),
    ) { coverage[it] ?: TrackerCoverage.UNKNOWN }

    @Test
    fun `seuls les traqueurs pas bloques en entier, apps les plus exposees d'abord`() {
        assertEquals(listOf("Big", "Small"), leaks.map { it.app.app.label })
        assertEquals(listOf("Mystery", "Firebase"), leaks[0].trackers.map { it.name })
        assertEquals(TrackerCoverage.UNKNOWN, leaks[0].trackers[0].coverage)
        assertEquals(15.0, leaks[0].penalty, 1e-9)
        assertEquals(3.5, leaks[1].penalty, 1e-9)
    }

    @Test
    fun `traqueurs distincts`() {
        assertEquals(3, ShieldLeaks.distinctTrackers(leaks))
    }
}
