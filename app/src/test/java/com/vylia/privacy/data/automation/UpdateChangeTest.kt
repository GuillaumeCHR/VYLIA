package com.vylia.privacy.data.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateChangeTest {
    private fun baseline(
        version: Long,
        score: Int = 70,
        trackers: Set<String>? = setOf("Firebase"),
        permissions: Set<String> = setOf("android.permission.CAMERA"),
        databases: String = "exodus=1",
        shield: Boolean = false,
    ) = AppBaseline(version, score, trackers, permissions, databases, shield)

    @Test
    fun `same version is not an update`() {
        assertNull(UpdateChange.between(baseline(1), baseline(1, trackers = setOf("Firebase", "Criteo"))))
    }

    @Test
    fun `an update that changes nothing VYLIA scores is not reported`() {
        assertNull(UpdateChange.between(baseline(1), baseline(2, score = 68)))
    }

    @Test
    fun `added and removed trackers and permissions are reported with both scores`() {
        val change = UpdateChange.between(
            baseline(1, score = 72, trackers = setOf("Firebase", "Adjust"), permissions = setOf("android.permission.CAMERA")),
            baseline(2, score = 58, trackers = setOf("Firebase", "Criteo", "Facebook"), permissions = setOf("android.permission.READ_CONTACTS")),
        )!!
        assertEquals(setOf("Criteo", "Facebook"), change.trackersAdded)
        assertEquals(setOf("Adjust"), change.trackersRemoved)
        assertEquals(setOf("android.permission.READ_CONTACTS"), change.permissionsAdded)
        assertEquals(setOf("android.permission.CAMERA"), change.permissionsRemoved)
        assertEquals(72, change.scoreBefore)
        assertEquals(58, change.scoreAfter)
    }

    @Test
    fun `after a database update trackers and score are not compared, permissions still are`() {
        val before = baseline(1, trackers = setOf("Firebase"))
        assertNull(UpdateChange.between(before, baseline(2, trackers = setOf("Firebase", "Criteo"), databases = "exodus=2")))
        val change = UpdateChange.between(before, baseline(2, trackers = setOf("Firebase", "Criteo"), permissions = before.permissions + "android.permission.RECORD_AUDIO", databases = "exodus=2"))!!
        assertEquals(emptySet<String>(), change.trackersAdded)
        assertEquals(setOf("android.permission.RECORD_AUDIO"), change.permissionsAdded)
        assertNull(change.scoreBefore)
    }

    @Test
    fun `unknown trackers on either side are not compared`() {
        assertNull(UpdateChange.between(baseline(1, trackers = null), baseline(2, trackers = setOf("Criteo"))))
    }

    @Test
    fun `a shield turned on or off hides the score before`() {
        val change = UpdateChange.between(baseline(1, score = 60), baseline(2, score = 75, trackers = emptySet(), shield = true))!!
        assertEquals(setOf("Firebase"), change.trackersRemoved)
        assertNull(change.scoreBefore)
    }
}
