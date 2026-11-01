package com.vylia.privacy.domain.shield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldStateTest {

    @Test
    fun `la pause dure cinq minutes puis le blocage reprend seul`() {
        val state = ShieldState(status = ShieldStatus.ON, pausedUntil = 1_000L + ShieldState.PAUSE_MS)
        assertTrue(state.isPaused(1_000L))
        assertTrue(state.isPaused(1_000L + ShieldState.PAUSE_MS - 1))
        assertFalse(state.isPaused(1_000L + ShieldState.PAUSE_MS))
        assertFalse(ShieldState(status = ShieldStatus.ON).isPaused(1_000L))
    }
}
