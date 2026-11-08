package com.vylia.directory

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class ExodusClientTest {

    @Test
    fun `jeton et user-agent envoyes, cadence respectee, jeton absent des erreurs`() {
        val seen = mutableListOf<Pair<String?, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                seen += exchange.requestHeaders.getFirst("Authorization") to exchange.requestHeaders.getFirst("User-Agent")
                val (code, body) = when (exchange.requestURI.path) {
                    "/api/search/com.found.app/details" -> 200 to "[{\"report\":1}]"
                    "/api/search/com.missing.app/details" -> 404 to ""
                    "/api/trackers" -> 500 to ""
                    else -> 200 to "[]"
                }
                exchange.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.length.toLong())
                if (body.isNotEmpty()) exchange.responseBody.use { it.write(body.toByteArray()) }
                exchange.close()
            }
            start()
        }
        try {
            var now = 100_000L
            val sleeps = mutableListOf<Long>()
            val client = ExodusClient("SECRET", "http://127.0.0.1:${server.address.port}", intervalMs = 2_000, sleep = { sleeps += it; now += it }, clock = { now })
            assertEquals("[{\"report\":1}]", client.details("com.found.app"))
            assertNull(client.details("com.missing.app"))
            assertNull(client.details("com.empty.app"))
            val error = runCatching { client.trackers() }.exceptionOrNull()!!
            assertFalse(error.message!!.contains("SECRET"))
            assertTrue(seen.all { it.first == "Token SECRET" && it.second == ExodusClient.USER_AGENT })
            assertEquals(listOf(2_000L, 2_000L, 2_000L), sleeps)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `nom de paquet controle avant toute requete`() {
        val client = ExodusClient("T", "http://127.0.0.1:1")
        assertTrue(runCatching { client.details("../../admin") }.isFailure)
    }
}
