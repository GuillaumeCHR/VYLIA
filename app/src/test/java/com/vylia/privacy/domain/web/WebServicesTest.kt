package com.vylia.privacy.domain.web

import com.vylia.privacy.domain.scoring.AppScan
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebServicesTest {
    private val embedded = WebServices.parse(File("src/main/assets/web_services.json").readText())

    @Test
    fun `liste embarquee - lisible, categories connues de la grille`() {
        assertTrue(embedded.services.size >= 7)
        val categories = TestFixtures.rules.categories.keys
        assertTrue(embedded.services.all { it.category in categories })
        assertEquals(embedded.services.size, embedded.services.map { it.id }.toSet().size)
    }

    @Test
    fun `domaine et sous-domaine reconnus, jamais un domaine qui finit pareil ni du http`() {
        assertEquals("lemonde", embedded.forUrl("https://www.lemonde.fr/")?.id)
        assertEquals("lemonde", embedded.forUrl("https://lemonde.fr/international/")?.id)
        assertEquals("myswisslife", embedded.forUrl("https://myswisslife.fr/")?.id)
        assertNull(embedded.forUrl("https://fauxlemonde.fr/"))
        assertNull(embedded.forUrl("http://www.lemonde.fr/"))
        assertNull(embedded.forUrl(null))
        assertEquals("lemonde", embedded.forPackage("com.lemonde.morning")?.id)
    }

    @Test
    fun `entree mal formee ecartee`() {
        val parsed = WebServices.parse(
            """{"services":[
              {"id":"ok","name":"Ok","url":"https://ok.example.org/","hosts":["ok.example.org"],"category":"NEWS_READING"},
              {"id":"http","name":"X","url":"http://x.example.org/","hosts":["x.example.org"],"category":"NEWS_READING"},
              {"id":"host","name":"X","url":"https://x.example.org/","hosts":["bad host"],"category":"NEWS_READING"},
              {"id":"pkg","name":"X","url":"https://x.example.org/","hosts":["x.example.org"],"category":"NEWS_READING","packages":["not a package"]}
            ]}"""
        )
        assertEquals(listOf("ok"), parsed.services.map { it.id })
    }

    @Test
    fun `un WebAPK prend la categorie de son site, apres les surcharges de la grille`() {
        val scorer = PrivacyScorer(TestFixtures.rules)
        val webapk = AppScan("org.chromium.webapk.a1", 33, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList())
        assertEquals("DEFAULT", scorer.resolveCategory(webapk))
        assertEquals("FINANCE_ACCOUNTS", scorer.resolveCategory(webapk.copy(webCategory = "FINANCE_ACCOUNTS")))
        // Une catégorie inconnue de la grille est ignorée.
        assertEquals("DEFAULT", scorer.resolveCategory(webapk.copy(webCategory = "NO_SUCH")))
        // Les traqueurs ont été analysés (0) : pas de pénalité « non analysée ».
        val result = scorer.score(webapk.copy(webCategory = "FINANCE_ACCOUNTS"))
        assertEquals(100, result.score)
    }
}
