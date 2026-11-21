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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LighterVariantsTest {

    private val scorer = PrivacyScorer(TestFixtures.rules)
    private val embedded = LighterVariants.parse(File("src/main/assets/lighter_variants.json").readText())

    private fun app(pkg: String, trackers: Int): AuditedApp = scorer.scoreApp(
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
            appInfoCategory = "SOCIAL",
            declaredPermissions = emptySet(),
            grantedPermissions = emptySet(),
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
    fun `liste embarquee - lisible, Facebook Lite et TikTok Lite`() {
        assertEquals(listOf("com.facebook.lite", "com.zhiliaoapp.musically.go"), embedded.variants.map { it.packageName })
        assertTrue(embedded.variants.all { it.loss.containsKey("en") && it.loss.containsKey("fr") })
    }

    @Test
    fun `proposee pour l app d origine mal notee, la plus mal notee d abord`() {
        val facebook = app("com.facebook.katana", trackers = 8)
        val tiktok = app("com.ss.android.ugc.trill", trackers = 3)
        val suggestions = embedded.suggestions(listOf(tiktok, facebook, app("org.example.other", 5)))
        assertEquals(listOf("com.facebook.katana", "com.ss.android.ugc.trill"), suggestions.map { it.app.app.packageName })
        assertEquals("com.facebook.lite", suggestions.first().variant.packageName)
    }

    @Test
    fun `pas proposee si deja installee ou si l app est deja mieux notee`() {
        val facebook = app("com.facebook.katana", trackers = 8)
        assertTrue(embedded.suggestions(listOf(facebook, app("com.facebook.lite", 0))).isEmpty())
        // Sans traqueur ni permission, l'app d'origine vaut 100 : mieux que Facebook Lite (50).
        assertTrue(embedded.suggestions(listOf(app("com.facebook.katana", trackers = 0))).isEmpty())
    }

    @Test
    fun `entrees mal formees ecartees`() {
        val parsed = LighterVariants.parse(
            """
            {"variants":[
              {"replaces":["a.b"],"package":"a.b.lite","name":"Ok‮","grade":"B","score":70,"trackers":1,"exodus_report":1,"exodus_date":"2026-01-01","loss":{"en":"x"}},
              {"replaces":["a.b"],"package":"a.b.lite","name":"Note","grade":"F","score":70,"trackers":1,"exodus_report":1,"exodus_date":"2026-01-01","loss":{"en":"x"}},
              {"replaces":["a.b"],"package":"a.b.lite","name":"Score","grade":"B","score":170,"trackers":1,"exodus_report":1,"exodus_date":"2026-01-01","loss":{"en":"x"}},
              {"replaces":["a.b"],"package":"a.b.lite","name":"Date","grade":"B","score":70,"trackers":1,"exodus_report":1,"exodus_date":"hier","loss":{"en":"x"}},
              {"replaces":["a b"],"package":"a.b.lite","name":"Paquet","grade":"B","score":70,"trackers":1,"exodus_report":1,"exodus_date":"2026-01-01","loss":{"en":"x"}},
              {"replaces":["a.b"],"package":"a.b","name":"Lui-meme","grade":"B","score":70,"trackers":1,"exodus_report":1,"exodus_date":"2026-01-01","loss":{"en":"x"}}
            ]}
            """,
        )
        assertEquals(listOf("Ok"), parsed.variants.map { it.name })
    }
}
