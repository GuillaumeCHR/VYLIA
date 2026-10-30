package com.vylia.privacy.domain.learn

import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerGlossaryTest {

    @Test
    fun `chaque categorie notee par les regles est expliquee`() {
        val missing = TestFixtures.rules.trackerRules.categoryWeights.keys.filter { TrackerGlossary.find(it) == null }
        assertTrue("Catégories sans explication : $missing", missing.isEmpty())
    }

    @Test
    fun `les exemples existent dans le snapshot Exodus avec cette categorie`() {
        TrackerGlossary.entries.forEach { info ->
            info.examples.forEach { name ->
                val categories = TestFixtures.exodusCategories[name]
                assertTrue("$name absent du snapshot", categories != null)
                assertTrue("$name n'est pas classé ${info.category} (${categories})", info.category in categories!!)
            }
        }
    }
}
