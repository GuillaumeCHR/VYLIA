package com.vylia.privacy.data.trackers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExodusTrackerParserTest {

    private val parser = ExodusTrackerParser()

    @Test
    fun `lit le format de l API Exodus et ignore les champs inconnus`() {
        val json = """
            {"trackers": {
              "27": {"id": 27, "name": "Google Firebase Analytics", "description": "…",
                     "creation_date": "2017-09-24", "code_signature": "com.google.firebase.analytics.",
                     "network_signature": "firebase.com", "website": "https://firebase.google.com/",
                     "categories": ["Analytics"], "documentation": []},
              "5": {"id": 5, "name": "Facebook Ads", "code_signature": "com.facebook.ads.|com.facebook.audiencenetwork.",
                    "website": "", "categories": ["Advertisement"]}
            }}
        """.trimIndent()

        val signatures = parser.parse(json)

        assertEquals(listOf(5, 27), signatures.map { it.tracker.id })
        val facebook = signatures.first()
        assertNull("site vide normalisé", facebook.tracker.website)
        assertTrue(facebook.codeSignature.containsMatchIn("com.facebook.audiencenetwork."))
        assertEquals(listOf("Analytics"), signatures.last().tracker.categories)
    }

    @Test
    fun `ignore les traqueurs sans signature ou a regex invalide`() {
        val json = """{"trackers": {
            "1": {"id": 1, "name": "Réseau seul", "code_signature": ""},
            "2": {"id": 2, "name": "Cassé", "code_signature": "com.foo.(" }
        }}"""
        assertTrue(parser.parse(json).isEmpty())
    }

    @Test
    fun `base vide embarquee par defaut`() {
        assertTrue(parser.parse("""{"trackers": {}}""").isEmpty())
    }
}
