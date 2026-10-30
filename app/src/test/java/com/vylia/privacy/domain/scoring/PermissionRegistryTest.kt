package com.vylia.privacy.domain.scoring

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRegistryTest {

    @Test
    fun `representant de famille et ordre`() {
        val strongest = PermissionRegistry.strongestByFamily(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS),
        )
        assertEquals(listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_MEDIA_IMAGES), strongest.map { it.permission })
    }
}
