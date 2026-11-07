package com.vylia.privacy.domain.web

import com.vylia.privacy.domain.web.SiteChooser.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteChooserTest {

    private fun verified(vararg names: String) = names.map { Host(it, verified = true) }
    private fun declared(vararg names: String) = names.map { Host(it, verified = false) }

    @Test
    fun `prefers the device country among same-name domains`() {
        val hosts = verified("doctolib.eu.go.link", "www.doctolib.de", "www.doctolib.fr", "www.doctolib.it") + declared("a.doctolib.fr")
        assertEquals("https://www.doctolib.fr/", SiteChooser.choose("fr.doctolib.www", "Doctolib", hosts, "FR"))
    }

    @Test
    fun `drops technical hosts`() {
        val hosts = verified("api-project-1020531999467.firebaseapp.com", "www.shazam.com")
        assertEquals("https://www.shazam.com/", SiteChooser.choose("com.shazam.android", "Shazam", hosts, "FR"))
    }

    @Test
    fun `picks a verified subdomain when there is no www`() {
        val hosts = verified("connecttest.garmin.com", "connect.garmin.com", "connectus.garmin.cn") + declared("apps-test.garmin.com", "apps.garmin.com")
        assertEquals("https://connect.garmin.com/", SiteChooser.choose("com.garmin.android.apps.connectmobile", "Garmin Connect", hosts, "FR"))
    }

    @Test
    fun `matches the label when the package says nothing`() {
        assertEquals("https://www.france.tv/", SiteChooser.choose("fr.francetv.pluzz", "france.tv", verified("www.france.tv"), "FR"))
    }

    @Test
    fun `ignores unverified domains that do not look like the app`() {
        assertNull(SiteChooser.choose("com.example.reader", "Reader", declared("twitter.com", "www.reddit.com"), "FR"))
    }

    @Test
    fun `keeps unverified domains named like the app`() {
        assertEquals("https://telegram.org/", SiteChooser.choose("org.telegram.messenger", "Telegram", declared("telegram.dog", "t.me", "telegram.org"), "FR"))
    }

    @Test
    fun `prefers the domain with a home page`() {
        val hosts = verified(
            "platform.openai.com", "chatgpt.com", "www.chatgpt.com", "click.openai.com", "u.email.openai.com", "link.openai.com",
            "chat.openai.com", "www.chat.com", "chat.com", "r.openai.com", "s.openai.com",
        ) + declared("chatgpt-staging.com", "app.chatgpt.com", "tt.chatgpt.com")
        assertEquals("https://www.chatgpt.com/", SiteChooser.choose("com.openai.chatgpt", "ChatGPT", hosts, "FR"))
    }

    @Test
    fun `registrable domain handles two-level suffixes`() {
        assertEquals("bbc.co.uk", SiteChooser.registrable("www.bbc.co.uk"))
        assertEquals("doctolib.fr", SiteChooser.registrable("a.doctolib.fr"))
    }

    @Test
    fun `no hosts gives no site`() {
        assertNull(SiteChooser.choose("com.android.chrome", "Chrome", emptyList(), "FR"))
    }
}
