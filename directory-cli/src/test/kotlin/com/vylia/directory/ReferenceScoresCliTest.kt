package com.vylia.directory

import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.ReferenceApps
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les apps de référence passées par le chemin de l'annuaire (rapport Exodus → AppScan) : même note que l'app. */
class ReferenceScoresCliTest {

    @Test
    fun `memes notes que le moteur et l app`() {
        val scorer = PrivacyScorer(ReferenceApps.rules)
        ReferenceApps.apps.forEach { ref ->
            // L'annuaire ne connaît pas le SDK cible : il n'est comparable que pour les apps de référence qui ne
            // déclarent pas l'accès historique au stockage.
            val scan = ReportConverter.toScan(ref.packageName, Fixtures.referenceReport(ref), Fixtures.referenceTrackers)
                .copy(targetSdk = ref.targetSdk)
            assertEquals(ref.packageName, ref.expected, ReferenceApps.summary(scorer.score(scan)))
        }
    }
}
