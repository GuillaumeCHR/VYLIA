package com.vylia.privacy.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionClassifierTest {

    @Test
    fun `une fiche du lexique impose son theme`() {
        assertEquals(PermissionTheme.LOCATION, PermissionClassifier.theme("android.permission.ACCESS_FINE_LOCATION"))
        assertEquals(PermissionTheme.ADS, PermissionClassifier.theme("com.google.android.gms.permission.AD_ID"))
        assertEquals(PermissionTheme.SCREEN, PermissionClassifier.theme("android.permission.DETECT_SCREEN_CAPTURE"))
    }

    @Test
    fun `sans fiche, le nom range la permission`() {
        assertEquals(PermissionTheme.SYSTEM, PermissionClassifier.theme("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"))
        assertEquals(PermissionTheme.MIC_CAMERA, PermissionClassifier.theme("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertEquals(PermissionTheme.CONTACTS, PermissionClassifier.theme("android.permission.WRITE_SYNC_SETTINGS"))
        assertEquals(PermissionTheme.NETWORK, PermissionClassifier.theme("android.permission.CHANGE_NETWORK_STATE"))
        assertEquals(PermissionTheme.NEARBY, PermissionClassifier.theme("android.permission.BLUETOOTH_ADVERTISE"))
        assertEquals(PermissionTheme.CONTROL, PermissionClassifier.theme("android.permission.DISABLE_KEYGUARD"))
        assertEquals(PermissionTheme.OTHER, PermissionClassifier.theme("com.example.permission.C2D_INTERNAL"))
    }

    @Test
    fun `Health Connect - une fiche pour toutes les donnees de sante`() {
        assertNotNull(PermissionGlossary.find("android.permission.health.READ_SLEEP"))
        assertEquals(PermissionTheme.HEALTH, PermissionClassifier.theme("android.permission.health.WRITE_STEPS"))
    }

    @Test
    fun `badges des lanceurs regroupes, pas les permissions Android`() {
        assertTrue(PermissionClassifier.isLauncherBadge("com.sec.android.provider.badge.permission.READ"))
        assertTrue(PermissionClassifier.isLauncherBadge("com.htc.launcher.permission.UPDATE_SHORTCUT"))
        assertTrue(PermissionClassifier.isLauncherBadge("me.everything.badger.permission.BADGE_COUNT_READ"))
        assertFalse(PermissionClassifier.isLauncherBadge("android.permission.READ_APP_BADGE"))
    }
}
