package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `serveurs connus bloques par la liste embarquee sauf les regies, jamais ajoutes a la liste`() {
        val embedded = Blocklist.parse(java.io.File("src/main/assets/shield_blocklist.json").readText())
        // Régies publicitaires : pas de filtrage mobile dans cette version, leurs serveurs passent.
        val ads = setOf(
            "Amazon Advertisement", "Pangle", "Appodeal Stack", "BidMachine", "ironSource", "Singular", "Google AdMob",
            "Facebook Ads", "Facebook Audience",
        )
        ExodusShieldDomains.KNOWN_SERVERS.forEach { (name, domains) ->
            domains.forEach {
                if (name in ads) assertNull("$name : $it", embedded.match(it)) else assertNotNull("$name : $it", embedded.match(it)?.filter)
            }
        }
        val named = ExodusShieldDomains.named(emptyList())
        assertFalse(ExodusShieldDomains.KNOWN_SERVERS.values.flatten().any { it in named })
    }

    @Test
    fun `complement maison ajoute`() {
        assertEquals("Google Firebase Analytics" to ShieldFilter.WEB, named["app-measurement.com"])
    }
}
