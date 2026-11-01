package com.vylia.privacy.domain.act

import com.vylia.privacy.data.dns.PrivateDnsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivateDnsTest {
    private val bundled = PrivateDnsRepository.parse(File("src/main/assets/private_dns.json").readText())
    private val quad9 = bundled.resolvers.single { it.id == "quad9" }
    private val nextdns = bundled.resolvers.single { it.id == "nextdns" }

    @Test
    fun `the bundled list offers Quad9 first, as recommended, then NextDNS only`() {
        assertEquals(listOf("quad9", "nextdns"), bundled.availableIn("FR").map { it.id })
        assertTrue(quad9.recommended)
        assertEquals("dns.quad9.net", quad9.hostnameFor())
        assertTrue(bundled.resolvers.all { it.privacyPolicy.startsWith("https://") })
    }

    @Test
    fun `NextDNS hostname is built from the pasted ID or full hostname`() {
        assertNull(nextdns.hostnameFor(""))
        assertEquals("abc123.dns.nextdns.io", nextdns.hostnameFor("abc123"))
        assertEquals("abc123.dns.nextdns.io", nextdns.hostnameFor("  ABC123.dns.nextdns.io "))
        assertNull(nextdns.hostnameFor("evil.example.com"))
        assertNull(nextdns.hostnameFor("abc12"))
    }

    @Test
    fun `a resolver can be hidden or unavailable in a country without an app update`() {
        val list = bundled.copy(
            resolvers = listOf(quad9.copy(unavailableCountries = listOf("de")), nextdns.copy(available = false)),
        )
        assertEquals(listOf("quad9"), list.availableIn("FR").map { it.id })
        assertEquals(emptyList<String>(), list.availableIn("DE").map { it.id })
    }

    @Test
    fun `a downloaded list with bad hostnames, links or patterns is cleaned or refused`() {
        val evil = bundled.copy(
            resolvers = listOf(
                quad9.copy(id = "a", hostname = "dns.quad9.net; rm -rf"),
                quad9.copy(id = "b", website = "http://insecure.example"),
                nextdns.copy(id = "c", idPattern = "(a+)+"),
                quad9.copy(id = "d", name = "Good‮"),
            ),
        )
        val cleaned = PrivateDnsValidator.validate(evil)
        assertEquals(listOf("d"), cleaned.resolvers.map { it.id })
        assertEquals("Good", cleaned.resolvers.single().name)
        assertThrows(IllegalArgumentException::class.java) { PrivateDnsValidator.validate(bundled.copy(format = "other")) }
        assertThrows(IllegalArgumentException::class.java) {
            PrivateDnsValidator.validate(bundled.copy(resolvers = listOf(quad9.copy(hostname = "not a host"))))
        }
    }
}
