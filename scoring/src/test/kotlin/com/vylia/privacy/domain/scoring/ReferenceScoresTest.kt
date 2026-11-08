package com.vylia.privacy.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

/** Les apps de référence gardent la note figée : une divergence avec l'app ou l'annuaire se voit ici. */
class ReferenceScoresTest {

    @Test
    fun `notes figees des apps de reference`() {
        val scorer = PrivacyScorer(ReferenceApps.rules)
        ReferenceApps.apps.forEach { app ->
            val actual = ReferenceApps.summary(scorer.score(ReferenceApps.scan(app)))
            if (app.expected == null) println("REF ${app.packageName} -> $actual")
            else assertEquals(app.packageName, app.expected, actual)
        }
    }
}
