package com.vylia.privacy.domain.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupportResourcesTest {

    private val file = File("src/main/assets/support_resources.json")
    private val shipped = SupportResources.parse(file.readText())

    @Test
    fun `le fichier livre est entierement valide`() {
        assertEquals(Regex("\"id\"").findAll(file.readText()).count(), shipped.size)
    }

    @Test
    fun `seulement deux exemples internationaux, un par parcours concerne`() {
        assertEquals(listOf("stopstalkerware"), shipped.forPath(SurveillancePath.PARTNER).map { it.id })
        assertEquals(listOf("amnesty_securitylab"), shipped.forPath(SurveillancePath.STATE).map { it.id })
        // Parcours « criminel » : conseils génériques seulement.
        assertTrue(shipped.forPath(SurveillancePath.CRIMINAL).isEmpty())
    }

    @Test
    fun `aucun organisme national`() {
        assertTrue("region" !in file.readText())
    }

    @Test
    fun `entrees invalides ecartees`() {
        val parsed = SupportResources.parse(
            """{"version":1,"resources":[
              {"id":"ok","path":"PARTNER","kind":"NGO","name":"Ok","url":"https://ok.example.org/"},
              {"id":"http","path":"PARTNER","kind":"NGO","name":"Http","url":"http://bad.example.org/"},
              {"id":"phone","path":"PARTNER","kind":"HELPLINE","name":"Phone","url":"https://p.example.org/","phone":"call-me"},
              {"id":"long","path":"PARTNER","kind":"NGO","name":"${"x".repeat(200)}","url":"https://l.example.org/"},
              {"id":"Bad Id","path":"PARTNER","kind":"NGO","name":"R","url":"https://r.example.org/"}]}""",
        )
        assertEquals(listOf("ok"), parsed.resources.map { it.id })
    }
}
