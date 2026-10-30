package com.vylia.privacy.domain.learn

import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeBandsTest {

    private val bands = GradeBands.from(TestFixtures.rules.scoring).associateBy { it.grade }

    @Test
    fun `tranches contigues de 100 a 0`() {
        assertEquals(listOf("A" to (85..100), "B" to (70..84), "C" to (50..69), "D" to (30..49), "E" to (0..29)),
            GradeBands.from(TestFixtures.rules.scoring).map { it.grade to (it.min..it.max) })
    }

    @Test
    fun `au-dessus de 50 aucune exigence, les permissions seules peuvent y mener`() {
        listOf("A", "B", "C").forEach {
            assertEquals(it, null, bands.getValue(it).permissionPenaltyAbove)
            assertEquals(it, null, bands.getValue(it).trackerPenaltyAbove)
        }
    }

    @Test
    fun `sous 50 les traqueurs pesent forcement, sous 30 les deux se cumulent`() {
        // D : pénalité > 50,5 avec des permissions plafonnées à 50 → au moins un point de traqueurs.
        assertEquals(null, bands.getValue("D").permissionPenaltyAbove)
        assertEquals(0, bands.getValue("D").trackerPenaltyAbove)
        // E : pénalité > 70,5 → permissions > 10,5 (traqueurs plafonnés à 60) et traqueurs > 20,5.
        assertEquals(10, bands.getValue("E").permissionPenaltyAbove)
        assertEquals(20, bands.getValue("E").trackerPenaltyAbove)
    }
}
