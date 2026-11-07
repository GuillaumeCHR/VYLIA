package com.vylia.privacy.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryProgressTest {
    private val day = 24L * 60 * 60 * 1000
    private fun point(dayIndex: Int, average: Double) = ScoreSnapshot(dayIndex * day, average, 0.0, emptyMap())

    @Test
    fun `aucun progres avec un seul point`() {
        assertNull(History(listOf(point(0, 60.0))).progress())
        assertNull(History().progress())
    }

    @Test
    fun `historique court - ecart depuis le premier scan`() {
        val progress = History(listOf(point(0, 60.0), point(5, 63.4), point(10, 66.2))).progress()
        assertEquals(History.Progress(points = 6, sinceStart = true), progress)
    }

    @Test
    fun `historique long - ecart sur trente jours`() {
        val progress = History(listOf(point(0, 40.0), point(20, 55.0), point(45, 58.0), point(50, 52.0))).progress()
        // Référence : le jour 20, le plus récent à au moins 30 jours du dernier point.
        assertEquals(History.Progress(points = -3, sinceStart = false), progress)
    }
}
