package com.vylia.privacy.data.automation

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.domain.scoring.PermissionFinding
import com.vylia.privacy.domain.scoring.ScoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBaselinesTest {
    private class MemoryStore : BaselineStore {
        var saved: Map<String, AppBaseline> = emptyMap()
        override fun load() = saved
        override fun save(baselines: Map<String, AppBaseline>) { saved = baselines }
    }

    private val store = MemoryStore()
    private var databases = mapOf("exodus" to "1")
    private val baselines = UpdateBaselines(store, databases = { databases }, shield = { false })

    private fun audited(pkg: String, version: Long, score: Int, trackers: List<String>?, permissions: Map<String, Double> = emptyMap()) = AuditedApp(
        app = InstalledApp(
            label = pkg,
            packageName = pkg,
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
            versionCode = version,
        ),
        score = PrivacyScore(score),
        trackerAnalysis = trackers?.let { names -> TrackerAnalysis.Analyzed(names.mapIndexed { i, n -> Tracker(i, n, null, emptyList()) }) } ?: TrackerAnalysis.NotAnalyzed,
        result = ScoreResult(
            score, "C", Confidence.FULL, "DEFAULT", 0.0, 0,
            permissions.map { (p, penalty) -> PermissionFinding(p, granted = false, context = Context.UNEXPECTED, penalty = penalty) },
            emptyList(),
        ),
    )

    @Test
    fun `an update is compared with the last full scan`() {
        baselines.replaceAll(listOf(audited("a", 1, 72, listOf("Firebase")), audited("b", 1, 90, emptyList())))
        val changes = baselines.update(listOf(audited("a", 2, 58, listOf("Firebase", "Criteo"), mapOf("android.permission.READ_CONTACTS" to 5.0, "android.permission.INTERNET" to 0.0))))
        assertEquals(1, changes.size)
        val change = changes.single().second
        assertEquals(setOf("Criteo"), change.trackersAdded)
        assertEquals(setOf("android.permission.READ_CONTACTS"), change.permissionsAdded)
        assertEquals(72, change.scoreBefore)
        // Le nouvel état remplace l'ancien : la mise à jour suivante part de la version 2.
        assertEquals(2L, store.saved.getValue("a").version)
        assertEquals(setOf("a", "b"), store.saved.keys)
    }

    @Test
    fun `an app never analysed is not reported, and its next update is`() {
        assertTrue(baselines.update(listOf(audited("new", 1, 80, listOf("Firebase")))).isEmpty())
        val changes = baselines.update(listOf(audited("new", 2, 80, emptyList())))
        assertEquals(setOf("Firebase"), changes.single().second.trackersRemoved)
    }

    @Test
    fun `a tracker that appears with a database update is not blamed on the app`() {
        baselines.replaceAll(listOf(audited("a", 1, 72, listOf("Firebase"))))
        databases = mapOf("exodus" to "2")
        assertTrue(baselines.update(listOf(audited("a", 2, 60, listOf("Firebase", "Criteo")))).isEmpty())
    }

    @Test
    fun `a full scan drops removed apps and clear empties everything`() {
        baselines.replaceAll(listOf(audited("a", 1, 72, null), audited("b", 1, 90, null)))
        baselines.replaceAll(listOf(audited("b", 1, 90, null)))
        assertEquals(setOf("b"), store.saved.keys)
        baselines.clear()
        assertTrue(store.saved.isEmpty())
    }
}
