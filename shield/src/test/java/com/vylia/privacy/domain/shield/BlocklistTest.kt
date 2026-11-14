package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BlocklistTest {

    private val list = Blocklist.parse(
        """
        {"version":2,"groups":{
          "mobile":{"trackers":{"Google DoubleClick":["doubleclick.net"]},"domains":["ads.unity3d.com"]},
          "web":{"trackers":{"Bad":["not a domain","-x.com"]},
                 "domains":["metrics.example.com","doubleclick.net","com","unity3d.com","x.ads.unity3d.com"]}}}
        """,
    )

    @Test
    fun `bloque le domaine et ses sous-domaines`() {
        assertEquals(BlockMatch("doubleclick.net", ShieldFilter.MOBILE, "Google DoubleClick"), list.match("doubleclick.net"))
        assertEquals(BlockMatch("doubleclick.net", ShieldFilter.MOBILE, "Google DoubleClick"), list.match("googleads.g.DoubleClick.net."))
        assertEquals(BlockMatch("metrics.example.com", ShieldFilter.WEB, null), list.match("a.metrics.example.com"))
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
    fun `ignore les entrees invalides`() {
        assertEquals(6, list.size)
    }

    @Test
    fun `chaque filtrage ne bloque que ses domaines`() {
        val web = setOf(ShieldFilter.WEB)
        val mobile = setOf(ShieldFilter.MOBILE)
        assertEquals(ShieldFilter.WEB, list.match("a.metrics.example.com", web)?.filter)
        assertNull(list.match("a.metrics.example.com", mobile))
        assertEquals(ShieldFilter.MOBILE, list.match("ads.unity3d.com", mobile)?.filter)
    }

    @Test
    fun `le reglage mobile l emporte sur une liste web qui cite le meme domaine ou un parent`() {
        val web = setOf(ShieldFilter.WEB)
        // doubleclick.net est dans les deux groupes : filtrage mobile coupé, il passe.
        assertNull(list.match("googleads.g.doubleclick.net", web))
        // ads.unity3d.com est mobile, unity3d.com web : le sous-domaine mobile passe, le reste du domaine web est bloqué.
        assertNull(list.match("ads.unity3d.com", web))
        assertNull(list.match("x.ads.unity3d.com", web))
        assertEquals(BlockMatch("unity3d.com", ShieldFilter.WEB, null), list.match("cdn.unity3d.com", web))
        // Les deux actifs : le domaine le plus précis est retenu.
        assertEquals(BlockMatch("x.ads.unity3d.com", ShieldFilter.WEB, null), list.match("x.ads.unity3d.com"))
    }

    @Test
    fun `la liste embarquee se lit et ne bloque pas les grandes plateformes`() {
        val asset = File("src/main/assets/shield_blocklist.json").readText()
        val embedded = Blocklist.parse(asset)
        assert(embedded.size > 10_000) { "liste trop courte : ${embedded.size}" }
        listOf("www.google.com", "graph.facebook.com", "api.github.com", "www.wikipedia.org", "fonts.gstatic.com")
            .forEach { assertNull(it, embedded.match(it)) }
        assertEquals("Google Firebase Analytics", embedded.match("app-measurement.com")?.trackerName)
    }

    @Test
    fun `la liste embarquee bloque les traqueurs mais pas les regies publicitaires`() {
        val embedded = Blocklist.parse(File("src/main/assets/shield_blocklist.json").readText())
        // Ni groupe mobile ni régie dans le groupe web, tous filtrages actifs : Google Play interdit de gêner les pubs.
        listOf(
            "outcome-ssp.supersonicads.com", // ironSource
            "an.facebook.com", // Meta Audience Network
            "api16-access-sg.pangle.io", // Pangle
            "auction.unityads.unity3d.com", // Unity Ads
            "ms.applovin.com", // AppLovin
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", "adservice.google.com", // AdMob, DoubleClick
        ).forEach { assertNull(it, embedded.match(it)) }
        val web = setOf(ShieldFilter.WEB)
        assertEquals(ShieldFilter.WEB, embedded.match("app-measurement.com", web)?.filter)
        assertEquals(ShieldFilter.WEB, embedded.match("api2.amplitude.com", web)?.filter)
    }

    @Test
    fun `logiciel espion bloque meme filtrages coupes, avec le nom de la campagne`() {
        val spy = Blocklist.parse(
            """{"version":2,"groups":{"web":{"domains":["tracker.example"]},
               "spyware":{"trackers":{"Predator (Intellexa)":["spy-c2.example"]}}}}""",
        )
        assertEquals(BlockMatch("spy-c2.example", null, "Predator (Intellexa)"), spy.match("a.spy-c2.example", emptySet()))
        assertNull(spy.match("tracker.example", emptySet()))
    }

    @Test
    fun `liste embarquee - domaines MVT presents`() {
        val embedded = Blocklist.parse(File("src/main/assets/shield_blocklist.json").readText())
        assertEquals(null, embedded.match("nsoqa.com", emptySet())?.filter)
        assertEquals("Pegasus (NSO Group)", embedded.match("nsoqa.com", emptySet())?.trackerName)
    }
}
