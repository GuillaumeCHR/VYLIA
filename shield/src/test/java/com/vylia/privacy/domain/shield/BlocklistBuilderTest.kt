package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlocklistBuilderTest {

    private val embedded = Blocklist.snapshot(
        """{"version":2,"groups":{
           "mobile":{"trackers":{"Google AdMob":["admob.example.com"]},"domains":["old-mobile.example.com"]},
           "web":{"trackers":{"Firebase Analytics":["app-measurement.example.com"]},"domains":["old-web.example.com"]},
           "spyware":{"trackers":{"Pegasus":["evil.example.net"]}}}}""",
    )

    private fun parsed(vararg lines: String) = AdblockListParser.parse(lines.asSequence())

    private val web = BlocklistBuilder.Source(
        ShieldFilter.WEB,
        parsed("||tracker.example.com^", "||sub.tracker.example.com^", "||allowed.example.org^", "||google.com^", "||admob.example.com^"),
    )
    private val mobile = BlocklistBuilder.Source(ShieldFilter.MOBILE, parsed("||ads.example.net^", "@@||allowed.example.org^"))

    @Test
    fun `fusionne les listes et garde les noms des traqueurs`() {
        val result = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false)
        val list = result.blocklist
        assertEquals(BlockMatch("tracker.example.com", ShieldFilter.WEB, null), list.match("x.tracker.example.com"))
        // Liste de rôle mobile : pas de filtrage mobile dans cette version, ses domaines ne sont pas bloqués.
        assertNull(list.match("ads.example.net"))
        assertEquals("Firebase Analytics", list.match("app-measurement.example.com")?.trackerName)
        assertEquals(BlockMatch("evil.example.net", null, "Pegasus"), list.match("evil.example.net"))
    }

    @Test
    fun `dedoublonne les sous-domaines`() {
        val result = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false)
        // web : tracker.example.com et app-measurement (sub.tracker retiré, admob est mobile) ; mobile : vide.
        assertEquals(2, result.web)
        assertEquals(0, result.mobile)
    }

    @Test
    fun `une exception d une liste s applique aux autres et NEVER_BLOCK a toutes`() {
        val list = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false).blocklist
        assertNull(list.match("allowed.example.org"))
        assertNull(list.match("www.google.com"))
    }

    @Test
    fun `une regie nommee n est pas bloquee meme citee par une liste web`() {
        val list = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false).blocklist
        // admob est nommé mobile dans la liste embarquée et cité par une liste web : il passe, ses sous-domaines aussi.
        assertNull(list.match("admob.example.com"))
        assertNull(list.match("x.admob.example.com"))
    }

    @Test
    fun `AdMob et DoubleClick ne sont jamais bloques`() {
        val ads = BlocklistBuilder.Source(ShieldFilter.WEB, parsed("||doubleclick.net^", "||pagead2.googlesyndication.com^", "||tracker.example.com^"))
        val list = BlocklistBuilder.build(embedded, listOf(ads), fallback = false).blocklist
        assertNull(list.match("googleads.g.doubleclick.net"))
        assertNull(list.match("pagead2.googlesyndication.com"))
        assertEquals(ShieldFilter.WEB, list.match("tracker.example.com")?.filter)
    }

    @Test
    fun `la liste embarquee ne complete que si une liste manque`() {
        val without = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false).blocklist
        assertNull(without.match("old-web.example.com"))
        val with = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = true).blocklist
        assertEquals(ShieldFilter.WEB, with.match("old-web.example.com")?.filter)
        assertNull(with.match("old-mobile.example.com"))
    }

    @Test
    fun `une exception protege aussi un sous-domaine d un domaine liste`() {
        val list = BlocklistBuilder.build(
            embedded,
            listOf(BlocklistBuilder.Source(ShieldFilter.WEB, parsed("||example.cn^", "@@||ad.example.cn^|", "@@|exact.example.cn^|"))),
            fallback = false,
        ).blocklist
        assertNull(list.match("ad.example.cn"))
        assertNull(list.match("x.ad.example.cn"))
        assertNull(list.match("exact.example.cn"))
        assertEquals(ShieldFilter.WEB, list.match("other.example.cn")?.filter)
        // Les logiciels espions restent bloqués.
        assertEquals("Pegasus", list.match("evil.example.net")?.trackerName)
    }
}
