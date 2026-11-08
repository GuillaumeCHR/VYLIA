package com.vylia.privacy.domain.databases

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class DatabaseUpdaterTest {

    private val tmp: File = Files.createTempDirectory("db").toFile()

    /** Faux réseau : contenu et ETag par adresse ; une adresse dans [failing] lève une erreur. */
    private class FakeHttp(val bodies: MutableMap<String, Pair<String, String>>, val failing: Set<String> = emptySet()) : Http {
        val gets = mutableListOf<String>()
        val running = AtomicInteger()
        var maxRunning = 0

        override suspend fun request(url: String, validators: Validators?, maxBytes: Long, dest: File?): Http.Response {
            synchronized(this) { maxRunning = maxOf(maxRunning, running.incrementAndGet()) }
            try {
                delay(10)
                if (url in failing) error("réseau indisponible")
                val (body, etag) = bodies.getValue(url)
                if (validators?.etag == etag) return Http.Response.NotModified
                if (dest != null) {
                    synchronized(this) { gets += url }
                    dest.writeText(body)
                }
                return Http.Response.Ok(Validators(etag = etag), body.length.toLong())
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private class MemoryMeta : DatabaseMetaStore {
        var saved: Map<String, DatabaseMeta> = emptyMap()
        override fun load() = saved
        override fun save(meta: Map<String, DatabaseMeta>) { saved = meta }
    }

    /** Base qui n'accepte qu'un contenu commençant par « ok » ; [installed] : version en service. */
    private class FakeSource(
        override val id: String,
        override val affectsScore: Boolean = false,
        override val proposed: Boolean = false,
        var enabled: Boolean = true,
    ) : DatabaseSource {
        override val group = DatabaseGroup.SHIELD
        override val name = id
        override val attribution = "test"
        override val checkUrl = "https://example.org/$id"
        var installed: String? = null
        override fun isEnabled() = enabled
        override fun entries(): Int? = installed?.length
        override suspend fun update(fetch: Fetch): Outcome {
            val text = fetch.text(checkUrl, 1_000) ?: return Outcome.Unchanged
            require(text.startsWith("ok")) { "contenu invalide" }
            installed = text
            return Outcome.Installed(text.length)
        }
    }

    private fun updater(sources: List<DatabaseSource>, http: Http, meta: DatabaseMetaStore = MemoryMeta()) =
        DatabaseUpdater({ sources }, http, meta, tmp, clock = { 1_000L })

    @Test
    fun `un echec n arrete pas les autres et l ancienne version reste en service`() = runTest {
        val a = FakeSource("a")
        val b = FakeSource("b").apply { installed = "ok-ancienne" }
        val c = FakeSource("c")
        val http = FakeHttp(
            mutableMapOf("https://example.org/a" to ("ok-a" to "1"), "https://example.org/b" to ("piège" to "1"), "https://example.org/c" to ("ok-c" to "1")),
            failing = setOf("https://example.org/c"),
        )
        val summary = updater(listOf(a, b, c), http).updateAll()
        assertEquals(emptyMap<String, String>(), summary.failed.filterKeys { it == "a" })
        assertEquals(listOf("a"), summary.updated)
        assertEquals(setOf("b", "c"), summary.failed.keys)
        assertEquals("ok-ancienne", b.installed)
        assertEquals("réseau indisponible", summary.failed["c"])
    }

    @Test
    fun `une base inchangee n est pas retelechargee`() = runTest {
        val a = FakeSource("a")
        val http = FakeHttp(mutableMapOf("https://example.org/a" to ("ok-a" to "v1")))
        val meta = MemoryMeta()
        updater(listOf(a), http, meta).updateAll()
        val second = updater(listOf(a), http, meta).updateAll()
        assertEquals(listOf("a"), second.unchanged)
        assertEquals(1, http.gets.size)
        // Nouvelle version publiée : téléchargée.
        http.bodies["https://example.org/a"] = "ok-a2" to "v2"
        assertEquals(listOf("a"), updater(listOf(a), http, meta).updateAll().updated)
        assertEquals("ok-a2", a.installed)
    }

    @Test
    fun `trois telechargements a la fois au plus`() = runTest {
        val sources = (1..8).map { FakeSource("s$it") }
        val http = FakeHttp(sources.associate { it.checkUrl to ("ok" to "1") }.toMutableMap())
        updater(sources, http).updateAll()
        assertTrue(http.maxRunning <= DatabaseUpdater.CONCURRENCY)
        assertEquals(8, http.gets.size)
    }

    @Test
    fun `une base proposee non ajoutee n est pas telechargee`() = runTest {
        val proposed = FakeSource("cname", proposed = true, enabled = false)
        val http = FakeHttp(mutableMapOf(proposed.checkUrl to ("ok" to "1")))
        val summary = updater(listOf(proposed), http).updateAll()
        assertTrue(summary.updated.isEmpty() && summary.failed.isEmpty())
        assertTrue(http.gets.isEmpty())
    }

    @Test
    fun `echecs consecutifs comptes puis remis a zero`() = runTest {
        val a = FakeSource("a")
        val http = FakeHttp(mutableMapOf(a.checkUrl to ("ok" to "1")), failing = setOf(a.checkUrl))
        val meta = MemoryMeta()
        repeat(3) { updater(listOf(a), http, meta).updateAll() }
        assertEquals(3, meta.saved.getValue("a").consecutiveFailures)
        val rows = DatabaseRows.rows(listOf(a), meta.saved, emptySet())
        assertTrue(DatabaseRows.aggregate(rows).repeatedFailure)
        updater(listOf(a), FakeHttp(mutableMapOf(a.checkUrl to ("ok" to "1"))), meta).updateAll()
        assertEquals(0, meta.saved.getValue("a").consecutiveFailures)
        assertNull(meta.saved.getValue("a").lastError)
    }

    @Test
    fun `le bilan signale un changement qui touche au score`() = runTest {
        val score = FakeSource("exodus", affectsScore = true)
        val other = FakeSource("easylist")
        val http = FakeHttp(mutableMapOf(score.checkUrl to ("ok" to "1"), other.checkUrl to ("ok" to "1")))
        assertTrue(updater(listOf(score, other), http).updateAll().scoreChanged)
        assertFalse(updater(listOf(other), FakeHttp(mutableMapOf(other.checkUrl to ("ok" to "2")))).updateAll().scoreChanged)
    }

    @Test
    fun `un controle annonce une mise a jour disponible sans telecharger`() = runTest {
        val a = FakeSource("a")
        val http = FakeHttp(mutableMapOf(a.checkUrl to ("ok" to "v1")))
        val meta = MemoryMeta()
        updater(listOf(a), http, meta).updateAll()
        http.bodies[a.checkUrl] = "ok2" to "v2"
        updater(listOf(a), http, meta).checkAll()
        assertTrue(meta.saved.getValue("a").updateAvailable)
        assertEquals(1, http.gets.size)
        assertEquals(DatabaseStatus.UPDATE_AVAILABLE, DatabaseRows.rows(listOf(a), meta.saved, emptySet()).single().status)
    }
}
