package com.vylia.privacy.domain.widget

import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.shield.ShieldBypass
import com.vylia.privacy.domain.shield.ShieldIndicator
import com.vylia.privacy.domain.shield.ShieldIndicatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStateTest {

    private val now = 1_000_000L
    private val active = ShieldIndicator(ShieldIndicatorState.ACTIVE)

    private fun state(indicator: ShieldIndicator, pausedUntil: Long? = null, needsConsent: Boolean = false) =
        WidgetState.of(72, "B", indicator, pausedUntil, blockedToday = 12, needsConsent = needsConsent, now = now)

    @Test
    fun `bouclier actif - le bouton met en pause`() {
        val s = state(active)
        assertEquals(WidgetShield.ACTIVE, s.shield)
        assertEquals(WidgetAction.PAUSE, s.action)
        assertEquals(ScoreRating.FAIR, s.rating)
    }

    @Test
    fun `en pause - le bouton reprend, une pause finie ne compte plus`() {
        val paused = state(active, pausedUntil = now + 60_000)
        assertEquals(WidgetShield.PAUSED, paused.shield)
        assertEquals(WidgetAction.RESUME, paused.action)
        val over = state(active, pausedUntil = now - 1)
        assertEquals(WidgetShield.ACTIVE, over.shield)
        assertNull(over.pausedUntil)
    }

    @Test
    fun `arrete - activation directe, ou ouverture de l app sans accord VPN`() {
        val off = ShieldIndicator.INACTIVE
        assertEquals(WidgetAction.ACTIVATE, state(off).action)
        assertEquals(WidgetAction.OPEN_APP, state(off, needsConsent = true).action)
    }

    @Test
    fun `contourne - l app explique pourquoi, en demarrage - rien`() {
        assertEquals(WidgetAction.OPEN_APP, state(ShieldIndicator(ShieldIndicatorState.BYPASSED, ShieldBypass.OTHER_VPN)).action)
        assertEquals(WidgetAction.NONE, state(ShieldIndicator(ShieldIndicatorState.STARTING)).action)
    }

    @Test
    fun `pas encore de scan - ni score ni couleur`() {
        val s = WidgetState.of(null, null, active, null, 0, needsConsent = false, now = now)
        assertNull(s.rating)
    }
}
