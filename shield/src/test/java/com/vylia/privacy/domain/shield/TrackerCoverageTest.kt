package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerCoverageTest {

    private val blocklist = Blocklist(
        web = DomainHashSet.of(setOf("stats.example.org", "app-measurement.com", "half.net")),
        mobile = DomainHashSet.of(setOf("adnet.com")),
    )

    private val trackers = listOf(
        ExodusNetworkTracker("AdNet", listOf("Advertisement"), "\\.adnet\\.com|cdn\\.adnet\\.com"),
        ExodusNetworkTracker("Stats", listOf("Analytics"), ".*\\.stats\\.example\\.org"),
        ExodusNetworkTracker("Half", listOf("Analytics"), "half\\.net|other\\.net"),
        ExodusNetworkTracker("Direct", listOf("Analytics"), "half\\.net|54\\.243\\.73\\.253:8080/x/"),
        ExodusNetworkTracker("Platform", listOf("Analytics"), "logging\\.googleapis\\.com"),
        ExodusNetworkTracker("Google Firebase Analytics", listOf("Analytics"), "firebaselogging\\.googleapis\\.com"),
    )

    private fun coverage(filters: Set<ShieldFilter> = ShieldFilter.entries.toSet()) =
        TrackerCoverages.of(trackers, blocklist, filters)

    @Test
    fun `tous les domaines bloques, une partie, aucun`() {
        val c = coverage()
        assertEquals(TrackerCoverage.BLOCKED, c["AdNet"])
        assertEquals(TrackerCoverage.BLOCKED, c["Stats"])
        assertEquals(TrackerCoverage.PARTIAL, c["Half"])
        assertEquals(TrackerCoverage.NONE, c["Platform"])
    }

    @Test
    fun `une signature qui n'est pas un domaine compte comme non bloquee`() {
        assertEquals(TrackerCoverage.PARTIAL, coverage()["Direct"])
    }

    @Test
    fun `filtrage mobile coupe, les regies passent`() {
        val c = coverage(setOf(ShieldFilter.WEB))
        assertEquals(TrackerCoverage.NONE, c["AdNet"])
        assertEquals(TrackerCoverage.BLOCKED, c["Stats"])
    }

    @Test
    fun `complement maison compte avec la signature Exodus`() {
        assertEquals(TrackerCoverage.PARTIAL, coverage()["Google Firebase Analytics"])
    }

    @Test
    fun `traqueur sans domaine connu`() {
        assertEquals(TrackerCoverage.UNKNOWN, TrackerCoverages.of("Inconnu", coverage()))
    }
}
