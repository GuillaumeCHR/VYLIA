package com.vylia.privacy.domain.learn

import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRationaleTest {

    @Test
    fun `chaque categorie des regles a sa justification`() {
        val missing = TestFixtures.rules.categories.keys.filter { CategoryRationale.of(it) == null || CategoryRationale.nameOf(it) == null }
        assertTrue("Catégories sans justification : $missing", missing.isEmpty())
    }

    @Test
    fun `categorie importee inconnue - textes des regles a la place`() {
        assertNull(CategoryRationale.of("NOUVELLE"))
        assertNull(CategoryRationale.nameOf("NOUVELLE"))
    }
}
