package com.vylia.privacy.domain.act

import android.graphics.drawable.Drawable
import com.vylia.privacy.data.alternatives.AlternativesRepository
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.TestFixtures
import com.vylia.privacy.domain.usecase.scoreApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReductionAdvisorTest {

    private val scorer = PrivacyScorer(TestFixtures.rules)
    private val day = 24L * 60 * 60 * 1000

    private fun app(pkg: String, category: String, granted: Set<String>, trackers: Int = 0): AuditedApp = scorer.scoreApp(
        InstalledApp(
            label = pkg.substringAfterLast('.'),
            packageName = pkg,
            // Jamais lu par le calcul : un stub Android suffit.
            icon = object : Drawable() {
                override fun draw(canvas: android.graphics.Canvas) = Unit
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
                @Deprecated("Deprecated in Java")
                override fun getOpacity() = 0
            },
            isSystemApp = false,
            category = null,
            apkPaths = emptyList(),
            lastUpdateTime = 0L,
            targetSdk = 35,
            appInfoCategory = category,
            declaredPermissions = granted,
            grantedPermissions = granted,
            requestedPermissions = emptyList(),
            permissions = emptyList(),
        ),
        TrackerAnalysis.Analyzed(
            List(trackers) { i ->
                val name = listOf("Google AdMob", "Criteo", "Facebook Ads")[i % 3]
                Tracker(i, name, null, TestFixtures.exodusCategories.getValue(name).toList())
            },
        ),
    )

    @Test
    fun `retirer la position d un jeu - gain exact, position precise et approximative ensemble`() {
        val game = app("org.example.game", "CATEGORY_GAME", setOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"))
        val suggestion = ReductionAdvisor.revocations(listOf(game), scorer).single()
        assertEquals(setOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"), suggestion.revoked)
        // Retirée mais toujours déclarée dans le manifeste : elle compte encore ×0,25 (8 × 1,6 × 0,25 = 3,2).
        assertEquals(97, suggestion.newScore)
        assertEquals(97 - game.score.value, suggestion.gain)
        assertTrue(suggestion.badgesLost.any { it.id == "PERM_OUTLAW" })
    }

    @Test
    fun `retraits - les apps au score le plus bas d abord`() {
        val light = app("org.example.light", "CATEGORY_GAME", setOf("ACCESS_FINE_LOCATION"))
        val heavy = app("org.example.heavy", "CATEGORY_GAME", setOf("ACCESS_FINE_LOCATION"), trackers = 3)
        assertTrue(heavy.score.value < light.score.value)
        val order = ReductionAdvisor.revocations(listOf(light, heavy), scorer).map { it.app.app.packageName }.distinct()
        assertEquals(listOf("org.example.heavy", "org.example.light"), order)
    }

    @Test
    fun `une carte par app - toutes ses permissions, score recalcule avec tout retire`() {
        val game = app("org.example.game", "CATEGORY_GAME", setOf("ACCESS_FINE_LOCATION", "READ_CONTACTS", "RECORD_AUDIO"))
        val other = app("org.example.other", "CATEGORY_GAME", setOf("READ_CONTACTS"), trackers = 3)
        val revocations = ReductionAdvisor.revocations(listOf(game, other), scorer)
        val cards = ReductionAdvisor.byApp(revocations, scorer)
        assertEquals(listOf("org.example.other", "org.example.game"), cards.map { it.app.app.packageName })
        val gameCard = cards.single { it.app.app.packageName == "org.example.game" }
        assertEquals(revocations.count { it.app.app.packageName == "org.example.game" }, gameCard.suggestions.size)
        assertTrue(gameCard.suggestions.size >= 2)
        // Le moteur recalcule tout retiré à la fois : au moins le meilleur gain seul, au plus le score de base.
        assertTrue(gameCard.gain >= gameCard.suggestions.maxOf { it.gain })
        assertTrue(gameCard.newScore <= 100)
        assertEquals(gameCard.suggestions.sortedByDescending { it.gain }, gameCard.suggestions)
    }

    @Test
    fun `une permission justifiee n est jamais proposee`() {
        val maps = app("org.example.maps", "CATEGORY_MAPS", setOf("ACCESS_FINE_LOCATION"))
        assertTrue(ReductionAdvisor.revocations(listOf(maps), scorer).isEmpty())
    }

    @Test
    fun `suppressions - intrusives toujours, inutilisees sous 70, les plus delaissees d abord`() {
        val now = 400 * day
        val intrusive = app("org.example.ads", "CATEGORY_GAME", emptySet(), trackers = 6)
        val nosy = setOf("READ_CONTACTS", "ACCESS_FINE_LOCATION", "READ_CALENDAR")
        val stale = app("org.example.stale", "CATEGORY_GAME", nosy)
        val recent = app("org.example.recent", "CATEGORY_GAME", nosy)
        val clean = app("org.example.clean", "CATEGORY_GAME", emptySet())
        assertTrue(intrusive.score.value < 50 && stale.score.value in 50..69)
        val lastUsed = mapOf("org.example.ads" to now - day, "org.example.recent" to now - day, "org.example.clean" to now - 300 * day)
        val result = ReductionAdvisor.removals(listOf(intrusive, stale, recent, clean), lastUsed, now)
        assertEquals(listOf("org.example.stale", "org.example.ads"), result.map { it.app.app.packageName })
        assertEquals(100 - intrusive.score.value, result.last().points)
        // Sans accès aux données d'utilisation : seules les intrusives.
        assertEquals(listOf("org.example.ads"), ReductionAdvisor.removals(listOf(intrusive, stale, clean), null, now).map { it.app.app.packageName })
    }

    @Test
    fun `score projete - recalcule sans les retraits et sans les apps supprimees`() {
        val game = app("org.example.game", "CATEGORY_GAME", setOf("ACCESS_FINE_LOCATION"))
        val clean = app("org.example.clean", "CATEGORY_GAME", emptySet())
        val revocations = ReductionAdvisor.revocations(listOf(game, clean), scorer)
        // Jeu : 97 une fois la position retirée (encore déclarée) ; moyenne avec l'app propre : 98,5 → 99.
        assertEquals(99, ReductionAdvisor.projectedAverage(listOf(game, clean), revocations, emptyList(), scorer)?.value)
    }

    @Test
    fun `catalogue d alternatives - lisible, paquets uniques par famille, textes en deux langues`() {
        val catalogue = AlternativesRepository.parse(File("src/main/assets/alternatives.json").readText())
        catalogue.groups.forEach { group ->
            assertEquals(group.id, group.alternatives.size, group.alternatives.map { it.packageName }.toSet().size)
            assertTrue(group.id, "en" in group.title && "fr" in group.title)
            assertTrue(group.id, group.alternatives.size in 2..3)
            group.alternatives.forEach { alt ->
                assertTrue(alt.name, "en" in alt.pitch && "fr" in alt.pitch)
                assertTrue(alt.name, alt.fdroid != null || alt.play != null || alt.web != null)
                assertEquals(alt.name, "Free", alt.cost)
            }
        }
        val categories = TestFixtures.rules.categories.keys
        catalogue.groups.flatMap { it.categories }.forEach { assertTrue("Catégorie inconnue : $it", it in categories) }
        assertNotNull(catalogue.groupFor(app("com.whatsapp", "CATEGORY_UNDEFINED", emptySet())))
    }
}
