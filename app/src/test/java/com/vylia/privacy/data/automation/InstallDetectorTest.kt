package com.vylia.privacy.data.automation

import com.vylia.privacy.data.automation.InstallDetector.Installed
import com.vylia.privacy.data.preferences.SettingsRepository.InstallCursor
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallDetectorTest {
    private val cursor = InstallCursor(bootCount = 3, sequence = 10, checkedAt = 1_000)

    @Test
    fun `first check only sets the starting point`() {
        val result = InstallDetector.detect(null, 3, 2_000, { 12 to listOf("a") }, { Installed(1_500, false) })
        assertEquals(InstallCursor(3, 12, 2_000), result.cursor)
        assertEquals(emptyList<String>(), result.packages)
    }

    @Test
    fun `new installs are reported, system apps and removed apps are not`() {
        val apps = mapOf(
            "new" to Installed(1_500, ignored = false),
            "updated" to Installed(500, ignored = false),
            "system" to Installed(1_500, ignored = true),
        )
        var asked = -1
        val result = InstallDetector.detect(cursor, 3, 2_000, { asked = it; 15 to listOf("new", "updated", "system", "gone", "new") }, apps::get)
        assertEquals(10, asked)
        assertEquals(listOf("new"), result.packages)
        assertEquals(InstallCursor(3, 15, 2_000), result.cursor)
    }

    @Test
    fun `updates of apps already there are reported apart`() {
        val apps = mapOf(
            "new" to Installed(1_500, ignored = false, lastUpdateTime = 1_800),
            "updated" to Installed(500, ignored = false, lastUpdateTime = 1_500),
            "old" to Installed(500, ignored = false, lastUpdateTime = 800),
            "system" to Installed(500, ignored = true, lastUpdateTime = 1_500),
        )
        val result = InstallDetector.detect(cursor, 3, 2_000, { 15 to apps.keys.toList() }, apps::get)
        assertEquals(listOf("new"), result.packages)
        assertEquals(listOf("updated"), result.updated)
    }

    @Test
    fun `after a reboot the sequence is read from zero`() {
        var asked = -1
        val result = InstallDetector.detect(cursor, 4, 2_000, { asked = it; 2 to listOf("new") }, { Installed(1_500, false) })
        assertEquals(0, asked)
        assertEquals(listOf("new"), result.packages)
        assertEquals(InstallCursor(4, 2, 2_000), result.cursor)
    }

    @Test
    fun `nothing changed keeps the sequence and moves the check time`() {
        val result = InstallDetector.detect(cursor, 3, 2_000, { null }, { error("not called") })
        assertEquals(InstallCursor(3, 10, 2_000), result.cursor)
        assertEquals(emptyList<String>(), result.packages)
    }
}
