package com.vylia.privacy.domain.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesManagerTest {

    private class MemoryStore(var saved: RewardsState? = null) : RewardsStore {
        override fun load() = saved
        override fun save(state: RewardsState) {
            saved = state
        }
    }

    private val store = MemoryStore()
    private val manager = BadgesManager(store, clock = { 1_000L })

    private val intrusive = AppSnapshot("Espion", score = 20, trackerCount = 12)
    private val clean = AppSnapshot("Notes", score = 95, trackerCount = 0)

    @Test
    fun `aucune recompense au premier scan`() {
        assertTrue(manager.onAppsListed(setOf("spy", "notes")).isEmpty())
        manager.onScanCompleted(mapOf("spy" to intrusive, "notes" to clean))

        assertEquals(setOf(Badge.FIRST_AUDIT), manager.state.value.unlockedBadges.keys)
        assertEquals(0, manager.state.value.points)
    }

    @Test
    fun `recompense la suppression d une app intrusive detectee au scan suivant`() {
        manager.onScanCompleted(mapOf("spy" to intrusive, "notes" to clean))

        val rewarded = manager.onAppsListed(setOf("notes"))

        assertEquals(listOf(RemovedApp("spy", "Espion", 20, 12, 1_000L)), rewarded)
        assertEquals(80, manager.state.value.points)
        assertTrue(Badge.FIRST_CLEANUP in manager.state.value.unlockedBadges)
        assertFalse("spy" in manager.state.value.snapshot)
    }

    @Test
    fun `ne recompense pas la suppression d une app non intrusive`() {
        manager.onScanCompleted(mapOf("notes" to clean))

        assertTrue(manager.onAppsListed(emptySet()).isEmpty())
        assertTrue(manager.state.value.snapshot.isEmpty())
    }

    @Test
    fun `une suppression en direct n est pas recompensee une seconde fois au scan suivant`() {
        manager.onScanCompleted(mapOf("spy" to intrusive, "notes" to clean))

        assertEquals(1, manager.onAppsRemoved(mapOf("spy" to intrusive)).size)
        assertTrue(manager.onAppsListed(setOf("notes")).isEmpty())
        assertEquals(1, manager.state.value.removedApps.size)
    }

    @Test
    fun `debloque les badges de score et persiste l etat`() {
        manager.onScanCompleted(mapOf("notes" to clean, "maps" to AppSnapshot("Maps", 82, 1)))

        assertEquals(setOf(Badge.FIRST_AUDIT, Badge.NO_RED_ZONE, Badge.GREEN_DEVICE), manager.state.value.unlockedBadges.keys)
        assertEquals(manager.state.value, BadgesManager(store).state.value)
    }

    @Test
    fun `chasseur de traqueurs apres 20 traqueurs elimines`() {
        manager.onScanCompleted(mapOf("a" to intrusive, "b" to intrusive.copy(label = "Pub")))
        manager.onAppsListed(emptySet())

        assertTrue(Badge.TRACKER_HUNTER in manager.state.value.unlockedBadges)
    }
}
