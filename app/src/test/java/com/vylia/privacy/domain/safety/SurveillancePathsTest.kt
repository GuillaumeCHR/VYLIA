package com.vylia.privacy.domain.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SurveillancePathsTest {

    @Test
    fun `le parcours autorites ne renvoie jamais vers une autorite`() {
        val resources = SupportResources(
            1,
            listOf(
                SupportResource("ngo", SurveillancePath.STATE, ResourceKind.NGO, "NGO", "https://ngo.example.org/"),
                SupportResource("police", SurveillancePath.STATE, ResourceKind.AUTHORITY, "Police", "https://police.example.org/"),
            ),
        )
        assertEquals(listOf("ngo"), resources.forPath(SurveillancePath.STATE).map { it.id })
    }

    @Test
    fun `le texte du parcours autorites ne parle ni de police ni de plainte`() {
        listOf("values", "values-fr").forEach { dir ->
            val xml = File("src/main/res/$dir/strings.xml").readText()
            val text = Regex("<string name=\"safety_state_text\">(.*?)</string>").find(xml)!!.groupValues[1].lowercase()
            listOf("police", "plainte", "complaint", "judicia", "autorités", "authorities").forEach { assertTrue("$dir : $it", it !in text) }
        }
    }
}
