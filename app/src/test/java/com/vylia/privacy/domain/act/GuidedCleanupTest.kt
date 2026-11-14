package com.vylia.privacy.domain.act

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.TestFixtures
import com.vylia.privacy.domain.usecase.scoreApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedCleanupTest {

    private val scorer = PrivacyScorer(TestFixtures.rules)

    private fun app(pkg: String, granted: Set<String> = emptySet(), trackers: Int = 0): AuditedApp = scorer.scoreApp(
        InstalledApp(
            label = pkg.substringAfterLast('.'),
            packageName = pkg,
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
            appInfoCategory = "CATEGORY_GAME",
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

    private fun plan(apps: List<AuditedApp>, removals: List<AuditedApp> = emptyList(), shielded: Map<String, Int>? = null) =
        GuidedCleanup.plan(
            apps,
            ReductionAdvisor.byApp(ReductionAdvisor.revocations(apps, scorer), scorer),
            removals.map { RemovalSuggestion(it, lastUsedAt = null) },
            shielded,
        )

    private val clean = List(4) { app("org.example.clean$it") }

    @Test
    fun `rien a gagner - aucune etape`() {
        assertTrue(plan(clean).isEmpty())
    }

    @Test
    fun `trois etapes au plus, la plus rentable d abord, chacune evaluee apres la precedente`() {
        val apps = clean + listOf(
            app("org.example.a", setOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION")),
            app("org.example.b", setOf("READ_CONTACTS")),
            app("org.example.c", setOf("RECORD_AUDIO")),
            app("org.example.d", setOf("READ_CALENDAR")),
        )
        val steps = plan(apps)
        assertEquals(GuidedCleanup.MAX_STEPS, steps.size)
        steps.zipWithNext().forEach { (first, second) ->
            assertEquals(first.deviceAfter, second.deviceBefore, 1e-9)
            assertTrue(first.deviceGain >= second.deviceGain)
        }
        assertEquals(apps.map { it.score.value }.average(), steps.first().deviceBefore, 1e-9)
    }

    @Test
    fun `une app ne revient pas deux fois - retrait ou suppression, pas les deux`() {
        val spy = app("org.example.spy", setOf("READ_CONTACTS", "RECORD_AUDIO"), trackers = 3)
        val steps = plan(clean + spy, removals = listOf(spy))
        assertEquals(1, steps.size)
        // La suppression rapporte plus que le retrait : l'app part, sa note ne compte plus.
        assertTrue(steps.single().action is CleanupAction.Remove)
    }

    @Test
    fun `supprimer une app mieux notee que la moyenne n est jamais propose`() {
        val bad = app("org.example.bad", trackers = 3)
        val good = clean.first()
        val steps = plan(listOf(bad, good), removals = listOf(good))
        assertFalse(steps.any { it.action is CleanupAction.Remove })
    }

    @Test
    fun `bouclier - propose s il fait monter les apps, pas s il est deja actif`() {
        val tracked = List(3) { app("org.example.tracked$it", trackers = 3) }
        val apps = clean + tracked
        val shielded = apps.associate { it.app.packageName to (it.score.value + 20).coerceAtMost(100) }
        val step = plan(apps, shielded = shielded).single()
        assertEquals(CleanupAction.Shield, step.action)
        assertEquals(null, step.appBefore)
        assertTrue(step.deviceGain > 0)
        assertTrue(plan(apps, shielded = null).isEmpty())
    }
}
