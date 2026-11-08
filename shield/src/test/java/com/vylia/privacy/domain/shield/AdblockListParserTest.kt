package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class AdblockListParserTest {

    private val list = AdblockListParser.parse(
        """
        [Adblock Plus 2.0]
        ! Title: Test
        ! Version: 202611081956
        ||tracker.example.com^
        ||Important.example.net^${'$'}important
        ||site.example.org^${'$'}third-party
        ||popup.example.org^${'$'}popup
        ||cancelled.example.com^
        ||cancelled.example.com^${'$'}badfilter
        @@||allowed.example.com^
        @@||cdn.example.com^${'$'}script
        example.com##.banner
        example.com#@#.banner
        example.com#${'$'}#body { overflow: auto }
        example.com#%#//scriptlet('abort')
        /pixel.gif?
        ||example.com/track/*
        0.0.0.0 hosts.example.com
        127.0.0.1 localhost
        # commentaire hosts
        bare.example.io
        ||not_a_domain^
        """.trimIndent(),
    )

    @Test
    fun `retient les regles de domaine entier`() {
        assertEquals(
            setOf("tracker.example.com", "important.example.net", "hosts.example.com", "bare.example.io"),
            list.domains,
        )
    }

    @Test
    fun `badfilter annule la regle qu il designe`() {
        assertFalse("cancelled.example.com" in list.domains)
    }

    @Test
    fun `third-party et les autres modificateurs sont ecartes et comptes`() {
        assertFalse("site.example.org" in list.domains)
        // $third-party, $popup, et l'exception $script
        assertEquals(3, list.ignoredModifiers)
    }

    @Test
    fun `regles cosmetiques ignorees et comptees`() {
        assertEquals(4, list.ignoredCosmetic)
    }

    @Test
    fun `chemins et regex comptes a part`() {
        // /pixel.gif?, ||example.com/track/*, ||not_a_domain^
        assertEquals(3, list.ignoredOther)
    }

    @Test
    fun `exceptions de domaine entier`() {
        assertEquals(setOf("allowed.example.com"), list.exceptions)
    }

    @Test
    fun `version lue, pas de somme de controle`() {
        assertEquals("202611081956", list.version)
        assertNull(list.checksumValid)
    }

    @Test
    fun `somme de controle AdGuard verifiee`() {
        val body = "[Adblock Plus 2.0]\n! Title: Test\n||a.example.com^\n\n||b.example.com^\n"
        val checksum = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("MD5").digest("[Adblock Plus 2.0]\n! Title: Test\n||a.example.com^\n||b.example.com^\n".toByteArray()),
        ).trimEnd('=')
        val signed = body.replaceFirst("! Title", "! Checksum: $checksum\n! Title")
        assertTrue(AdblockListParser.parse(signed).checksumValid == true)
        assertFalse(AdblockListParser.parse(signed.replace("b.example", "c.example")).checksumValid == true)
    }
}
