package com.vylia.privacy.domain.learn

import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionGlossaryTest {

    @Test
    fun `chaque permission notee par les regles est expliquee`() {
        val missing = TestFixtures.rules.permissionWeights.keys - PermissionGlossary.entries.map { it.permission }.toSet()
        assertTrue("Permissions sans explication : $missing", missing.isEmpty())
    }

    @Test
    fun `une entree par permission, textes renseignes`() {
        val entries = PermissionGlossary.entries
        assertEquals(entries.size, entries.map { it.permission }.toSet().size)
        entries.forEach { assertTrue(it.permission, it.title != 0 && it.allows != 0 && it.whySensitive != 0) }
    }

    @Test
    fun `recherche avec ou sans prefixe android permission`() {
        assertEquals(GrantMode.INSTALL, PermissionGlossary.find("android.permission.QUERY_ALL_PACKAGES")?.grant)
        assertNotNull(PermissionGlossary.find("READ_PHONE_STATE"))
    }
}
