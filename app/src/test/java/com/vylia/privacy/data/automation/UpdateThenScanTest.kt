package com.vylia.privacy.data.automation

import com.vylia.privacy.data.databases.AutoUpdatePolicy
import com.vylia.privacy.data.databases.NetworkState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateThenScanTest {

    @Test
    fun `le scan part apres la mise a jour`() = runTest {
        val order = mutableListOf<String>()
        UpdateThenScan.run(update = { order += "update" }, scan = { order += "scan" })
        assertEquals(listOf("update", "scan"), order)
    }

    @Test
    fun `le scan part aussi apres un echec de la mise a jour`() = runTest {
        var scanned = false
        UpdateThenScan.run(update = { error("réseau indisponible") }, scan = { scanned = true }, onUpdateError = {})
        assertTrue(scanned)
    }

    @Test
    fun `une mise a jour par semaine au plus`() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(AutoUpdatePolicy.isDue(enabled = true, lastAutoUpdate = null, now = 10 * day))
        assertFalse(AutoUpdatePolicy.isDue(enabled = true, lastAutoUpdate = 10 * day, now = 13 * day))
        assertTrue(AutoUpdatePolicy.isDue(enabled = true, lastAutoUpdate = 10 * day, now = 17 * day))
        assertFalse(AutoUpdatePolicy.isDue(enabled = false, lastAutoUpdate = null, now = 10 * day))
    }

    @Test
    fun `sans reseau rien et en donnees mobiles un simple controle si Wi-Fi seulement`() {
        assertEquals(AutoUpdatePolicy.Action.SKIP, AutoUpdatePolicy.action(NetworkState.NONE, wifiOnly = false))
        assertEquals(AutoUpdatePolicy.Action.CHECK, AutoUpdatePolicy.action(NetworkState.METERED, wifiOnly = true))
        assertEquals(AutoUpdatePolicy.Action.UPDATE, AutoUpdatePolicy.action(NetworkState.METERED, wifiOnly = false))
        assertEquals(AutoUpdatePolicy.Action.UPDATE, AutoUpdatePolicy.action(NetworkState.UNMETERED, wifiOnly = true))
    }
}
