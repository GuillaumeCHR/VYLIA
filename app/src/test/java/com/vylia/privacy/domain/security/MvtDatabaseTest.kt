package com.vylia.privacy.domain.security

import com.vylia.privacy.domain.scoring.AppScan
import com.vylia.privacy.domain.scoring.IocDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MvtDatabaseTest {

    private fun scan(pkg: String, system: Boolean = false, certs: Set<String> = emptySet()) =
        AppScan(pkg, 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList())
            .copy(signingCertDigests = certs, isSystemApp = system)

    private val sample = MvtDatabase.parse(
        """
        {"commit":"b22ddf05e1a31e7732b8895676987c5c3482ef65","campaigns":[
          {"name":"NoviSpy","packages":["com.serv.services","not a package"],
           "certificates":["35926E966186C8F2DEEC66864389D73989C75C85CB6668DA2F455DB6FE67E91F","xyz"],"domains":["a.example"]},
          {"name":"Pegasus (NSO Group)","domains":["nsoqa.com"]},
          {"name":"Morpheus","packages":["com.android.core"]}
        ]}
        """,
    )

    @Test
    fun `paquets et certificats valides, campagnes sans indicateur d'app non comptees`() {
        assertEquals(2, sample.campaigns)
        assertEquals("b22ddf05e1a31e7732b8895676987c5c3482ef65", sample.commit)
        assertEquals("NoviSpy", sample.iocs.match(scan("com.serv.services")))
        assertEquals("NoviSpy", sample.iocs.match(scan("renamed.app", certs = setOf("35926e966186c8f2deec66864389d73989c75c85cb6668da2f455db6fe67e91f"))))
        assertNull(sample.iocs.match(scan("not a package")))
    }

    @Test
    fun `un nom de paquet imitant le systeme ne vise pas une app preinstallee`() {
        assertEquals("Morpheus", sample.iocs.match(scan("com.android.core")))
        assertNull(sample.iocs.match(scan("com.android.core", system = true)))
        // Le certificat, lui, vaut partout.
        val cert = "35926e966186c8f2deec66864389d73989c75c85cb6668da2f455db6fe67e91f"
        assertEquals("NoviSpy", sample.iocs.match(scan("com.android.core", system = true, certs = setOf(cert))))
    }

    @Test
    fun `fusion avec Echap - surveillance ciblee reconnue a part`() {
        val echap = IocDatabase(mapOf("com.thetruth" to "TheTruthSpy"), emptyMap())
        val all = echap + sample.iocs
        assertEquals("TheTruthSpy", all.match(scan("com.thetruth")))
        assertEquals("NoviSpy", all.match(scan("com.serv.services")))
        assertTrue("NoviSpy" in all.targeted)
        assertEquals(1, all.stalkerwareSize)
        assertEquals(2, all.targetedSize)
    }

    @Test
    fun `base embarquee - lisible, campagnes connues`() {
        val embedded = MvtDatabase.parse(File("src/main/assets/${MvtDatabase.ASSET}").readText())
        assertTrue(embedded.campaigns >= 7)
        assertTrue(embedded.total >= 15)
        assertTrue(embedded.domains.size >= 4_000)
        assertEquals("NoviSpy", embedded.iocs.match(scan("com.serv.services")))
        assertEquals(40, embedded.commit.length)
    }

    @Test
    fun `index - campagnes github seulement, sans Echap ni chemin douteux`() {
        val yaml = """
            indicators:
              -
                type: github
                name: Predator Spyware Indicators of Compromise
                github:
                  owner: mvt-project
                  repo: mvt-indicators
                  branch: main
                  path: intellexa_predator/predator.stix2
              -
                type: github
                name: Stalkerware Indicators of Compromise
                github:
                  owner: AssoEchap
                  repo: stalkerware-indicators
                  branch: master
                  path: generated/stalkerware.stix2
              -
                type: github
                name: Evil
                github:
                  owner: evil
                  repo: x
                  branch: main
                  path: ../../etc/passwd
              -
                type: url
                name: Elsewhere
                url: https://example.com/x.stix2
        """.trimIndent()
        val sources = MvtIndicators.parseIndex(yaml)
        assertEquals(1, sources.size)
        assertEquals("https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/intellexa_predator/predator.stix2", sources[0].url)
    }

    @Test
    fun `stix - paquets, certificats et domaines valides, plateformes ecartees`() {
        val stix = """
            {"type":"bundle","objects":[
              {"type":"indicator","pattern":"[app:id='com.serv.services']"},
              {"type":"indicator","pattern":"[app:id='not a package']"},
              {"type":"indicator","pattern":"[app:cert.sha256='35926E966186C8F2DEEC66864389D73989C75C85CB6668DA2F455DB6FE67E91F']"},
              {"type":"indicator","pattern":"[domain-name:value='Spy-C2.example.']"},
              {"type":"indicator","pattern":"[domain-name:value='evil.github.io']"},
              {"type":"indicator","pattern":"[ipv4-addr:value='1.2.3.4']"},
              {"type":"malware","name":"x"}
            ]}
        """
        val campaign = MvtIndicators.parseStix("NoviSpy (Serbia) Indicators of Compromise", stix)
        assertEquals("NoviSpy", campaign.name)
        assertEquals(listOf("com.serv.services"), campaign.packages)
        assertEquals(listOf("35926e966186c8f2deec66864389d73989c75c85cb6668da2f455db6fe67e91f"), campaign.certificates)
        assertEquals(listOf("spy-c2.example"), campaign.domains)
        assertEquals("Morpheus", MvtIndicators.shortName("Morpheus Indicators of Compromise"))
    }

    @Test
    fun `fichier telecharge - relu au format de la base embarquee`() {
        val json = MvtIndicators.toJson(listOf(MvtIndicators.Campaign("NoviSpy", listOf("com.serv.services"), domains = listOf("spy-c2.example"))))
        val loaded = MvtDatabase.parse(json)
        assertEquals("NoviSpy", loaded.iocs.match(scan("com.serv.services")))
        assertEquals(mapOf("spy-c2.example" to "NoviSpy"), loaded.domains)
        assertEquals(1, loaded.total)
    }
}
