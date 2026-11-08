package com.vylia.privacy.domain.scoring

import com.vylia.privacy.domain.scoring.TestFixtures.trackers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cas reconstruits sur un téléphone réel : permissions accordées et
 * traqueurs listés dans l'export vylia_audit_report.csv.
 */
class PrivacyScorerTest {

    private val scorer = PrivacyScorer(TestFixtures.rules)

    private fun scan(
        packageName: String,
        granted: Set<String>,
        trackers: List<Tracker>?,
        targetSdk: Int = 35,
        category: String = "CATEGORY_UNDEFINED",
    ) = AppScan(packageName, targetSdk, category, declaredPermissions = granted, grantedPermissions = granted, trackers = trackers)

    // "Amazon Advertisement" n'a pas de catégorie dans Exodus : c'est le mapping validé
    // (app/src/main/assets/exodus_category_overrides.json) qui le classe en Advertisement (12) ; sans lui, 40 au lieu de 37.
    @Test
    fun `leboncoin vaut 37 (D)`() {
        val result = scorer.score(
            scan(
                "fr.leboncoin",
                setOf("ACCESS_FINE_LOCATION"),
                trackers(
                    "mParticle", "Batch", "Google CrashLytics", "Google Firebase Analytics", "Adjust",
                    "Amazon Advertisement", "Google AdMob", "Didomi", "Prebid Mobile",
                ),
            )
        )
        assertEquals("ECOMMERCE", result.categoryId)
        assertEquals(3.2, result.permissionPenalty, 0.001)
        assertEquals(60, result.trackerPenalty)
        assertEquals(37, result.score)
        assertEquals("D", result.grade)
    }

    @Test
    fun `Bandsintown vaut 38 (D)`() {
        val result = scorer.score(
            scan(
                "com.bandsintown",
                setOf("ACCESS_FINE_LOCATION", "READ_EXTERNAL_STORAGE"),
                trackers(
                    "Google CrashLytics", "Google Analytics", "Google Firebase Analytics", "Facebook Ads",
                    "Facebook Analytics", "Facebook Login", "AppLovin (MAX and SparkLabs)", "Amazon Advertisement",
                    "Fyber", "Google Tag Manager", "Inmobi", "Unity3d Ads", "ironSource", "AltBeacon",
                    "Google AdMob", "IAB Open Measurement",
                ),
                // READ_EXTERNAL_STORAGE n'est compté que jusqu'au targetSdk 32 (la simulation le compte).
                targetSdk = 32,
            )
        )
        assertEquals(2.0, result.permissionPenalty, 0.001)
        assertEquals(60, result.trackerPenalty)
        assertEquals(38, result.score)
        assertEquals("D", result.grade)
    }

    @Test
    fun `COROS vaut 74 (B)`() {
        val result = scorer.score(
            scan(
                "com.garmin.android.apps.connectmobile",
                setOf("CAMERA", "READ_SMS", "ACCESS_FINE_LOCATION", "READ_CONTACTS"),
                trackers("Google AdMob", "Sentry"),
            )
        )
        assertEquals("WEARABLE_COMPANION", result.categoryId)
        assertEquals(11.6, result.permissionPenalty, 0.001)
        assertEquals(14, result.trackerPenalty)
        assertEquals(74, result.score)
        assertEquals("B", result.grade)
        assertEquals(Confidence.FULL, result.confidence)
    }

    @Test
    fun `DuckDuckGo vaut 98 (A)`() {
        val result = scorer.score(scan("com.duckduckgo.mobile.android", setOf("CAMERA"), trackers = emptyList()))
        assertEquals(1.6, result.permissionPenalty, 0.001)
        assertEquals(0, result.trackerPenalty)
        assertEquals(98, result.score)
        assertEquals("A", result.grade)
    }

    @Test
    fun `traqueurs non analyses - penalite forfaitaire et note suffixee`() {
        val result = scorer.score(scan("org.example.unknown", emptySet(), trackers = null))
        assertEquals(Confidence.PARTIAL, result.confidence)
        assertEquals(12, result.trackerPenalty)
        assertEquals(88, result.score)
        assertEquals("A?", result.grade)
    }

    @Test
    fun `permission seulement declaree comptee au quart`() {
        val declaredOnly = AppScan("org.example", 35, "CATEGORY_UNDEFINED", setOf("READ_CONTACTS"), emptySet(), emptyList())
        val finding = scorer.score(declaredOnly).permissionFindings.single()
        assertEquals(Context.UNEXPECTED, finding.context)
        assertEquals(2.5, finding.penalty, 0.001)
    }

    @Test
    fun `categorie Android traduite`() {
        assertEquals("GAME", scorer.resolveCategory(scan("org.example.game", emptySet(), null, category = "CATEGORY_GAME")))
    }

    @Test
    fun `VYLIA se note elle-meme avec la meme grille et assume QUERY_ALL_PACKAGES`() {
        // Permissions réelles du manifeste ; l'accès aux données d'utilisation reste à accorder par l'utilisateur.
        val declared = setOf(
            "QUERY_ALL_PACKAGES", "PACKAGE_USAGE_STATS", "INTERNET", "ACCESS_NETWORK_STATE", "POST_NOTIFICATIONS", "RECEIVE_BOOT_COMPLETED",
        )
        val vylia = AppScan("com.vylia.privacy", 37, "CATEGORY_UNDEFINED", declared, declared - "PACKAGE_USAGE_STATS", emptyList())
        assertEquals(false, scorer.isExcluded("com.vylia.privacy"))
        val result = scorer.score(vylia)
        assertEquals("SECURITY_AUTH", result.categoryId)
        assertTrue(result.permissionFindings.single { it.permission == "QUERY_ALL_PACKAGES" }.counted > 0.0)
        assertTrue(result.score < 100)
        println("Score de VYLIA : ${result.score} (${result.grade})")
    }

    @Test
    fun `les notifications ne sont jamais comptees, meme accordees ou avec un poids`() {
        val notifications = setOf("POST_NOTIFICATIONS")
        val result = scorer.score(AppScan("org.example.news", 34, "CATEGORY_NEWS", notifications, notifications, emptyList()))
        assertTrue(result.permissionFindings.isEmpty())
        assertEquals(0.0, result.permissionPenalty, 0.0)
        // Une grille qui lui donnerait un poids ne la ferait pas compter : la liste des neutralisées l'emporte.
        val weighted = PrivacyScorer(TestFixtures.rules.let { it.copy(permissionWeights = it.permissionWeights + ("POST_NOTIFICATIONS" to 12)) })
        assertTrue(weighted.score(AppScan("org.example.news", 34, "CATEGORY_NEWS", notifications, notifications, emptyList())).permissionFindings.isEmpty())
    }
}
