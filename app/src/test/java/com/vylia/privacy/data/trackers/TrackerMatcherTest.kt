package com.vylia.privacy.data.trackers

import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerSignature
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerMatcherTest {

    private fun signature(id: Int, name: String, pattern: String) =
        TrackerSignature(Tracker(id, name, null, emptyList()), Regex(pattern))

    private fun TrackerMatcher.find(vararg classNames: String): Set<String> =
        newScan().apply { classNames.forEach(::accept) }.result().mapTo(HashSet()) { it.name }

    private val matcher = TrackerMatcher(
        listOf(
            signature(1, "Facebook Ads", "com.facebook.ads."),
            signature(2, "Demdex", "com.adobe.mobile.Analytics|com.adobe.mobile.Config."),
            signature(3, "Sentry", "io.sentry.|com.joshdholtz.sentry"),
            signature(4, "Regex", "com\\.example\\.(ads|track)\\."),
        )
    )

    @Test
    fun `detecte les signatures de paquet et de classe`() {
        assertEquals(setOf("Facebook Ads", "Demdex"), matcher.find("com.facebook.ads.AdView", "com.adobe.mobile.Analytics"))
    }

    @Test
    fun `classe interne equivalente au point des regex Exodus`() {
        assertEquals(setOf("Demdex"), matcher.find("com.adobe.mobile.Config\$Builder"))
    }

    @Test
    fun `recherche n importe ou dans le nom et alternatives regulieres`() {
        assertEquals(setOf("Sentry", "Regex"), matcher.find("shadow.com.joshdholtz.sentry.Client", "com.example.track.Hit"))
    }

    @Test
    fun `aucun faux positif sur un prefixe partiel`() {
        assertTrue(matcher.find("com.facebook.login.Login", "com.adobe.mobile.Media", "io.sentryx.Foo").isEmpty())
    }

    @Test
    fun `retrouve les traqueurs par identifiant pour le cache`() {
        assertEquals(listOf("Sentry", "Facebook Ads"), matcher.trackersById(listOf(3, 1, 99)).map { it.name })
    }

    @Test
    fun `snapshot embarque - toutes les signatures sont prises en compte`() {
        val signatures = ExodusTrackerParser().parse(File("src/main/assets/exodus_trackers.json").readText())
        val real = TrackerMatcher(signatures)
        assertEquals(
            setOf("Google AdMob", "Sentry", "Google Firebase Analytics"),
            real.find("com.google.android.gms.ads.AdView", "io.sentry.Sentry", "com.google.firebase.analytics.FirebaseAnalytics"),
        )
    }

    @Test
    fun `une correction de categories garde le cache d analyse et donne les nouvelles categories`() {
        val before = TrackerMatcher(listOf(TrackerSignature(Tracker(7, "AdLib", null, emptyList()), Regex("com.adlib."))))
        val after = TrackerMatcher(listOf(TrackerSignature(Tracker(7, "AdLib", null, listOf("Advertisement")), Regex("com.adlib."))))
        assertEquals(before.version, after.version)
        assertEquals(listOf("Advertisement"), after.trackersById(listOf(7)).single().categories)
        val changed = TrackerMatcher(listOf(TrackerSignature(Tracker(7, "AdLib", null, emptyList()), Regex("com.adlib.sdk."))))
        assertTrue(before.version != changed.version)
    }
}
