package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldExceptionsTest {

    private val list = Blocklist.parse(
        """
        {"version":2,"groups":{
          "spyware":{"trackers":{"Pegasus":["evil-spy.com"]}},
          "web":{"trackers":{"Traqueur de test":["app-measurement.com"]},"domains":["metrics.example.com","evil-spy.com"]}}}
        """,
    )

    @Test
    fun `l exception leve le blocage du domaine et de ses sous-domaines, pas du parent`() {
        val exceptions = ShieldExceptions.EMPTY + "metrics.example.com"
        assertNull(exceptions.cover("metrics.example.com", list.match("metrics.example.com")))
        assertNull(exceptions.cover("a.metrics.example.com", list.match("a.metrics.example.com")))
        assertTrue(exceptions.covers("A.Metrics.Example.com."))
        assertFalse(exceptions.covers("example.com"))
        assertEquals(list.match("app-measurement.com"), exceptions.cover("app-measurement.com", list.match("app-measurement.com")))
    }

    @Test
    fun `jamais pour un logiciel espion`() {
        val exceptions = ShieldExceptions.EMPTY + "evil-spy.com"
        val match = list.match("evil-spy.com")
        assertNull(match!!.filter)
        assertEquals(match, exceptions.cover("evil-spy.com", match))
    }

    @Test
    fun `domaines invalides ecartes, nombre borne, retrait`() {
        val exceptions = ShieldExceptions.of(listOf("not a domain", "com", "ok.example.com") + List(300) { "d$it.example.com" })
        assertEquals(ShieldExceptions.MAX, exceptions.domains.size)
        assertTrue(exceptions.covers("ok.example.com"))
        assertFalse((exceptions - "ok.example.com").covers("ok.example.com"))
    }

    @Test
    fun `la couverture des traqueurs tient compte des exceptions`() {
        val trackers = listOf(ExodusNetworkTracker("Traqueur de test", listOf("Analytics"), "app-measurement\\.com"))
        assertEquals(TrackerCoverage.BLOCKED, TrackerCoverages.of(trackers, list, setOf(ShieldFilter.WEB))["Traqueur de test"])
        assertEquals(
            TrackerCoverage.NONE,
            TrackerCoverages.of(trackers, list, setOf(ShieldFilter.WEB), ShieldExceptions.EMPTY + "app-measurement.com")["Traqueur de test"],
        )
    }
}
