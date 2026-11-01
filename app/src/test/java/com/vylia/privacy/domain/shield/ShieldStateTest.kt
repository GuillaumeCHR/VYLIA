package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldStateTest {

    @Test
    fun `la pause dure le temps choisi puis le blocage reprend seul`() {
        val end = 1_000L + 60 * 60_000L
        val state = ShieldState(status = ShieldStatus.ON, pausedUntil = end, pauseMinutes = 60)
        assertTrue(state.isPaused(1_000L))
        assertTrue(state.isPaused(end - 1))
        assertFalse(state.isPaused(end))
        assertFalse(ShieldState(status = ShieldStatus.ON).isPaused(1_000L))
    }

    @Test
    fun `moins et plus parcourent les durees proposees sans en sortir`() {
        assertEquals(5, ShieldState.shorterPause(5))
        assertEquals(5, ShieldState.shorterPause(10))
        assertEquals(45, ShieldState.shorterPause(60))
        assertEquals(90, ShieldState.longerPause(60))
        assertEquals(120, ShieldState.longerPause(120))
        assertEquals(5, ShieldState().pauseMinutes)
    }
}
