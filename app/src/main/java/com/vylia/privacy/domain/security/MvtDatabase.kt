package com.vylia.privacy.domain.security

import com.vylia.privacy.domain.scoring.IocDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Logiciels espions de surveillance ciblée (Pegasus, Predator, NoviSpy…), produite par tools/update_mvt_iocs.py
 * depuis les indicateurs publics de MVT (`assets/mvt_iocs.json`), ou mise à jour depuis les Paramètres (même format,
 * voir [MvtIndicators]). Noms de paquet et certificats servent au score ; les domaines vont au bouclier. Lue comme
 * une donnée externe : une entrée mal formée est écartée.
 *
 * Les noms de paquet ne valent que pour une app installée par l'utilisateur : plusieurs imitent un nom système.
 */
object MvtDatabase {
    const val ASSET = "mvt_iocs.json"
    private const val MAX_CAMPAIGNS = 500
    private const val MAX_PER_CAMPAIGN = 20_000
    private const val MAX_DOMAINS = 50_000
    private val CERTIFICATE = Regex("[0-9a-f]{40}|[0-9a-f]{64}")
    private val COMMIT = Regex("[0-9a-f]{7,40}")
    private val DOMAIN = Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

    @Serializable
    private data class File(val commit: String = "", val campaigns: List<MvtIndicators.Campaign> = emptyList())

    /**
     * [campaigns] : campagnes qui ont au moins un indicateur d'app ; [total] : toutes les campagnes ; [domains] :
     * domaine vers nom de campagne, pour le bouclier ; [commit] : version (vide pour une base téléchargée).
     */
    class Loaded(val iocs: IocDatabase, val commit: String, val campaigns: Int, val total: Int, val domains: Map<String, String>)

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): Loaded {
        val file = json.decodeFromString<File>(content)
        val packages = HashMap<String, String>()
        val certificates = HashMap<String, String>()
        val domains = HashMap<String, String>()
        val names = HashSet<String>()
        var total = 0
        val counted = file.campaigns.take(MAX_CAMPAIGNS).count { campaign ->
            val name = Untrusted.text(campaign.name, 80)
            names += name
            val added = campaign.packages.take(MAX_PER_CAMPAIGN).filter(Untrusted::isPackage).count { packages.putIfAbsent(it, name) == null } +
                campaign.certificates.take(MAX_PER_CAMPAIGN).map { it.lowercase() }.filter(CERTIFICATE::matches)
                    .count { certificates.putIfAbsent(it, name) == null }
            val blocked = campaign.domains.take(MAX_PER_CAMPAIGN).map { it.lowercase() }
                .filter { DOMAIN.matches(it) && !MvtIndicators.isProtected(it) && domains.size < MAX_DOMAINS }
                .count { domains.putIfAbsent(it, name) == null }
            if (added > 0 || blocked > 0) total++
            added > 0
        }
        return Loaded(
            IocDatabase(packages = emptyMap(), certificates = certificates, userAppPackages = packages, targeted = names),
            file.commit.takeIf(COMMIT::matches).orEmpty(),
            counted,
            total,
            domains,
        )
    }
}
