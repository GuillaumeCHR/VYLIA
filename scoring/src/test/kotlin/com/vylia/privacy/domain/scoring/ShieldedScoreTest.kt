package com.vylia.privacy.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldedScoreTest {

    private val rules = ReferenceApps.rules
    private val scorer = PrivacyScorer(rules)

    private fun scan(trackers: List<Tracker>?) = AppScan(
        packageName = "com.example.app",
        targetSdk = 35,
        appInfoCategory = "CATEGORY_UNDEFINED",
        declaredPermissions = emptySet(),
        grantedPermissions = emptySet(),
        trackers = trackers,
    )

    private val ads = Tracker("Ads", setOf("Advertisement")) // 12
    private val stats = Tracker("Stats", setOf("Analytics")) // 5
    private val profile = Tracker("Profile", setOf("Profiling")) // 10

    @Test
    fun `le score de l'app ne change pas, seule l'exposition baisse`() {
        val result = scorer.score(scan(listOf(ads, stats, profile)))
        val coverage = mapOf("Ads" to ShieldCoverage.BLOCKED, "Stats" to ShieldCoverage.PARTIAL)
        val shielded = scorer.shielded(result) { coverage[it] ?: ShieldCoverage.NONE }!!
        val f = rules.scoring.shield
        val expectedPenalty = 12 * f.blockedFactor + 5 * f.partialFactor + 10
        assertEquals(expectedPenalty, shielded.trackerPenalty, 1e-9)
        assertEquals((100 - result.permissionPenalty - expectedPenalty).let { Math.round(it).toInt() }, shielded.score)
        assertEquals(73, result.score)
        assertTrue(shielded.score > result.score)
        assertEquals(1, shielded.blocked)
        assertEquals(1, shielded.partial)
        assertEquals(1, shielded.exposed)
    }

    @Test
    fun `rien de bloque, meme score`() {
        val result = scorer.score(scan(listOf(ads, stats)))
        assertEquals(result.score, scorer.shielded(result) { ShieldCoverage.NONE }!!.score)
    }

    @Test
    fun `plafond des traqueurs applique apres les facteurs`() {
        val many = (1..12).map { Tracker("Ads$it", setOf("Advertisement")) } // 144, plafonné à 60
        val result = scorer.score(scan(many))
        val shielded = scorer.shielded(result) { ShieldCoverage.PARTIAL }!!
        assertEquals(rules.scoring.trackerCap.toDouble(), shielded.trackerPenalty, 1e-9)
        assertEquals(result.score, shielded.score)
    }

    @Test
    fun `traqueurs non analyses, pas d'exposition calculable`() {
        assertNull(scorer.shielded(scorer.score(scan(null))) { ShieldCoverage.BLOCKED })
    }

    @Test
    fun `facteurs hors bornes refuses`() {
        val bad = rules.copy(scoring = rules.scoring.copy(shield = ShieldFactors(blockedFactor = 0.8, partialFactor = 0.5)))
        val error = runCatching { RulesValidator.validate(bad, rules) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("bouclier"))
    }
}
