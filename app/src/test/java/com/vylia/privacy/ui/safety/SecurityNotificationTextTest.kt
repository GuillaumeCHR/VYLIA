package com.vylia.privacy.ui.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Les textes des notifications de sécurité, dans les deux langues, ne nomment rien et ne disent rien d'explicite. */
class SecurityNotificationTextTest {

    private val keys = listOf("notif_security_title", "notif_security_text", "notif_channel_security", "notif_channel_security_description", "notif_threshold_sensitive")
    private val forbidden = listOf("stalker", "espion", "spy", "keylog", "surveill", "%1\$s")

    @Test
    fun `aucun terme explicite ni nom d app`() {
        listOf("values", "values-fr").forEach { dir ->
            val xml = File("src/main/res/$dir/strings.xml").readText()
            keys.forEach { key ->
                val text = Regex("<string name=\"$key\"[^>]*>(.*?)</string>").find(xml)?.groupValues?.get(1)
                assertTrue("$dir/$key absent", text != null)
                forbidden.forEach { word -> assertFalse("$dir/$key contient $word", text!!.lowercase().contains(word)) }
            }
        }
    }

    @Test
    fun `l ancienne notification explicite a disparu`() {
        val xml = File("src/main/res/values-fr/strings.xml").readText()
        assertFalse(xml.contains("notif_stalkerware_title"))
        assertFalse(xml.contains("Logiciel espion connu détecté"))
    }

    @Test
    fun `plus aucun numero national en dur dans les textes`() {
        listOf("values", "values-fr").forEach { dir ->
            val xml = File("src/main/res/$dir/strings.xml").readText()
            assertFalse(dir, xml.contains("3919"))
        }
    }
}
