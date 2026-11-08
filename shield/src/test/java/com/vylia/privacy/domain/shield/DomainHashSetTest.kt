package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class DomainHashSetTest {

    @Test
    fun `contient exactement les domaines donnes, sans doublon`() {
        val set = DomainHashSet.of(listOf("a.example.com", "b.example.com", "a.example.com"))
        assertEquals(2, set.size)
        assertTrue("a.example.com" in set)
        assertFalse("example.com" in set)
        assertFalse("c.example.com" in set)
    }

    @Test
    fun `relu a l identique`() {
        val domains = (1..1000).map { "d$it.example.com" }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { DomainHashSet.of(domains).write(it) }
        val read = DomainHashSet.read(DataInputStream(ByteArrayInputStream(out.toByteArray())), 10_000)
        assertEquals(1000, read.size)
        assertTrue(domains.all { it in read })
    }

    @Test
    fun `la liste compilee relue bloque la meme chose`() {
        val list = Blocklist.parse(
            """{"version":2,"groups":{"mobile":{"trackers":{"Unity Ads":["unityads.unity3d.com"]}},
               "web":{"domains":["metrics.example.com"]},"spyware":{"trackers":{"Pegasus":["evil.example.net"]}}}}""",
        )
        val out = ByteArrayOutputStream()
        list.write(out)
        val read = Blocklist.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(list.size, read.size)
        listOf("x.unityads.unity3d.com", "a.metrics.example.com", "evil.example.net", "example.com", "com").forEach {
            assertEquals(list.match(it), read.match(it))
        }
        assertEquals("Unity Ads", read.match("unityads.unity3d.com")?.trackerName)
    }
}
