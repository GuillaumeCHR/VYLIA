package com.vylia.privacy.data.trackers

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.InstalledApp
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerScanCacheTest {

    private val file = Files.createTempDirectory("cache").resolve("tracker_scan_cache.json").toFile()

    private fun app(versionCode: Long, lastUpdateTime: Long = 1L) = InstalledApp(
        label = "App",
        packageName = "org.example",
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
        lastUpdateTime = lastUpdateTime,
        versionCode = versionCode,
        requestedPermissions = emptyList(),
        permissions = emptyList(),
    )

    @Test
    fun `persiste entre deux instances`() = runTest {
        TrackerScanCache(file).put(app(1), "v1", listOf(3, 7))
        assertEquals(listOf(3, 7), TrackerScanCache(file).get(app(1), "v1"))
    }

    @Test
    fun `une mise a jour de l app ou de la base invalide l entree`() = runTest {
        val cache = TrackerScanCache(file)
        cache.put(app(1), "v1", listOf(3))
        assertNull(cache.get(app(2), "v1"))
        assertNull(cache.get(app(1, lastUpdateTime = 2), "v1"))
        assertNull(TrackerScanCache(file).get(app(1), "v2"))
    }

    @Test
    fun `liste vide memorisee comme analyse sans traqueur`() = runTest {
        TrackerScanCache(file).put(app(1), "v1", emptyList())
        assertEquals(emptyList<Int>(), TrackerScanCache(file).get(app(1), "v1"))
    }
}
