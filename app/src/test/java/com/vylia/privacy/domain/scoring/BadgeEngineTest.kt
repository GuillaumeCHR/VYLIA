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
        assertEquals(setOf("PERM_FEATHER", "PERM_DORMANT", "TRK_ORGANIC", "COMBO_TOP_OF_CLASS"), ids)
    }

    @Test
    fun `permission interdite accordee - sortie de route remplace peche a la ligne`() {
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
        // Sans pénalité, le score brut dépasse le plafond : la carte compacte doit l'annoncer.
        assertTrue(result.uncappedScore > 29)
        assertTrue(result.isCapped)
    }

    @Test
    fun `clavier Gboard exempte`() {
        val result = score(signals = setOf("INTERNET", "IME_ENABLED"), packageName = "com.google.android.inputmethod.latin")
        assertTrue(result.securityFindings.isEmpty())
        assertNull(result.securityScoreCap)
        assertFalse(result.isCapped)
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
        val withIoc = PrivacyScorer(TestFixtures.rules, IocDatabase(mapOf("com.spy.app" to "SpyApp"), emptyMap()))
        val result = withIoc.score(AppScan("com.spy.app", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList()))
        assertEquals(0, result.score)
        assertTrue("SEC_WANTED" in result.ids())
        assertEquals("SpyApp", result.knownStalkerware)
    }

    @Test
    fun `app systeme - ses capacites ne sont pas une alerte, une app connue des indicateurs l est toujours`() {
        val keyboard = AppScan("com.vendor.keyboard", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList(), setOf("INTERNET", "IME_ENABLED"))
        assertEquals(listOf("KEYLOGGER_CAPABLE"), scorer.score(keyboard).securityFindings.map { it.id })
        val system = scorer.score(keyboard.copy(isSystemApp = true))
        assertTrue(system.securityFindings.isEmpty())
        assertNull(system.securityScoreCap)
        val hidden = AppScan(
            "com.vendor.agent", 35, "CATEGORY_UNDEFINED",
            setOf("READ_SMS", "ACCESS_BACKGROUND_LOCATION"), setOf("READ_SMS", "ACCESS_BACKGROUND_LOCATION"), emptyList(),
            setOf("NO_LAUNCHER_ICON"), isSystemApp = true,
        )
        assertTrue(scorer.score(hidden).securityFindings.isEmpty())
        // Un logiciel espion connu reste signalé, même installé comme app système.
        val withIoc = PrivacyScorer(TestFixtures.rules, IocDatabase(mapOf("com.spy.app" to "SpyApp"), emptyMap()))
        val spy = withIoc.score(AppScan("com.spy.app", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList(), isSystemApp = true))
        assertEquals(listOf("KNOWN_STALKERWARE"), spy.securityFindings.map { it.id })
        assertEquals(0, spy.score)
    }

    @Test
    fun `base d Echap embarquee - reconnait un stalkerware renomme par son certificat`() {
        val loaded = com.vylia.privacy.domain.security.StalkerwareDatabase.parse(
            java.io.File("src/main/assets/stalkerware_iocs.json").readText()
        )
        assertTrue(loaded.apps > 100)
        assertTrue(loaded.commit.length == 40)
        val scorer = PrivacyScorer(TestFixtures.rules, loaded.iocs)
        // TheTruthSpy, sous un nom de paquet inventé : seule l'empreinte SHA-1 de son certificat le trahit.
        val renamed = AppScan("org.example.innocent", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList())
            .copy(signingCertDigests = setOf("31:a6:ec:ec:d9:7c:f3:9b:c4:12:6b:87:45:cd:94:a7:c3:0b:f8:1c"))
        val result = scorer.score(renamed)
        assertEquals("TheTruthSpy", result.knownStalkerware)
        assertEquals(0, result.score)
        assertTrue("SEC_WANTED" in result.ids())
        // Une app ordinaire n'y est pas.
        assertNull(scorer.score(AppScan("org.mozilla.firefox", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList())).knownStalkerware)
    }

    @Test
    fun `v3 - permissions publicitaires neutralisees - ni penalite ni badge`() {
        val result = score(declared = setOf("ACCESS_ADSERVICES_TOPICS", "com.google.android.gms.permission.AD_ID"))
        assertEquals(0.0, result.permissionPenalty, 0.001)
        assertTrue("PERM_FEATHER" in result.ids())
        assertTrue("com.google.android.gms.permission.AD_ID" in TestFixtures.rules.neutralPermissions)
    }

    @Test
    fun `v3 - capture d ecran - accord a chaque usage, comptee comme seulement declaree`() {
        val result = scorer.score(
            AppScan("com.paypal.android.p2pmobile", 35, "CATEGORY_UNDEFINED", setOf("FOREGROUND_SERVICE_MEDIA_PROJECTION"),
                setOf("FOREGROUND_SERVICE_MEDIA_PROJECTION"), emptyList()),
        )
        assertEquals("FINANCE_ACCOUNTS", result.categoryId)
        assertEquals(4 * 0.25, result.permissionPenalty, 0.001)
        assertFalse("PERM_ROLLING" in result.ids())
    }

    @Test
    fun `v3 - micro en arriere-plan - sans acces au micro seulement declare, avec moteur ca tourne`() {
        val fgs = "FOREGROUND_SERVICE_MICROPHONE"
        val withoutMic = score(declared = setOf(fgs), category = "CATEGORY_GAME")
        assertEquals(6 * 1.6 * 0.25, withoutMic.permissionPenalty, 0.001)
        assertFalse("PERM_ROLLING" in withoutMic.ids())
        val withMic = score(declared = setOf(fgs, "RECORD_AUDIO"), category = "CATEGORY_GAME")
        assertTrue("PERM_ROLLING" in withMic.ids())
    }

    @Test
    fun `v3 - micro en arriere-plan pour une messagerie - justifie, pas de badge`() {
        val result = scorer.score(
            AppScan("org.thoughtcrime.securesms", 35, "CATEGORY_UNDEFINED", setOf("FOREGROUND_SERVICE_MICROPHONE"),
                setOf("FOREGROUND_SERVICE_MICROPHONE"), emptyList()),
        )
        assertEquals(0.0, result.permissionPenalty, 0.001)
        assertFalse("PERM_ROLLING" in result.ids())
        assertFalse("PERM_DORMANT" in result.ids())
    }

    @Test
    fun `v3 - deux moyens injustifies de tourner en arriere-plan - insomniaque`() {
        val ids = score(
            declared = setOf("FOREGROUND_SERVICE_LOCATION", "ACCESS_COARSE_LOCATION", "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"),
            category = "CATEGORY_GAME",
        ).ids()
        assertTrue("PERM_NIGHT_OWL" in ids)
    }

    @Test
    fun `v3 - Health Connect - le palier le plus sensible compte seul`() {
        // Jeu : données intimes (8) et activité (2), toutes deux interdites (×1,6) → seule l'intime compte.
        val result = score(declared = setOf("health.READ_MENSTRUATION", "health.READ_STEPS"), category = "CATEGORY_GAME")
        assertEquals(8 * 1.6, result.permissionPenalty, 0.001)
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
