package com.vylia.privacy.domain.scoring

import com.vylia.privacy.domain.scoring.TestFixtures.trackers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Badges (v2.1) et patterns de sécurité, évalués par le moteur de score sur les règles embarquées. */
class BadgeEngineTest {

    private val scorer = PrivacyScorer(TestFixtures.rules)

    private fun score(
        declared: Set<String> = emptySet(),
        granted: Set<String> = declared,
        trackers: List<Tracker>? = emptyList(),
        category: String = "CATEGORY_UNDEFINED",
        signals: Set<String> = emptySet(),
        packageName: String = "org.example",
    ) = scorer.score(AppScan(packageName, 35, category, declared, granted, trackers, signals))

    private fun ScoreResult.ids() = badges.map { it.id }.toSet()

    @Test
    fun `app sobre et sans traqueur - premier de la classe`() {
        val ids = score().ids()
        assertEquals(setOf("PERM_FEATHER", "TRK_ORGANIC", "COMBO_TOP_OF_CLASS"), ids)
    }

    @Test
    fun `permission interdite accordee - hors-la-loi remplace peche a la ligne`() {
        // Jeu : contacts accordés, SMS seulement demandés, tous deux interdits.
        val result = score(declared = setOf("READ_CONTACTS", "READ_SMS"), granted = setOf("READ_CONTACTS"), category = "CATEGORY_GAME")
        assertTrue("PERM_OUTLAW" in result.ids())
        assertFalse("PERM_FISHING" in result.ids())
    }

    @Test
    fun `permission interdite refusee - peche a la ligne seulement`() {
        val ids = score(declared = setOf("READ_SMS"), granted = emptySet(), category = "CATEGORY_GAME").ids()
        assertTrue("PERM_FISHING" in ids)
        assertFalse("PERM_OUTLAW" in ids)
    }

    @Test
    fun `trois traqueurs publicitaires - regie publicitaire remplace homme-sandwich`() {
        val ids = score(trackers = trackers("Google AdMob", "Facebook Ads", "Criteo")).ids()
        assertTrue("TRK_AD_NETWORK" in ids)
        assertFalse("TRK_SANDWICH" in ids)
    }

    @Test
    fun `traqueurs techniques uniquement - mecano`() {
        assertTrue("TRK_MECHANIC" in score(trackers = trackers("Google CrashLytics", "Didomi")).ids())
    }

    @Test
    fun `traqueurs non analyses - boite noire, aucun badge de traqueurs positif`() {
        val ids = score(trackers = null).ids()
        assertTrue("TRK_BLACK_BOX" in ids)
        assertFalse("TRK_ORGANIC" in ids)
        assertFalse("COMBO_TOP_OF_CLASS" in ids)
    }

    @Test
    fun `badges affiches - trois au plus, les plus graves d abord`() {
        val result = score(
            declared = setOf("READ_CONTACTS", "READ_SMS", "ACCESS_FINE_LOCATION"),
            category = "CATEGORY_GAME",
            trackers = trackers("Google AdMob", "Facebook Ads", "Criteo", "Pilgrim by Foursquare"),
        )
        assertTrue(result.displayedBadges.size <= 3)
        assertTrue(result.displayedBadges.all { it.severity == "danger" })
    }

    @Test
    fun `clavier tiers avec Internet - capacite keylogger, score plafonne a 29`() {
        val result = score(signals = setOf("INTERNET", "IME_ENABLED"))
        assertEquals(listOf("KEYLOGGER_CAPABLE"), result.securityFindings.map { it.id })
        assertEquals(29, result.securityScoreCap)
        assertTrue(result.score <= 29)
        assertTrue("SEC_SPY_PIANIST" in result.ids())
    }

    @Test
    fun `clavier Gboard exempte`() {
        val result = score(signals = setOf("INTERNET", "IME_ENABLED"), packageName = "com.google.android.inputmethod.latin")
        assertTrue(result.securityFindings.isEmpty())
        assertNull(result.securityScoreCap)
    }

    @Test
    fun `icone cachee et deux acces tres sensibles - profil de logiciel de surveillance`() {
        val result = score(
            declared = setOf("READ_SMS", "ACCESS_BACKGROUND_LOCATION"),
            signals = setOf("NO_LAUNCHER_ICON"),
        )
        assertTrue("STALKERWARE_PROFILE" in result.securityFindings.map { it.id })
        assertTrue("SEC_MOLOTOV" in result.ids())
    }

    @Test
    fun `base d indicateurs - stalkerware connu, score a 0`() {
        val withIoc = PrivacyScorer(TestFixtures.rules, IocDatabase(setOf("com.spy.app"), emptySet()))
        val result = withIoc.score(AppScan("com.spy.app", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList()))
        assertEquals(0, result.score)
        assertTrue("SEC_WANTED" in result.ids())
    }

    @Test
    fun `conditions - syntaxe et signaux inconnus refuses`() {
        val signals = setOf("INTERNET", "IME_ENABLED")
        val cond = ConditionParser("INTERNET & atleast(1: IME_ENABLED, granted:CAMERA)", signals).parse()
        assertTrue(cond.eval { it == "INTERNET" || it == "IME_ENABLED" })
        assertFalse(cond.eval { it == "INTERNET" })
        assertThrows(IllegalArgumentException::class.java) { ConditionParser("INTERNETT", signals).parse() }
        assertThrows(IllegalArgumentException::class.java) { ConditionParser("(INTERNET", signals).parse() }
    }
}
