package com.vylia.privacy.domain.scoring

import android.Manifest
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.ScoreRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyScorerTest {

    private val scorer = PrivacyScorer()

    @Test
    fun `app sans permission a risque ni traqueur obtient 100`() {
        assertEquals(100, scorer.calculateScore(listOf(Manifest.permission.INTERNET), trackerCount = 0))
    }

    @Test
    fun `permissions critiques`() {
        assertEquals(70, scorer.calculateScore(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0))
        assertEquals(70, scorer.calculateScore(listOf(Manifest.permission.BIND_ACCESSIBILITY_SERVICE), 0))
        assertEquals(75, scorer.calculateScore(listOf(Manifest.permission.CAMERA), 0))
        assertEquals(75, scorer.calculateScore(listOf(Manifest.permission.RECORD_AUDIO), 0))
        assertEquals(75, scorer.calculateScore(listOf(Manifest.permission.READ_SMS), 0))
    }

    @Test
    fun `variantes d une meme famille`() {
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS).forEach {
            assertEquals(it, 75, scorer.calculateScore(listOf(it), 0))
        }
        listOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS).forEach {
            assertEquals(it, 80, scorer.calculateScore(listOf(it), 0))
        }
        assertEquals(85, scorer.calculateScore(listOf(Manifest.permission.WRITE_CALENDAR), 0))
        assertEquals(90, scorer.calculateScore(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0))
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        ).forEach { assertEquals(it, 90, scorer.calculateScore(listOf(it), 0)) }
    }

    @Test
    fun `une famille ne penalise qu une fois au niveau de son membre le plus fort`() {
        val sms = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        assertEquals(75, scorer.calculateScore(sms, 0))
        // FINE implique toujours COARSE dans le manifeste depuis Android 12 : -20, pas -30.
        val location = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(80, scorer.calculateScore(location, 0))
        // L'arrière-plan reste une famille distincte et se cumule.
        assertEquals(50, scorer.calculateScore(location + Manifest.permission.ACCESS_BACKGROUND_LOCATION, 0))
    }

    @Test
    fun `representant de famille et ordre`() {
        val strongest = PermissionRegistry.strongestByFamily(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS),
        )
        assertEquals(listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_MEDIA_IMAGES), strongest.map { it.permission })
    }

    @Test
    fun `permissions de niveau eleve`() {
        assertEquals(80, scorer.calculateScore(listOf(Manifest.permission.ACCESS_FINE_LOCATION), 0))
        assertEquals(80, scorer.calculateScore(listOf(Manifest.permission.READ_CONTACTS), 0))
        assertEquals(85, scorer.calculateScore(listOf(Manifest.permission.READ_CALENDAR), 0))
    }

    @Test
    fun `paliers de traqueurs`() {
        val expected = mapOf(0 to 100, 1 to 90, 3 to 90, 4 to 80, 10 to 80, 11 to 65, 30 to 65, 31 to 50)
        expected.forEach { (trackers, score) ->
            assertEquals("$trackers traqueurs", score, scorer.calculateScore(emptyList(), trackers))
        }
    }

    @Test
    fun `penalites cumulees et score cape a zero`() {
        val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        assertEquals(30, scorer.calculateScore(permissions, trackerCount = 4))
        // Toutes les familles : 30 + 30 + 25 + 25 + 25 + 20 + 20 + 15 + 10 = 200.
        assertEquals(0, scorer.calculateScore(PermissionRegistry.risks.keys.toList(), trackerCount = 0))
    }

    @Test
    fun `seuils de couleur vert orange rouge`() {
        assertEquals(ScoreRating.GOOD, PrivacyScore(80).rating)
        assertEquals(ScoreRating.FAIR, PrivacyScore(79).rating)
        assertEquals(ScoreRating.FAIR, PrivacyScore(50).rating)
        assertEquals(ScoreRating.POOR, PrivacyScore(49).rating)
    }

    @Test
    fun `moyenne arrondie et liste vide`() {
        assertEquals(83, PrivacyScore.average(listOf(PrivacyScore(100), PrivacyScore(75), PrivacyScore(75)))?.value)
        assertNull(PrivacyScore.average(emptyList()))
    }
}
