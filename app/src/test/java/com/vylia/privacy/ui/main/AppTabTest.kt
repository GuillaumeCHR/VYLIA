package com.vylia.privacy.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTabTest {
    @Test
    fun `barre du bas - cinq destinations avec le bouclier, quatre sans`() {
        assertEquals(
            listOf(AppTab.SCAN, AppTab.ANALYZE, AppTab.MANAGE, AppTab.PROTECT, AppTab.CELEBRATE),
            AppTab.destinations(shieldEnabled = true),
        )
        assertEquals(listOf(AppTab.SCAN, AppTab.ANALYZE, AppTab.MANAGE, AppTab.CELEBRATE), AppTab.destinations(shieldEnabled = false))
    }
}
