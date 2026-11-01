package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BlocklistTest {

    private val list = Blocklist.parse(
        """
        {"version":1,"trackers":{"Google DoubleClick":["doubleclick.net"],"Bad":["not a domain","-x.com"]},
         "domains":["metrics.example.com","doubleclick.net","com"]}
        """,
    )

    @Test
    fun `bloque le domaine et ses sous-domaines`() {
        assertEquals(BlockMatch("doubleclick.net", "Google DoubleClick"), list.match("doubleclick.net"))
        assertEquals(BlockMatch("doubleclick.net", "Google DoubleClick"), list.match("googleads.g.DoubleClick.net."))
        assertEquals(BlockMatch("metrics.example.com", null), list.match("a.metrics.example.com"))
    }

    @Test
    fun `ne bloque ni le domaine parent ni un voisin`() {
        assertNull(list.match("example.com"))
        assertNull(list.match("www.example.com"))
        assertNull(list.match("notdoubleclick.net"))
        assertNull(list.match("com"))
        assertNull(list.match(""))
    }

    @Test
    fun `ignore les entrees invalides et garde le traqueur Exodus en priorite`() {
        assertEquals(2, list.size)
    }

    @Test
    fun `la liste embarquee se lit et ne bloque pas les grandes plateformes`() {
        val asset = File("src/main/assets/shield_blocklist.json").readText()
        val embedded = Blocklist.parse(asset)
        assert(embedded.size > 10_000) { "liste trop courte : ${embedded.size}" }
        listOf("www.google.com", "graph.facebook.com", "api.github.com", "www.wikipedia.org", "fonts.gstatic.com")
            .forEach { assertNull(it, embedded.match(it)) }
        assertEquals("Google DoubleClick", embedded.match("ad.doubleclick.net")?.trackerName)
    }

    @Test
    fun `la liste embarquee couvre les regies des jeux mobiles`() {
        val embedded = Blocklist.parse(File("src/main/assets/shield_blocklist.json").readText())
        listOf(
            "outcome-ssp.supersonicads.com", // ironSource
            "an.facebook.com", // Meta Audience Network
            "api16-access-sg.pangle.io", // Pangle
            "configure.rayjump.com", "analytics.mtgglobals.com", // Mintegral
            "auction.unityads.unity3d.com", // Unity Ads
            "ms.applovin.com", // AppLovin
        ).forEach { assert(embedded.match(it) != null) { "$it devrait être bloqué" } }
        assertEquals("Google Firebase Analytics", embedded.match("app-measurement.com")?.trackerName)
    }
}
