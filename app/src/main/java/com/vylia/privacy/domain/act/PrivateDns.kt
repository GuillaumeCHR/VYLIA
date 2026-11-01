package com.vylia.privacy.domain.act

import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Services de DNS privé proposés par le guide (assets/private_dns.json, ou sa dernière version téléchargée à votre
 * demande). La liste est une donnée, pas du code : un service peut être masqué (`available`) ou remplacé sans mise à
 * jour de l'app.
 */
@Serializable
data class PrivateDnsCatalogue(
    val format: String,
    val version: Int,
    val updated: String = "",
    val resolvers: List<DnsResolver>,
) {
    /** Services proposés dans ce pays (code ISO de la région du téléphone), le recommandé d'abord. */
    fun availableIn(country: String): List<DnsResolver> =
        resolvers.filter { it.available && country.uppercase() !in it.unavailableCountries.map(String::uppercase) }
            .sortedByDescending { it.recommended }

    companion object {
        const val FORMAT = "vylia-private-dns"
        const val MAX_BYTES = 64 * 1024
    }
}

@Serializable
data class DnsResolver(
    val id: String,
    val name: String,
    val recommended: Boolean = false,
    val available: Boolean = true,
    /** Nom d'hôte fixe, à saisir tel quel dans Android. */
    val hostname: String? = null,
    /** Nom d'hôte propre à chaque compte, ex. `{id}.dns.nextdns.io` ; l'identifiant est saisi par l'utilisateur. */
    @SerialName("hostname_template") val hostnameTemplate: String? = null,
    /** Forme attendue de l'identifiant de compte (expression régulière, sans ancres). */
    @SerialName("id_pattern") val idPattern: String? = null,
    @SerialName("requires_account") val requiresAccount: Boolean = false,
    val website: String,
    val pricing: String? = null,
    @SerialName("privacy_policy") val privacyPolicy: String,
    @SerialName("unavailable_countries") val unavailableCountries: List<String> = emptyList(),
    /** Textes par langue (« en », « fr ») ; l'anglais sert de repli. */
    val summary: Map<String, String>,
    val points: Map<String, List<String>> = emptyMap(),
) {
    /**
     * Nom d'hôte à saisir dans Android : le nom fixe, ou celui construit depuis ce que l'utilisateur a collé
     * (identifiant seul, ou nom d'hôte complet de son compte). `null` si la saisie ne correspond pas.
     */
    fun hostnameFor(input: String = ""): String? {
        hostname?.let { return it }
        val template = hostnameTemplate ?: return null
        val pattern = Regex(idPattern ?: return null)
        val text = input.trim().lowercase()
        val suffix = template.substringAfter("{id}")
        val id = if (text.endsWith(suffix)) text.removeSuffix(suffix) else text
        return if (pattern.matches(id)) template.replace("{id}", id) else null
    }

    fun localizedPoints(language: String): List<String> = points[language] ?: points["en"].orEmpty()
}

/**
 * Contrôle d'une liste de services, avant usage : elle peut venir d'Internet. Format reconnu, noms d'hôtes et liens
 * plausibles (HTTPS seulement), identifiants de compte bornés, textes nettoyés. Un service douteux est écarté ; une liste
 * sans aucun service valide est refusée.
 */
object PrivateDnsValidator {
    private val HOSTNAME = Regex("(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
    private val ID_PATTERN = Regex("[a-z0-9\\[\\]{},\\-]{1,40}")
    private const val MAX_RESOLVERS = 10

    fun validate(catalogue: PrivateDnsCatalogue): PrivateDnsCatalogue {
        require(catalogue.format == PrivateDnsCatalogue.FORMAT) { "liste de services DNS inconnue" }
        val resolvers = catalogue.resolvers.take(MAX_RESOLVERS).mapNotNull(::clean)
        require(resolvers.isNotEmpty()) { "aucun service DNS valide" }
        return catalogue.copy(resolvers = resolvers)
    }

    private fun clean(r: DnsResolver): DnsResolver? {
        if (!Regex("[a-z0-9_]{1,30}").matches(r.id)) return null
        val links = listOfNotNull(r.website, r.pricing, r.privacyPolicy)
        if (!links.all(::isHttps)) return null
        val fixed = r.hostname
        val template = r.hostnameTemplate
        when {
            fixed != null -> if (!HOSTNAME.matches(fixed)) return null
            template != null -> {
                val pattern = r.idPattern ?: return null
                // Le modèle doit donner un nom d'hôte valide, et le motif rester simple (pas de quantificateurs imbriqués).
                if (template.count { it == '{' } != 1 || !HOSTNAME.matches(template.replace("{id}", "abc123"))) return null
                if (!ID_PATTERN.matches(pattern) || runCatching { Regex(pattern) }.isFailure) return null
            }
            else -> return null
        }
        if (r.summary.isEmpty()) return null
        return r.copy(
            name = Untrusted.text(r.name, 40),
            summary = r.summary.mapValues { Untrusted.text(it.value, 300) },
            points = r.points.mapValues { (_, list) -> list.take(6).map { Untrusted.text(it, 200) } },
            unavailableCountries = r.unavailableCountries.filter { Regex("[A-Za-z]{2}").matches(it) },
        )
    }

    private fun isHttps(url: String) = url.length <= 300 && url.startsWith("https://") && ' ' !in url
}
