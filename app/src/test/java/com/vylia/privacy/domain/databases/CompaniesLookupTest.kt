package com.vylia.privacy.domain.databases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompaniesLookupTest {

    private val lookup = CompaniesLookup.parse(
        """{"categories":{"4":"advertising","3":"pornvertising","6":"site_analytics","9":"cdn"},
            "trackers":{"criteo":{"name":"Criteo","categoryId":4,"companyId":"criteo"},
                        "exo":{"name":"ExoClick","categoryId":3,"companyId":"exo"},
                        "matomo":{"name":"Matomo","categoryId":6},
                        "cdn":{"name":"Some CDN","categoryId":9,"companyId":"missing"}},
            "trackerDomains":{"criteo.com":"criteo","exoclick.com":"exo","matomo.cloud":"matomo","cdn.example.net":"cdn","bad domain":"criteo"}}""",
        """{"companies":{"criteo":{"name":"Criteo SA"},"exo":{"name":"ExoClick"}}}""",
    )

    @Test
    fun `domaine sous-domaines et entreprise`() {
        assertEquals(Company("Criteo SA", CompanyCategory.TARGETING), lookup.lookup("cas.criteo.com"))
        assertEquals(Company("Criteo SA", CompanyCategory.TARGETING), lookup.lookup("criteo.com."))
        assertNull(lookup.lookup("notcriteo.com"))
        assertNull(lookup.lookup("com"))
    }

    @Test
    fun `sans entreprise le nom du traqueur et des categories neutres`() {
        assertEquals(Company("Matomo", CompanyCategory.AUDIENCE), lookup.lookup("x.matomo.cloud"))
        assertEquals(Company("Some CDN", CompanyCategory.THIRD_PARTY), lookup.lookup("cdn.example.net"))
        // Aucune étiquette morale : le ciblage sur les sites pour adultes est du ciblage.
        assertEquals(CompanyCategory.TARGETING, lookup.lookup("exoclick.com")?.category)
    }

    @Test
    fun `domaines invalides ecartes et index relu a l identique`() {
        assertEquals(4, lookup.size)
        val again = CompaniesLookup.fromLines(lookup.toLines())
        assertEquals(lookup.lookup("cas.criteo.com"), again.lookup("cas.criteo.com"))
        assertEquals(4, again.size)
    }
}
