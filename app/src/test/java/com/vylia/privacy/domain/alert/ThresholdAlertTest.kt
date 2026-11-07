package com.vylia.privacy.domain.alert

import android.graphics.drawable.Drawable
import com.vylia.privacy.data.alert.StalkerwareAlerts
import com.vylia.privacy.domain.history.AppPoint
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.ScoreSnapshot
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.TestFixtures
import com.vylia.privacy.domain.usecase.historyPoint
import com.vylia.privacy.domain.usecase.scoreApp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdAlertTest {

    private val on = ThresholdSetting(enabled = true, threshold = 70)

    /** Enchaîne des scans et compte les notifications. */
    private fun run(setting: ThresholdSetting, start: AlertState?, vararg scores: Int): Pair<AlertState?, Int> {
        var state = start
        var notified = 0
        scores.forEach { score ->
            val decision = ThresholdAlert.onScan(setting, state, score)
            if (decision.notify) notified++
            if (decision.state != null) state = decision.state
        }
        return state to notified
    }

    // --- Logique de déclenchement ---------------------------------------------------------------

    @Test
    fun `franchissement vers le bas - une seule notification`() {
        val (state, notified) = run(on, AlertState.ARMED, 74, 69)
        assertEquals(AlertState.TRIGGERED, state)
        assertEquals(1, notified)
    }

    @Test
    fun `plusieurs scans sous le seuil - pas de nouvelle notification`() {
        assertEquals(1, run(on, AlertState.ARMED, 69, 65, 60, 69).second)
    }

    @Test
    fun `remontee au-dessus du seuil puis nouvelle baisse - nouvelle notification`() {
        val (state, notified) = run(on, AlertState.ARMED, 69, 71, 68)
        assertEquals(AlertState.TRIGGERED, state)
        assertEquals(2, notified)
    }

    @Test
    fun `remontee au seuil exact - pas de rearmement, marge d un point`() {
        // 69 → 70 → 69 : un va-et-vient d'un point autour du seuil ne renvoie pas l'alerte.
        assertEquals(1, run(on, AlertState.ARMED, 69, 70, 69).second)
        assertEquals(ThresholdAlert.REARM_MARGIN, 1)
    }

    @Test
    fun `premier scan sous le seuil - aucune notification`() {
        val (state, notified) = run(on, null, 55)
        assertEquals(AlertState.TRIGGERED, state)
        assertEquals(0, notified)
    }

    @Test
    fun `changement de seuil au-dessus du score actuel - etat TRIGGERED en silence`() {
        // Le réglage passe à 80 alors que le score est 74 : l'état est recalculé sans notification…
        val raised = on.copy(threshold = 80)
        assertEquals(AlertState.TRIGGERED, ThresholdAlert.settle(raised, 74))
        // …et le scan suivant au même score ne notifie pas.
        assertEquals(0, run(raised, ThresholdAlert.settle(raised, 74), 74).second)
    }

    @Test
    fun `alerte desactivee - aucun calcul d etat`() {
        val off = on.copy(enabled = false)
        val decision = ThresholdAlert.onScan(off, AlertState.ARMED, 10)
        assertNull(decision.state)
        assertFalse(decision.notify)
        assertNull(ThresholdAlert.settle(off, 10))
    }

    @Test
    fun `historique des franchissements - 50 au plus, les plus recents`() {
        var list = emptyList<ThresholdCrossing>()
        repeat(60) { i -> list = ThresholdAlert.append(list, ThresholdCrossing(i.toLong(), 60, 70, DropCause(DropCause.Type.SPREAD))) }
        assertEquals(50, list.size)
        assertEquals(10L, list.first().at)
    }

    // --- Score de l'appareil : un seul arrondi ---------------------------------------------------

    @Test
    fun `score de l appareil - meme arrondi partout, 62,5 donne 63`() {
        assertEquals(63, PrivacyScore.ofAverage(62.5).value)
        assertEquals(PrivacyScore.average(listOf(PrivacyScore(62), PrivacyScore(63)))?.value, ScoreSnapshot(0, 62.5, 0.0, emptyMap()).score)
        // L'évolution sur 30 jours suit le même arrondi : 61,5 → 62,5 compte +1 (62 → 63), pas 0.
        val history = History(listOf(ScoreSnapshot(0, 61.5, 0.0, emptyMap()), ScoreSnapshot(History.PROGRESS_PERIOD_MS, 62.5, 0.0, emptyMap())))
        assertEquals(1, history.progress()?.points)
    }

    @Test
    fun `ancien historique sans traqueurs ni motifs - toujours lisible`() {
        val old = """{"snapshots":[{"at":1,"average":80.0,"stdDev":0.0,"apps":{"org.a":{"label":"A","score":80,"granted":["CAMERA"]}}}]}"""
        val history = Json { ignoreUnknownKeys = true }.decodeFromString<History>(old)
        val point = history.snapshots.single().apps.getValue("org.a")
        assertNull(point.trackers)
        assertNull(point.security)
        assertEquals("", history.snapshots.single().rules)
    }

    // --- Alerte stalkerware ------------------------------------------------------------------------

    @Test
    fun `stalkerware - une notification par app, oubliee une fois desinstallee`() {
        assertEquals(setOf("com.spy"), StalkerwareAlerts.newDetections(setOf("com.spy"), emptySet()))
        assertEquals(emptySet<String>(), StalkerwareAlerts.newDetections(setOf("com.spy"), setOf("com.spy")))
        // Scan complet sans elle : oubliée ; contrôle partiel (une nouvelle app) : les autres restent connues.
        assertEquals(emptySet<String>(), StalkerwareAlerts.remembered(setOf("com.spy"), emptySet(), full = true))
        assertEquals(setOf("com.spy", "com.new"), StalkerwareAlerts.remembered(setOf("com.spy"), setOf("com.new"), full = false))
    }

    // --- Cause principale de la baisse -------------------------------------------------------------

    private val scorer = PrivacyScorer(TestFixtures.rules)

    private fun app(
        pkg: String,
        granted: Set<String> = emptySet(),
        trackers: List<String> = emptyList(),
        signals: Set<String> = emptySet(),
        category: String = "CATEGORY_GAME",
    ): AuditedApp = scorer.scoreApp(
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
            appInfoCategory = category,
            declaredPermissions = granted,
            grantedPermissions = granted,
            signals = signals,
            requestedPermissions = emptyList(),
            permissions = emptyList(),
        ),
        TrackerAnalysis.Analyzed(trackers.mapIndexed { i, name -> Tracker(i, name, null, TestFixtures.exodusCategories.getValue(name).toList()) }),
    )

    private fun snapshot(apps: List<AuditedApp>, rules: String = "r1") = ScoreSnapshot(
        at = 0,
        average = apps.map { it.score.value }.average(),
        stdDev = 0.0,
        apps = apps.associate { it.app.packageName to it.historyPoint() },
        rules = rules,
    )

    private val stable = List(5) { app("org.clean.app$it") }

    private fun cause(before: List<AuditedApp>, after: List<AuditedApp>, rules: String = "r1") =
        DropCauseAnalyzer.analyze(snapshot(before), after, rules, scorer)

    @Test
    fun `cause - app installee`() {
        val bad = app("org.ads.game", trackers = listOf("Google AdMob", "Facebook Ads", "Criteo"))
        val result = cause(stable, stable + bad)
        assertEquals(DropCause.Type.INSTALLED, result.type)
        assertEquals("org.ads.game", result.packageName)
        assertEquals(bad.score.value, result.appScore)
        assertEquals("org.ads.game", result.pinnedPackage)
    }

    @Test
    fun `cause - app bien notee desinstallee`() {
        val good = app("org.good.app")
        val meh = app("org.meh.app", trackers = listOf("Google AdMob"))
        val result = cause(listOf(good, meh), listOf(meh))
        assertEquals(DropCause.Type.REMOVED, result.type)
        assertEquals("org.good.app", result.packageName)
        // Elle n'est plus là : rien à mettre en tête de Réduire.
        assertNull(result.pinnedPackage)
    }

    @Test
    fun `cause - permission sensible nouvellement accordee`() {
        val before = app("org.game.app")
        val after = app("org.game.app", granted = setOf("ACCESS_FINE_LOCATION"))
        assertTrue(after.score.value < before.score.value)
        val result = cause(stable + before, stable + after)
        assertEquals(DropCause.Type.PERMISSION_GRANTED, result.type)
        assertEquals("ACCESS_FINE_LOCATION", result.detail)
    }

    @Test
    fun `cause - traqueur ajoute par une mise a jour`() {
        val before = app("org.game.app", trackers = listOf("Criteo"))
        val after = app("org.game.app", trackers = listOf("Criteo", "Google AdMob"))
        val result = cause(stable + before, stable + after)
        assertEquals(DropCause.Type.TRACKER_ADDED, result.type)
        assertEquals("Google AdMob", result.detail)
    }

    @Test
    fun `cause - nouveau motif de securite`() {
        val before = app("org.keyboard.app", signals = setOf("INTERNET"))
        val after = app("org.keyboard.app", signals = setOf("INTERNET", "IME_ENABLED"))
        assertTrue("KEYLOGGER_CAPABLE" in after.result.securityFindings.map { it.id })
        val result = cause(stable + before, stable + after)
        assertEquals(DropCause.Type.SECURITY_PATTERN, result.type)
        assertEquals("KEYLOGGER_CAPABLE", result.detail)
    }

    @Test
    fun `cause - mise a jour des regles de score, sans changement d app`() {
        // Mêmes apps, notées plus sévèrement : seule l'empreinte des règles a changé.
        val harsher = stable.map { it.copy(score = PrivacyScore(it.score.value - 10)) }
        assertEquals(DropCause.Type.RULES_UPDATED, cause(stable, harsher, rules = "r2").type)
    }

    @Test
    fun `cause - baisse repartie quand rien ne l explique et que les regles n ont pas change`() {
        val lower = stable.map { it.copy(score = PrivacyScore(it.score.value - 10)) }
        assertEquals(DropCause.Type.SPREAD, cause(stable, lower).type)
    }

    @Test
    fun `cause - la mise a jour d une app l emporte sur un changement de regles plus faible`() {
        val bad = app("org.ads.game", trackers = listOf("Google AdMob", "Facebook Ads", "Criteo"))
        assertEquals(DropCause.Type.INSTALLED, cause(stable, stable + bad, rules = "r2").type)
    }
}
