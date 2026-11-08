package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExodusShieldDomainsTest {

    private val named = ExodusShieldDomains.named(
        listOf(
            ExodusNetworkTracker("AdNet", listOf("Advertisement"), "\\.adnet\\.com|ads\\.adnet\\.io"),
            ExodusNetworkTracker("Stats", listOf("Analytics"), ".*\\.stats\\.example\\.org|\\.facebook\\.com|[0-9]+\\.x"),
            ExodusNetworkTracker("Maps", listOf("Location"), "maps\\.example\\.com"),
        ),
    )

    @Test
    fun `categories bloquees seulement, rangees par filtrage`() {
        assertEquals("AdNet" to ShieldFilter.MOBILE, named["adnet.com"])
        assertEquals("AdNet" to ShieldFilter.MOBILE, named["ads.adnet.io"])
        assertEquals("Stats" to ShieldFilter.WEB, named["stats.example.org"])
        assertFalse("maps.example.com" in named)
    }

    @Test
    fun `plateformes et signatures qui ne sont pas des domaines ecartees`() {
        assertFalse("facebook.com" in named)
        assertFalse(named.keys.any { it.contains('[') || it.contains('+') })
    }

    @Test
    fun `complement maison ajoute`() {
        assertEquals("Google Firebase Analytics" to ShieldFilter.WEB, named["app-measurement.com"])
    }
}
