package com.vylia.privacy.domain.databases

import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rôle d'un domaine selon AdGuard companiesdb, sous un nom neutre : VYLIA parle de traqueurs, de ciblage, de mesure
 * d'audience, jamais de publicité, et ne porte aucun jugement sur le contenu (« pornvertising » est du ciblage comme
 * un autre).
 */
enum class CompanyCategory { TARGETING, AUDIENCE, SOCIAL, TELEMETRY, CONSENT, THIRD_PARTY }

/** Entreprise derrière un domaine : « Criteo · ciblage ». */
data class Company(val name: String, val category: CompanyCategory)

/**
 * Index domaine → entreprise tiré de companiesdb (`trackers.json` et `companies.json`, CC BY-SA 4.0). La recherche se
 * fait par suffixe : `cas.criteo.com` est reconnu par `criteo.com`, jamais par le TLD seul.
 */
class CompaniesLookup(private val byDomain: Map<String, Company>) {

    val size: Int get() = byDomain.size

    fun lookup(domain: String): Company? {
        var name = domain.trimEnd('.').lowercase()
        while (true) {
            byDomain[name]?.let { return it }
            val dot = name.indexOf('.')
            if (dot < 0 || name.indexOf('.', dot + 1) < 0) return null
            name = name.substring(dot + 1)
        }
    }

    /** Index réduit, une ligne par domaine (`domaine\tentreprise\tcatégorie`), gardé compressé sur le téléphone. */
    fun toLines(): Sequence<String> = byDomain.entries.asSequence().sortedBy { it.key }
        .map { (domain, company) -> "$domain\t${company.name}\t${company.category.name}" }

    @Serializable
    private data class TrackersFile(
        val categories: Map<String, String> = emptyMap(),
        val trackers: Map<String, TrackerEntry> = emptyMap(),
        val trackerDomains: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class TrackerEntry(
        val name: String = "",
        val categoryId: Int? = null,
        @SerialName("companyId") val companyId: String? = null,
    )

    @Serializable
    private data class CompaniesFile(val companies: Map<String, CompanyEntry> = emptyMap())

    @Serializable
    private data class CompanyEntry(val name: String = "")

    companion object {
        val EMPTY = CompaniesLookup(emptyMap())
        private const val MAX_DOMAINS = 50_000
        private val DOMAIN = Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        private val json = Json { ignoreUnknownKeys = true }

        fun category(key: String?): CompanyCategory = when (key) {
            "advertising", "pornvertising" -> CompanyCategory.TARGETING
            "site_analytics", "mobile_analytics" -> CompanyCategory.AUDIENCE
            "social_media" -> CompanyCategory.SOCIAL
            "telemetry" -> CompanyCategory.TELEMETRY
            "consent" -> CompanyCategory.CONSENT
            else -> CompanyCategory.THIRD_PARTY
        }

        /** Construit l'index à partir des deux fichiers de companiesdb ; les entrées invalides sont écartées. */
        fun parse(trackersJson: String, companiesJson: String): CompaniesLookup {
            val trackers = json.decodeFromString<TrackersFile>(trackersJson.removePrefix("\uFEFF"))
            val companies = json.decodeFromString<CompaniesFile>(companiesJson.removePrefix("\uFEFF")).companies
            val byDomain = HashMap<String, Company>()
            trackers.trackerDomains.entries.asSequence().take(MAX_DOMAINS).forEach { (rawDomain, trackerId) ->
                val domain = rawDomain.lowercase()
                if (!DOMAIN.matches(domain)) return@forEach
                val tracker = trackers.trackers[trackerId] ?: return@forEach
                val name = tracker.companyId?.let { companies[it]?.name }?.takeIf { it.isNotBlank() } ?: tracker.name
                if (name.isBlank()) return@forEach
                val key = tracker.categoryId?.let { trackers.categories[it.toString()] }
                byDomain[domain] = Company(Untrusted.text(name, 60), category(key))
            }
            return CompaniesLookup(byDomain)
        }

        /** Relit l'index écrit par [toLines]. */
        fun fromLines(lines: Sequence<String>): CompaniesLookup {
            val byDomain = HashMap<String, Company>()
            lines.take(MAX_DOMAINS).forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 3 || !DOMAIN.matches(parts[0])) return@forEach
                val category = CompanyCategory.entries.firstOrNull { it.name == parts[2] } ?: CompanyCategory.THIRD_PARTY
                byDomain[parts[0]] = Company(Untrusted.text(parts[1], 60), category)
            }
            return CompaniesLookup(byDomain)
        }
    }
}
