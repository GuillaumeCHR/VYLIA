package com.vylia.privacy.domain.learn

import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRationaleTest {

    @Test
    fun `chaque categorie des regles a sa justification`() {
        val missing = TestFixtures.rules.categories.keys.filter { CategoryRationale.of(it, null) == null }
        assertTrue("Catégories sans justification : $missing", missing.isEmpty())
    }

    @Test
    fun `categorie importee inconnue - description des regles`() {
        assertEquals("Décrite par les règles", CategoryRationale.of("NOUVELLE", "Décrite par les règles"))
    }
}
