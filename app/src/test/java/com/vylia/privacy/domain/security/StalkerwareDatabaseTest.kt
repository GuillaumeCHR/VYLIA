package com.vylia.privacy.domain.security

import com.vylia.privacy.domain.scoring.AppScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class StalkerwareDatabaseTest {

    private fun scan(pkg: String, certs: Set<String> = emptySet()) =
        AppScan(pkg, 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList()).copy(signingCertDigests = certs)

    @Test
    fun `ioc yaml - garde les stalkerwares, leurs paquets et certificats, ignore le reste`() {
        val yaml = """
            # Indicateurs d'Echap
            - name: TheTruthSpy
              names:
              - Copy9
              type: stalkerware
              packages:
              - com.thetruth
              - 'com.guest'
              certificates:
              - 31A6ECECD97CF39BC4126B8745CD94A7C30BF81C
              websites:
              - copy9.com  # site de vente
              c2:
                ips:
                - 69.64.74.239
                domains:
                - api.example.com
            - name: "Watchful"
              type: watchware
              packages:
              - com.watchful.parent
        """.trimIndent()
        val loaded = StalkerwareDatabase.parseIoc(yaml)
        assertEquals(1, loaded.apps)
        assertEquals("TheTruthSpy", loaded.iocs.match(scan("com.guest")))
        assertEquals("TheTruthSpy", loaded.iocs.match(scan("org.renamed", setOf("31a6ececd97cf39bc4126b8745cd94a7c30bf81c"))))
        // Le contrôle parental assumé (watchware) n'est pas un stalkerware.
        assertNull(loaded.iocs.match(scan("com.watchful.parent")))
    }

    @Test
    fun `ioc yaml - une structure inattendue est refusee plutot que devinee`() {
        assertThrows(IllegalArgumentException::class.java) { EchapIocParser.parse("packages:\n- com.spy") }
        assertThrows(IllegalArgumentException::class.java) { EchapIocParser.parse("- name: Spy\n\tpackages: [com.spy]") }
    }

    /**
     * Même lecture que tools/update_stalkerware_iocs.py sur le vrai fichier d'Echap, quand il est fourni
     * (ECHAP_IOC_YAML=/chemin/ioc.yaml) : mêmes stalkerwares, mêmes paquets, mêmes certificats que la base embarquée.
     */
    @Test
    fun `ioc yaml reel - meme contenu que la base embarquee`() {
        val path = System.getenv("ECHAP_IOC_YAML")
        assumeTrue(path != null && File(path).exists())
        val fromYaml = StalkerwareDatabase.parseIoc(File(path!!).readText())
        val embedded = StalkerwareDatabase.parse(File("src/main/assets/stalkerware_iocs.json").readText())
        assertEquals(embedded.apps, fromYaml.apps)
        assertEquals(embedded.iocs.size, fromYaml.iocs.size)
        assertEquals(embedded.iocs.entries(), fromYaml.iocs.entries())
    }
}
