package com.vylia.privacy.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecorderTest {

    private class MemoryStore(var saved: History? = null) : HistoryStore {
        override fun load() = saved
        override fun save(history: History) { saved = history }
    }

    private val day = 24L * 60 * 60 * 1000
    private var now = 1_000L * day
    private val store = MemoryStore()
    private val recorder = HistoryRecorder(store) { now }

    private val notes = AppPoint("Notes", 90)
    private val maps = AppPoint("Maps", 50, setOf("ACCESS_FINE_LOCATION"))

    @Test
    fun `premier scan - reference sans evenement, moyenne et ecart-type`() {
        recorder.record(mapOf("notes" to notes, "maps" to maps))
        val history = recorder.history.value
        assertEquals(1, history.snapshots.size)
        assertEquals(70.0, history.snapshots.single().average, 0.001)
        assertEquals(20.0, history.snapshots.single().stdDev, 0.001)
        assertTrue(history.events.isEmpty())
    }

    @Test
    fun `scan suivant - installations, suppressions, permissions accordees et retirees`() {
        recorder.record(mapOf("notes" to notes, "maps" to maps))
        now += day
        recorder.record(mapOf("maps" to maps.copy(granted = setOf("CAMERA")), "game" to AppPoint("Game", 30)))
        val events = recorder.history.value.events.map { it.type to (it.permission ?: it.packageName) }.toSet()
        assertEquals(
            setOf(
                EventType.INSTALLED to "game",
                EventType.REMOVED to "notes",
                EventType.PERMISSION_GRANTED to "CAMERA",
                EventType.PERMISSION_REVOKED to "ACCESS_FINE_LOCATION",
            ),
            events,
        )
        assertEquals(2, recorder.history.value.snapshots.size)
        assertEquals(recorder.history.value, store.saved)
    }

    @Test
    fun `traqueur apparu avec une mise a jour des bases sur une app inchangee - evenement marque, sans cause app`() {
        val game = AppPoint("Game", 60, trackers = setOf("Criteo"), security = emptySet(), version = 5)
        recorder.record(mapOf("game" to game), rules = "r1", databases = mapOf("exodus" to "v1"))
        now += day
        recorder.record(mapOf("game" to game.copy(trackers = setOf("Criteo", "Google AdMob"))), rules = "r1", databases = mapOf("exodus" to "v2"))
        val event = recorder.history.value.events.single()
        assertEquals(EventType.TRACKERS_ADDED, event.type)
        assertEquals("Google AdMob", event.detail)
        assertTrue(event.fromDatabases)
        assertEquals(mapOf("exodus" to "v2"), recorder.history.value.snapshots.last().databases)
    }

    @Test
    fun `traqueur ajoute par une mise a jour de l app - evenement non marque`() {
        val game = AppPoint("Game", 60, trackers = setOf("Criteo"), security = emptySet(), version = 5)
        recorder.record(mapOf("game" to game), rules = "r1", databases = mapOf("exodus" to "v1"))
        now += day
        recorder.record(mapOf("game" to game.copy(trackers = setOf("Criteo", "Google AdMob"), version = 6)), rules = "r1", databases = mapOf("exodus" to "v2"))
        assertEquals(false, recorder.history.value.events.single().fromDatabases)
    }

    @Test
    fun `un point par jour - le plus recent remplace celui du jour`() {
        recorder.record(mapOf("notes" to notes))
        now += 60_000
        recorder.record(mapOf("notes" to notes.copy(score = 80)))
        assertEquals(listOf(80.0), recorder.history.value.snapshots.map { it.average })
    }

    @Test
    fun `influences - les apps qui tirent la moyenne vers le bas d abord`() {
        recorder.record(mapOf("notes" to notes, "maps" to maps, "ok" to AppPoint("Ok", 70)))
        val influences = HistoryRecorder.influences(recorder.history.value.snapshots.last())
        assertEquals(listOf("maps", "ok", "notes"), influences.map { it.packageName })
        assertEquals(-10.0, influences.first().pull, 0.001)
    }
}
