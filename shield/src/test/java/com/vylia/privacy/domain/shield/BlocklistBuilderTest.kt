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
        assertEquals(BlockMatch("ads.example.net", ShieldFilter.MOBILE, null), list.match("ads.example.net"))
        assertEquals("Firebase Analytics", list.match("app-measurement.example.com")?.trackerName)
        assertEquals(BlockMatch("evil.example.net", null, "Pegasus"), list.match("evil.example.net"))
    }

    @Test
    fun `dedoublonne les sous-domaines`() {
        val result = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false)
        // web : tracker.example.com et app-measurement (sub.tracker retiré, admob est mobile) ; mobile : admob, ads.
        assertEquals(2, result.web)
        assertEquals(2, result.mobile)
    }

    @Test
    fun `une exception d une liste s applique aux autres et NEVER_BLOCK a toutes`() {
        val list = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false).blocklist
        assertNull(list.match("allowed.example.org"))
        assertNull(list.match("www.google.com"))
    }

    @Test
    fun `le groupe mobile l emporte`() {
        val list = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false).blocklist
        // admob est nommé mobile dans la liste embarquée et cité par une liste web : filtrage mobile coupé, il passe.
        assertNull(list.match("admob.example.com", setOf(ShieldFilter.WEB)))
        assertEquals(ShieldFilter.MOBILE, list.match("admob.example.com")?.filter)
    }

    @Test
    fun `la liste embarquee ne complete que si une liste manque`() {
        val without = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = false).blocklist
        assertNull(without.match("old-web.example.com"))
        val with = BlocklistBuilder.build(embedded, listOf(web, mobile), fallback = true).blocklist
        assertEquals(ShieldFilter.WEB, with.match("old-web.example.com")?.filter)
        assertEquals(ShieldFilter.MOBILE, with.match("old-mobile.example.com")?.filter)
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
