package com.vylia.privacy.domain.preinstalled

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.ui.act.ManageSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreinstalledTest {
    private val list = UadList.parse(
        """
        {
          "com.samsung.android.weather": {"list": "Oem", "description": "Samsung Weather", "dependencies": [], "neededBy": [], "labels": [], "removal": "Recommended"},
          "com.google.android.youtube": {"list": "Google", "description": "YouTube", "dependencies": [], "neededBy": [], "labels": [], "removal": "Advanced"},
          "com.google.android.gms": {"list": "Google", "description": "Play Services", "dependencies": [], "neededBy": ["com.google.android.youtube", "bad name; rm -rf /"], "labels": [], "removal": "Expert"},
          "com.android.email": {"list": "Aosp", "description": "AOSP mail", "dependencies": [], "neededBy": [], "labels": [], "removal": "Recommended"},
          "com.orange.myorange": {"list": "Carrier", "description": "Orange", "dependencies": [], "neededBy": [], "labels": [], "removal": "Recommended"},
          "com.x; rm -rf ~": {"list": "Oem", "description": "trap", "dependencies": [], "neededBy": [], "labels": [], "removal": "Recommended"},
          "com.example.level": {"list": "Oem", "description": "unknown level", "removal": "Maybe"},
          "com.example.list": {"list": "Nope", "description": "unknown list", "removal": "Recommended"},
          "com.example.long": {"list": "Misc", "description": "${"x".repeat(5_000)}", "removal": "Unsafe"},
          "com.example.notobject": 42
        }
        """.trimIndent(),
    )

    private fun app(pkg: String, system: Boolean = true, launcher: Boolean = false) = InstalledApp(
        label = pkg,
        packageName = pkg,
        icon = object : Drawable() {
            override fun draw(canvas: android.graphics.Canvas) = Unit
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = 0
        },
        isSystemApp = system,
        category = null,
        apkPaths = emptyList(),
        lastUpdateTime = 0L,
        requestedPermissions = emptyList(),
        permissions = emptyList(),
        hasLauncherIcon = launcher,
    )

    @Test
    fun `invalid package names, unknown lists and levels are dropped`() {
        assertEquals(6, list.size)
        assertNull(list["com.x; rm -rf ~"])
        assertNull(list["com.example.level"])
        assertNull(list["com.example.list"])
        assertNull(list["com.example.notobject"])
    }

    @Test
    fun `levels and origins are mapped, descriptions and links are bounded`() {
        assertEquals(DisableRisk.SAFE, list["com.samsung.android.weather"]!!.risk)
        assertEquals(DisableRisk.CAUTION, list["com.google.android.youtube"]!!.risk)
        assertEquals(DisableRisk.ESSENTIAL, list["com.google.android.gms"]!!.risk)
        assertEquals(DisableRisk.ESSENTIAL, list["com.example.long"]!!.risk)
        assertEquals(PreinstalledOrigin.THIRD_PARTY, list["com.example.long"]!!.origin)
        assertEquals(UadList.MAX_DESCRIPTION, list["com.example.long"]!!.description.length)
        assertEquals(listOf("com.google.android.youtube"), list["com.google.android.gms"]!!.neededBy)
    }

    @Test
    fun `apps are split between user, preinstalled and system`() {
        assertEquals(AppGroup.USER, list.classify(app("com.samsung.android.weather", system = false)).group)
        assertEquals(AppGroup.PREINSTALLED, list.classify(app("com.samsung.android.weather")).group)
        assertEquals(AppGroup.PREINSTALLED, list.classify(app("com.google.android.youtube")).group)
        // Indispensable, ou d'Android lui-même (même avec une icône) : composant d'Android.
        assertEquals(AppGroup.SYSTEM, list.classify(app("com.google.android.gms")).group)
        assertEquals(AppGroup.SYSTEM, list.classify(app("com.android.email", launcher = true)).group)
        // Hors de la liste : l'icône dans le tiroir d'apps décide.
        assertEquals(AppGroup.PREINSTALLED, list.classify(app("com.vendor.gallery", launcher = true)).group)
        assertEquals(AppGroup.SYSTEM, list.classify(app("com.vendor.service")).group)
    }

    @Test
    fun `only safe or at-your-own-risk preinstalled apps get an action`() {
        assertTrue(list.classify(app("com.samsung.android.weather")).actionable)
        assertTrue(list.classify(app("com.google.android.youtube")).actionable)
        assertFalse(list.classify(app("com.google.android.gms")).actionable)
        assertFalse(list.classify(app("com.vendor.gallery", launcher = true)).actionable)
    }

    @Test
    fun `brand comes from the package name for manufacturer and carrier apps`() {
        assertEquals("Samsung", list.classify(app("com.samsung.android.weather")).brand)
        assertEquals("Orange", list.classify(app("com.orange.myorange")).brand)
        assertNull(list.classify(app("com.google.android.youtube")).brand)
    }

    @Test
    fun `adb commands only accept valid package names`() {
        assertEquals("adb shell pm disable-user --user 0 com.samsung.android.weather", AdbCommands.disable("com.samsung.android.weather"))
        assertEquals("adb shell pm enable --user 0 com.samsung.android.weather", AdbCommands.enable("com.samsung.android.weather"))
        assertEquals(2, AdbCommands.disableAll(listOf("a.b", "c.d")).lines().size)
        listOf("com.x; rm -rf ~", "com.x && reboot", "com.x\nreboot", "\$(id)", "", ".com").forEach { bad ->
            assertTrue(bad, runCatching { AdbCommands.disable(bad) }.isFailure)
        }
    }

    @Test
    fun `a downloaded list must be big enough and not lose more than a tenth`() {
        assertTrue(UadList.acceptable(5_400, 5_399))
        assertTrue(UadList.acceptable(4_900, 5_400))
        assertFalse(UadList.acceptable(4_800, 5_400))
        assertFalse(UadList.acceptable(3_000, 0))
    }

    @Test
    fun `the preinstalled tab only shows in advanced mode`() {
        assertFalse(ManageSection.PREINSTALLED in ManageSection.visible(shieldEnabled = true, advanced = false))
        assertTrue(ManageSection.PREINSTALLED in ManageSection.visible(shieldEnabled = false, advanced = true))
        assertFalse(ManageSection.TRACKERS in ManageSection.visible(shieldEnabled = false, advanced = true))
    }

    @Test
    fun `the embedded list is read in full`() {
        val embedded = UadList.parse(File("src/main/assets/uad_lists.json").readText())
        assertTrue(embedded.size.toString(), embedded.size >= UadList.MIN_ENTRIES)
        assertEquals(DisableRisk.ESSENTIAL, embedded["com.android.systemui"]?.risk)
    }
}
