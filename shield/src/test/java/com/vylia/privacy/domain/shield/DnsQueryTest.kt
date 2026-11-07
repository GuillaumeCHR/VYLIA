package com.vylia.privacy.domain.shield

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsQueryTest {

    /** Requête DNS « A ads.Example.com » (id 0x1234, RD) de 10.111.222.1:40000 vers 10.111.222.2:53, avec EDNS. */
    private fun query(name: String = "ads.Example.com", edns: Boolean = true): ByteArray {
        val dns = mutableListOf<Int>()
        dns += listOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, if (edns) 1 else 0)
        name.split('.').forEach { label -> dns += label.length; label.forEach { dns += it.code } }
        dns += listOf(0, 0, 1, 0, 1)
        if (edns) dns += listOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0)
        val udpLength = 8 + dns.size
        val total = 20 + udpLength
        val ip = listOf(0x45, 0, total shr 8, total and 0xFF, 0, 0, 0x40, 0, 64, 17, 0, 0, 10, 111, 222, 1, 10, 111, 222, 2)
        val udp = listOf(40000 shr 8, 40000 and 0xFF, 0, 53, udpLength shr 8, udpLength and 0xFF, 0, 0)
        return (ip + udp + dns).map { it.toByte() }.toByteArray()
    }

    @Test
    fun `lit le nom et le type demandes`() {
        val packet = query()
        val q = DnsQuery.parse(packet, packet.size)
        assertNotNull(q)
        q!!
        assertEquals("ads.example.com", q.name)
        assertEquals(1, q.type)
        assertEquals(packet.size - 28, q.message.size)
    }

    @Test
    fun `refuse ce qui n est pas une requete DNS IPv4 UDP valide`() {
        val packet = query()
        assertNull(DnsQuery.parse(packet, 30)) // tronqué
        assertNull(DnsQuery.parse(packet.copyOf().also { it[9] = 6 }, packet.size)) // TCP
        assertNull(DnsQuery.parse(packet.copyOf().also { it[23] = 54 }, packet.size)) // pas le port 53
        assertNull(DnsQuery.parse(packet.copyOf().also { it[30] = 0x81.toByte() }, packet.size)) // une réponse
        assertNull(DnsQuery.parse(packet.copyOf().also { it[0] = 0x60 }, packet.size)) // IPv6
        assertNull(DnsQuery.parse(packet.copyOf().also { it[40] = 0xC0.toByte() }, packet.size)) // pointeur de compression
        assertNull(DnsQuery.parse(packet.copyOf().also { it[40] = 60 }, packet.size)) // label qui déborde
    }

    @Test
    fun `la reponse bloquee est un NXDOMAIN qui reprend la question seule`() {
        val packet = query()
        val q = DnsQuery.parse(packet, packet.size)!!
        val response = q.blockedResponse()
        val questionEnd = 12 + "ads.Example.com".length + 2 + 4
        assertEquals(questionEnd, response.size) // l'enregistrement EDNS n'est pas recopié
        assertEquals(0x12, response[0].toInt())
        assertEquals(0x34, response[1].toInt())
        assertEquals(0x81, response[2].toInt() and 0xFF) // QR + RD
        assertEquals(0x83, response[3].toInt() and 0xFF) // RA + NXDOMAIN
        assertArrayEquals(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0), response.copyOfRange(4, 12))
        assertArrayEquals(q.message.copyOfRange(12, questionEnd), response.copyOfRange(12, questionEnd))
    }

    @Test
    fun `le paquet de reponse inverse adresses et ports et a un en-tete IPv4 valide`() {
        val packet = query(edns = false)
        val q = DnsQuery.parse(packet, packet.size)!!
        val payload = byteArrayOf(1, 2, 3)
        val reply = q.reply(payload)
        assertEquals(31, reply.size)
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), reply.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), reply.copyOfRange(16, 20))
        assertEquals(53, ((reply[20].toInt() and 0xFF) shl 8) or (reply[21].toInt() and 0xFF))
        assertEquals(40000, ((reply[22].toInt() and 0xFF) shl 8) or (reply[23].toInt() and 0xFF))
        assertEquals(11, reply[25].toInt())
        assertArrayEquals(payload, reply.copyOfRange(28, 31))
        // Un en-tête valide se vérifie à 0 quand le champ de contrôle est inclus dans la somme.
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((reply[i].toInt() and 0xFF) shl 8) or (reply[i + 1].toInt() and 0xFF)
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        assertEquals(0xFFFF, sum)
    }
}
