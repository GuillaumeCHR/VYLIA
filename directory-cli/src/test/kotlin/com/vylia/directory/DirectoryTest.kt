package com.vylia.directory

import com.vylia.privacy.domain.scoring.ReferenceApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DirectoryTest {

    private val rulesText = File("app/src/main/assets/vylia_rules_v3.json").readText()
    private val apps = ReferenceApps.apps.map { AppEntry(it.packageName, it.packageName, "X", listOf("FR")) } +
        AppEntry("com.unknown.app", "Inconnue", "X", listOf("DE"))
    private val reports = ReferenceApps.apps.associate { it.packageName to Fixtures.referenceReport(it) } + ("com.unknown.app" to null)

    private fun build(date: String = "2026-11-09") =
        DirectoryBuilder.build(apps.shuffled(), reports, Fixtures.referenceTrackers, ReferenceApps.rules, rulesText, date)

    @Test
    fun `sortie deterministe - memes entrees, memes octets`() {
        assertEquals(DirectoryBuilder.encode(build()), DirectoryBuilder.encode(build()))
        assertEquals(build(), DirectoryBuilder.decode(DirectoryBuilder.encode(build())))
    }

    @Test
    fun `app sans rapport - pas encore analysee, licence ODbL`() {
        val directory = build()
        val unknown = directory.apps.single { it.packageName == "com.unknown.app" }
        assertEquals("NOT_ANALYZED", unknown.status)
        assertEquals(null, unknown.score)
        assertEquals("ODbL-1.0", directory.license)
        assertTrue(directory.attribution.contains("Exodus Privacy"))
        assertFalse(directory.apps.first { it.status == "SCORED" }.targetSdkKnown)
    }

    @Test
    fun `garde-fou - plus de 10 pour cent de notes perdues bloque`() {
        fun dir(scored: Int, total: Int = 100) = Directory(
            generated = "d", rulesVersion = "r",
            apps = (1..total).map { DirectoryApp("p$it", "n", "X", emptyList(), if (it <= scored) "SCORED" else "NOT_ANALYZED", score = if (it <= scored) 50 else null) },
        )
        assertTrue(PublishGuard.check(null, dir(0)).ok)
        assertTrue(PublishGuard.check(dir(100), dir(91)).ok)
        assertFalse(PublishGuard.check(dir(100), dir(89)).ok)
        assertEquals(11, PublishGuard.check(dir(100), dir(89)).lost)
    }

    @Test
    fun `validation - liste complete et categories coherentes`() {
        val directory = build()
        val overrides = apps.associate { it.packageName to "X" }
        assertTrue(DirectoryValidator.problems(directory, apps, overrides).isEmpty())
        assertTrue(DirectoryValidator.problems(directory, apps.drop(1), overrides).isNotEmpty())
        assertTrue(DirectoryValidator.problems(directory, apps, overrides + ("com.whatsapp" to "SOCIAL")).isNotEmpty())
    }

    @Test
    fun `site - une fiche par app, aucune ressource externe`() {
        val out = File(System.getProperty("java.io.tmpdir"), "vylia-site-test-${System.nanoTime()}")
        try {
            SiteGenerator(build(), ReferenceApps.rules, null, mapOf("com.whatsapp" to listOf("2026-11-02" to 90, "2026-11-09" to 92))).write(out)
            assertEquals(apps.size, File(out, "apps").list()!!.size)
            out.walkTopDown().filter { it.extension == "html" }.forEach { page ->
                val html = page.readText()
                assertFalse(page.name, Regex("""(src|href)="https?://[^"]*\.(js|css|woff2?)""").containsMatchIn(html))
                assertTrue(page.name, html.contains("ODbL"))
            }
            assertTrue(File(out, "apps/com.whatsapp.html").readText().contains("<svg"))
        } finally {
            out.deleteRecursively()
        }
    }
}
