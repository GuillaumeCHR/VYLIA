package com.vylia.privacy.domain.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Lecture des indicateurs MVT téléchargés depuis les Paramètres, mêmes règles que tools/update_mvt_iocs.py :
 * `indicators.yaml` donne la liste des campagnes, chacune un fichier STIX2 hébergé sur GitHub ; de chaque campagne
 * (sauf celle des stalkerwares, déjà couverte par Echap) VYLIA garde les noms de paquet, les empreintes de certificat
 * et les noms de domaine. Tout est traité comme non fiable : une valeur mal formée est écartée.
 */
object MvtIndicators {

    const val INDEX_URL = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/indicators.yaml"

    /** Une campagne de l'index : son nom et l'adresse de son fichier STIX2 (toujours raw.githubusercontent.com). */
    data class Source(val name: String, val url: String)

    @Serializable
    data class Campaign(
        val name: String,
        val packages: List<String> = emptyList(),
        val certificates: List<String> = emptyList(),
        val domains: List<String> = emptyList(),
    )

    @Serializable
    private data class File(val source: String, val url: String, val commit: String = "", val campaigns: List<Campaign>)

    private const val MAX_CAMPAIGNS = 100
    private const val MAX_PER_CAMPAIGN = 20_000
    private val SKIPPED_OWNERS = setOf("AssoEchap")
    private val PATH_PART = Regex("[A-Za-z0-9._-]{1,100}")
    private val PATH = Regex("[A-Za-z0-9._/-]{1,300}")
    private val PATTERN = Regex("""^\[\s*([\w\-]+:[\w.\-]+)\s*=\s*'([^']*)'\s*]$""")
    private val CERTIFICATE = Regex("[0-9a-f]{40}|[0-9a-f]{64}")
    private val DOMAIN = Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

    /** Nom affiché (fiche, alerte, bouclier) : les mêmes noms courts que l'outil, sinon le nom de l'index raccourci. */
    private val SHORT_NAMES = mapOf(
        "NSO Group Pegasus Indicators of Compromise" to "Pegasus (NSO Group)",
        "Predator Spyware Indicators of Compromise" to "Predator (Intellexa)",
        "RCS Lab Spyware Indicators of Compromise" to "Hermit (RCS Lab)",
        "Surveillance campaign linked to mercenary spyware company" to "Mercenary spyware (Amnesty, 2023)",
        "Quadream KingSpawn Indicators of Compromise" to "KingSpawn (QuaDream)",
        "WyrmSpy and DragonEgg Indicators of Compromise" to "WyrmSpy / DragonEgg",
        "Wintego Helios Indicators of Compromise" to "Helios (Wintego)",
        "NoviSpy (Serbia) Indicators of Compromise" to "NoviSpy",
        "Candiru (DevilsTongue) Indicators of Compromise" to "DevilsTongue (Candiru)",
    )

    /**
     * Plateformes jamais bloquées, ni leurs sous-domaines : un hébergement compromis chez l'une d'elles ne doit pas
     * couper le service entier. Même liste que l'outil.
     */
    private val NEVER_BLOCK = setOf(
        "google.com", "googleapis.com", "gstatic.com", "youtube.com", "android.com",
        "facebook.com", "fbcdn.net", "instagram.com", "whatsapp.net", "whatsapp.com",
        "apple.com", "icloud.com", "microsoft.com", "live.com", "amazon.com", "amazonaws.com",
        "cloudfront.net", "akamaihd.net", "akamaized.net", "cloudflare.com", "cloudflare.net",
        "github.com", "githubusercontent.com", "twitter.com", "x.com", "tiktok.com", "yahoo.com", "qq.com", "baidu.com",
        "github.io", "blogspot.com", "herokuapp.com", "azurewebsites.net", "web.app", "firebaseapp.com", "appspot.com",
        "workers.dev", "pages.dev", "netlify.app", "vercel.app",
    )

    fun shortName(name: String): String = SHORT_NAMES[name]
        ?: Untrusted.text(name.removeSuffix(" Indicators of Compromise").removeSuffix(" Spyware"), 80)

    fun isProtected(domain: String): Boolean = NEVER_BLOCK.any { domain == it || domain.endsWith(".$it") }

    /** Les campagnes de `indicators.yaml` (format simple et stable, lu sans bibliothèque YAML). */
    fun parseIndex(yaml: String): List<Source> = yaml.split("\n  -\n").drop(1).take(MAX_CAMPAIGNS).mapNotNull { block ->
        fun field(key: String) = Regex("""^\s+$key: (.+)$""", RegexOption.MULTILINE).find(block)?.groupValues?.get(1)?.trim()
        val name = field("name") ?: return@mapNotNull null
        val owner = field("owner") ?: return@mapNotNull null
        val repo = field("repo") ?: return@mapNotNull null
        val branch = field("branch") ?: return@mapNotNull null
        val path = field("path") ?: return@mapNotNull null
        if (field("type") != "github" || owner in SKIPPED_OWNERS) return@mapNotNull null
        if (!listOf(owner, repo, branch).all(PATH_PART::matches) || !PATH.matches(path) || ".." in path) return@mapNotNull null
        Source(name, "https://raw.githubusercontent.com/$owner/$repo/$branch/$path")
    }

    /** Les indicateurs Android d'un fichier STIX2. */
    fun parseStix(name: String, content: String): Campaign {
        val packages = sortedSetOf<String>()
        val certificates = sortedSetOf<String>()
        val domains = sortedSetOf<String>()
        val objects = (Json.parseToJsonElement(content).jsonObject["objects"] as? JsonArray).orEmpty()
        for (element in objects) {
            val obj = element as? JsonObject ?: continue
            if ((obj["type"] as? JsonPrimitive)?.content != "indicator") continue
            val pattern = (obj["pattern"] as? JsonPrimitive)?.content?.trim() ?: continue
            val match = PATTERN.find(pattern) ?: continue
            val value = match.groupValues[2].trim()
            when (match.groupValues[1]) {
                "app:id" -> if (Untrusted.isPackage(value)) packages += value
                "app:cert.sha1", "app:cert.sha256" -> value.lowercase().replace(":", "").takeIf(CERTIFICATE::matches)?.let { certificates += it }
                "domain-name:value" -> value.lowercase().trimEnd('.').takeIf { DOMAIN.matches(it) && !isProtected(it) }?.let { domains += it }
            }
            if (packages.size + certificates.size + domains.size >= MAX_PER_CAMPAIGN) break
        }
        return Campaign(shortName(name), packages.toList(), certificates.toList(), domains.toList())
    }

    /** Le fichier enregistré sur le téléphone, au même format que `assets/mvt_iocs.json`. */
    fun toJson(campaigns: List<Campaign>): String = Json.encodeToString(
        File.serializer(),
        File(source = "MVT, mvt-indicators", url = "https://github.com/mvt-project/mvt-indicators", campaigns = campaigns),
    )

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this?.jsonArray ?: emptyList()
}
