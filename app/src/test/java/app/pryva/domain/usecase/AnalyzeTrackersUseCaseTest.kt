package app.pryva.domain.usecase

import android.Manifest
import android.graphics.drawable.Drawable
import app.pryva.domain.model.InstalledApp
import app.pryva.domain.model.Tracker
import app.pryva.domain.model.TrackerAnalysis
import app.pryva.domain.repository.TrackerAnalyzer
import app.pryva.domain.scoring.PrivacyScorer
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeTrackersUseCaseTest {

    private val scorer = PrivacyScorer()

    private class FakeAnalyzer(
        private val available: Boolean = true,
        private val results: Map<String, Result<List<Tracker>>> = emptyMap(),
    ) : TrackerAnalyzer {
        override suspend fun isAvailable() = available
        override suspend fun findTrackers(app: InstalledApp) = results.getValue(app.packageName).getOrThrow()
    }

    private fun app(packageName: String, vararg permissions: String) = InstalledApp(
        label = packageName,
        packageName = packageName,
        // Jamais lu par le cas d'usage : un stub Android suffit.
        icon = object : Drawable() {
            override fun draw(canvas: android.graphics.Canvas) = Unit
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = 0
        },
        isSystemApp = false,
        apkPaths = emptyList(),
        lastUpdateTime = 0L,
        requestedPermissions = permissions.toList(),
        permissions = emptyList(),
    )

    private fun trackers(count: Int) = List(count) { Tracker(it, "T$it", null, emptyList()) }

    @Test
    fun `re-note chaque app avec ses traqueurs`() = runTest {
        val camera = app("camera", Manifest.permission.CAMERA)
        val broken = app("broken")
        val analyzer = FakeAnalyzer(
            results = mapOf("camera" to Result.success(trackers(4)), "broken" to Result.failure(IOException())),
        )
        val initial = listOf(camera, broken).map { scorer.scoreApp(it, TrackerAnalysis.NotAnalyzed) }

        val results = AnalyzeTrackersUseCase(analyzer, scorer)(initial).toList()

        // 100 - 25 (caméra) - 20 (4 traqueurs)
        assertEquals(55, results[0].score.value)
        assertEquals(4, (results[0].trackerAnalysis as TrackerAnalysis.Analyzed).trackers.size)
        assertEquals(TrackerAnalysis.Failed, results[1].trackerAnalysis)
        assertEquals(100, results[1].score.value)
    }

    @Test
    fun `n emet rien sans base de signatures`() = runTest {
        val initial = listOf(scorer.scoreApp(app("a"), TrackerAnalysis.NotAnalyzed))
        assertTrue(AnalyzeTrackersUseCase(FakeAnalyzer(available = false), scorer)(initial).toList().isEmpty())
    }
}
