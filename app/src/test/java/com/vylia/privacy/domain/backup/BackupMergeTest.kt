package com.vylia.privacy.domain.backup

import com.vylia.privacy.data.backup.BackupRepository
import com.vylia.privacy.domain.history.AppPoint
import com.vylia.privacy.domain.history.EventType
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryEvent
import com.vylia.privacy.domain.history.ScoreSnapshot
import com.vylia.privacy.domain.rewards.AppSnapshot
import com.vylia.privacy.domain.rewards.Badge
import com.vylia.privacy.domain.rewards.RemovedApp
import com.vylia.privacy.domain.rewards.RewardsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupMergeTest {
    private val now = 10_000_000L
    private fun snap(at: Long, average: Double = 60.0) = ScoreSnapshot(at, average, 5.0, mapOf("a" to AppPoint("A", 60)))
    private fun event(at: Long, pkg: String = "a") = HistoryEvent(at, EventType.INSTALLED, pkg, "A", score = 60)

    @Test
    fun `history adds what is new, ignores what is there, sets aside the inconsistent`() {
        val current = History(listOf(snap(100), snap(200)), listOf(event(200)))
        val incoming = History(
            snapshots = listOf(snap(50), snap(200), snap(300, average = 140.0), snap(now + 2 * 86_400_000L)),
            events = listOf(event(200), event(150, "b"), event(-1)),
        )
        val merged = BackupMerge.history(current, incoming, now)
        assertEquals(listOf(50L, 100L, 200L), merged.value.snapshots.map { it.at })
        assertEquals(listOf(200L, 150L), merged.value.events.map { it.at })
        assertEquals(1, merged.added)
        assertEquals(1, merged.addedEvents)
        assertEquals(2, merged.alreadyThere)
        assertEquals(3, merged.invalid)
    }

    @Test
    fun `rewards are added, the phone keeps its own app state`() {
        val current = RewardsState(
            snapshot = mapOf("here" to AppSnapshot("Here", 40, 3)),
            removedApps = listOf(RemovedApp("x", "X", 20, 5, 1_000)),
            unlockedBadges = mapOf(Badge.FIRST_AUDIT to 500L),
            completedScans = 4,
            swaps = setOf("s1"),
        )
        val incoming = RewardsState(
            snapshot = mapOf("elsewhere" to AppSnapshot("Elsewhere", 10, 9)),
            removedApps = listOf(RemovedApp("x", "X", 20, 5, 1_000), RemovedApp("y", "Y", 30, 2, 2_000)),
            unlockedBadges = mapOf(Badge.FIRST_AUDIT to 100L, Badge.DETOX to 300L),
            completedScans = 9,
            swaps = setOf("s2"),
        )
        val merged = BackupMerge.rewards(current, incoming, now)
        assertEquals(setOf("here"), merged.value.snapshot.keys)
        assertEquals(listOf("y", "x"), merged.value.removedApps.map { it.packageName })
        assertEquals(mapOf(Badge.FIRST_AUDIT to 100L, Badge.DETOX to 300L), merged.value.unlockedBadges)
        assertEquals(9, merged.value.completedScans)
        assertEquals(setOf("s1", "s2"), merged.value.swaps)
        assertEquals(2, merged.added)
        assertEquals(1, merged.alreadyThere)
    }

    @Test
    fun `files that are not backups or come from a newer version are refused`() {
        fun error(text: String) = assertThrows(RestoreException::class.java) { BackupRepository.parse(text) }.error
        assertEquals(RestoreError.NOT_A_BACKUP, error("""{"hello": 1}"""))
        assertEquals(RestoreError.NOT_A_BACKUP, error("not json"))
        assertEquals(RestoreError.NOT_A_BACKUP, error("""{"format":"other","exportedAt":1,"app":"x"}"""))
        assertEquals(RestoreError.NEWER_VERSION, error("""{"format":"vylia-backup","version":99,"exportedAt":1,"app":"x"}"""))
        assertEquals(1, BackupRepository.parse("""{"format":"vylia-backup","version":1,"exportedAt":1,"app":"x"}""").version)
    }
}
