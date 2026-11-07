package com.vylia.privacy.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTabTest {
    @Test
    fun `barre du bas - cinq destinations, avec ou sans le bouclier (Filtrer garde le DNS prive et WebAPK)`() {
        assertEquals(
            listOf(AppTab.SCAN, AppTab.ANALYZE, AppTab.MANAGE, AppTab.PROTECT, AppTab.CELEBRATE),
            AppTab.destinations(shieldEnabled = true),
        )
        assertEquals(AppTab.destinations(shieldEnabled = true), AppTab.destinations(shieldEnabled = false))
    }
}
