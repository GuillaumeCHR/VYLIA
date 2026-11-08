package com.vylia.directory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportConverterTest {

    private val trackers = mapOf(7 to ExodusTracker(7, "Google AdMob", listOf("Advertisement")))

    @Test
    fun `noms courts, permission propre a l app gardee, tout est accorde`() {
        val report = ReportConverter.latestReport(
            Fixtures.details(1, "2026-10-01", listOf(7), listOf("android.permission.CAMERA", "com.example.permission.C2D", "android.permission.INTERNET")),
        )!!
        val scan = ReportConverter.toScan("com.example", report, trackers)
        assertEquals(setOf("CAMERA", "com.example.permission.C2D", "INTERNET"), scan.declaredPermissions)
        assertEquals(scan.declaredPermissions, scan.grantedPermissions)
        assertEquals(listOf("Google AdMob"), scan.trackers!!.map { it.name })
        assertTrue(scan.signals.isEmpty())
    }

    @Test
    fun `sdk cible inconnu - l acces historique au stockage compte`() {
        val report = ReportConverter.latestReport(Fixtures.details(1, "2026-10-01", emptyList(), listOf("android.permission.READ_EXTERNAL_STORAGE")))!!
        val scan = ReportConverter.toScan("com.example", report, trackers)
        assertTrue(scan.targetSdk < 33)
    }

    @Test
    fun `liste de traqueurs vide - analysee, aucun traqueur`() {
        val report = ReportConverter.latestReport(Fixtures.details(1, "2026-10-01", emptyList(), emptyList()))!!
        assertEquals(emptyList<Any>(), ReportConverter.toScan("com.example", report, trackers).trackers)
    }

    @Test
    fun `rapport absent ou vide - pas de rapport`() {
        assertNull(ReportConverter.latestReport("[]"))
        assertNull(ReportConverter.latestReport("{}"))
    }

    @Test
    fun `le rapport le plus recent est retenu`() {
        val text = "[" + Fixtures.details(1, "2026-01-01", emptyList(), emptyList()).trim('[', ']', ' ', '\n') + "," +
            Fixtures.details(2, "2026-06-01", emptyList(), emptyList()).trim('[', ']', ' ', '\n') + "]"
        assertEquals(2L, ReportConverter.latestReport(text)!!.report)
    }

    @Test
    fun `categories manquantes completees par les categories ajoutees par VYLIA`() {
        val api = """{"trackers":{"5":{"name":"AdLib","categories":[]},"6":{"id":6,"name":"Stats","categories":["Analytics"]}}}"""
        val overrides = ReportConverter.overrides("""{"validated":true,"categories":{"AdLib":["Advertisement"]}}""")
        val parsed = ReportConverter.trackers(api, overrides)
        assertEquals(listOf("Advertisement"), parsed[5]!!.categories)
        assertEquals(listOf("Analytics"), parsed[6]!!.categories)
        assertTrue(ReportConverter.overrides("""{"validated":false,"categories":{"AdLib":["Advertisement"]}}""").isEmpty())
    }
}
