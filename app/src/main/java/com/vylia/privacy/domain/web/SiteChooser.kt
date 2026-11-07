package com.vylia.privacy.domain.web

import java.text.Normalizer

/**
 * Choisit le site d'une app parmi les domaines web qu'elle déclare (ses liens d'app, lus par Android, sans réseau).
 * Les domaines techniques (redirection, hébergement, test) sont écartés ; les autres sont regroupés par domaine
 * principal (www.doctolib.fr et a.doctolib.fr : doctolib.fr). Le groupe retenu est celui dont le nom ressemble à
 * l'app (identifiant ou nom affiché), sinon le plus fourni parmi les domaines vérifiés par Android : un domaine non
 * vérifié qui ne ressemble pas à l'app peut appartenir à un autre service. À égalité, le pays de l'appareil, puis
 * les extensions courantes (.com, .org…).
 */
object SiteChooser {

    /** Un domaine déclaré, et s'il est vérifié (le site confirme que l'app lui appartient). */
    data class Host(val name: String, val verified: Boolean)

    private val TECHNICAL_SUFFIXES = listOf(
        "firebaseapp.com", "web.app", "appspot.com", "azurewebsites.net", "googleusercontent.com", "page.link",
        "app.link", "go.link", "onelink.me", "branch.io", "adj.st", "test-app.link", "cloudfront.net", "amazonaws.com",
    )
    private val TECHNICAL_WORDS = Regex("(^|[.-])(test|staging|stage|preprod|dev|qa|sandbox|beta|uat)\\d*([.-]|$)")
    private val HOST = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
    // Suffixes à deux niveaux courants : le domaine principal de www.bbc.co.uk est bbc.co.uk.
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "ac", "gouv")

    /** Adresse https du site retenu, ou `null` si aucun domaine ne convient. */
    fun choose(packageName: String, label: String, hosts: List<Host>, country: String): String? {
        val names = namesOf(packageName, label)
        val groups = hosts.mapNotNull { h -> h.name.lowercase().removePrefix("*.").takeIf { HOST.matches(it) }?.let { Host(it, h.verified) } }
            .filterNot { isInfrastructure(it.name) }
            .groupBy { registrable(it.name) }
            .map { (domain, members) ->
                Group(domain, members.filterNot { isTechnical(it.name, domain) }, members.count { it.verified }, domain.substringBefore('.') in names)
            }
            // Un groupe sans adresse utile n'est gardé que s'il porte le nom de l'app (on ouvre alors www.domaine) ;
            // un groupe non vérifié, que s'il porte le nom de l'app.
            .filter { g -> g.matches || (g.useful.isNotEmpty() && g.useful.any { it.verified }) }
        val best = groups.sortedWith(
            compareByDescending<Group> { it.useful.isNotEmpty() }
                .thenByDescending { it.matches }
                // Une page d'accueil (www.domaine ou domaine) plutôt qu'un sous-domaine de service.
                .thenByDescending { g -> g.useful.any { it.name == g.domain || it.name == "www.${g.domain}" } }
                .thenByDescending { it.verified }
                .thenBy { tldRank(it.domain, country) }
                .thenBy { it.domain },
        ).firstOrNull() ?: return null
        val all = best.useful.map { it.name }.toSet()
        val host = listOf("www.${best.domain}", best.domain, "m.${best.domain}").firstOrNull { it in all }
            ?: best.useful.sortedWith(compareByDescending<Host> { it.verified }.thenBy { it.name.length }.thenBy { it.name }).firstOrNull()?.name
            ?: "www.${best.domain}"
        return "https://$host/"
    }

    private class Group(val domain: String, val useful: List<Host>, val verified: Int, val matches: Boolean)

    /** Domaine principal : les deux derniers niveaux, trois pour un suffixe comme co.uk. */
    fun registrable(host: String): String {
        val parts = host.split('.')
        val keep = if (parts.size >= 3 && parts.last().length == 2 && parts[parts.size - 2] in SECOND_LEVEL) 3 else 2
        return parts.takeLast(keep).joinToString(".")
    }

    /** Les noms qui désignent l'app : les éléments de son identifiant et son nom affiché, sans accents ni espaces. */
    fun namesOf(packageName: String, label: String): Set<String> {
        val plain = Normalizer.normalize(label, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        val words = plain.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val fromLabel = listOfNotNull(words.joinToString(""), words.firstOrNull())
        val fromPackage = packageName.lowercase().split('.').drop(1)
        return (fromLabel + fromPackage).filter { it.length >= 3 && it !in GENERIC }.toSet()
    }

    private val COMMON_TLDS = listOf("com", "org", "net", "eu", "io", "app")

    private fun tldRank(domain: String, country: String): Int {
        val tld = domain.substringAfterLast('.')
        return if (tld == country.lowercase()) 0 else COMMON_TLDS.indexOf(tld).let { if (it < 0) COMMON_TLDS.size + 1 else it + 1 }
    }

    private val GENERIC = setOf("android", "app", "apps", "mobile", "www", "client", "main", "prod", "release")

    /** Domaines d'hébergement ou de redirection : jamais le site du service. */
    private fun isInfrastructure(host: String): Boolean = TECHNICAL_SUFFIXES.any { host == it || host.endsWith(".$it") }

    /** Adresse qui n'est pas une page d'accueil : test, connexion, redirection, suivi d'e-mails… */
    private fun isTechnical(host: String, domain: String): Boolean {
        val sub = host.removeSuffix(domain).removeSuffix(".")
        return TECHNICAL_WORDS.containsMatchIn(host) || sub.split('.').any { it in TECHNICAL_LABELS }
    }

    private val TECHNICAL_LABELS = setOf(
        "login", "auth", "sso", "s", "t", "u", "account", "accounts", "oauth", "signin", "id", "r", "l", "link", "links", "lnk", "click",
        "email", "mail", "e", "engage", "track", "tracking", "go", "api", "cdn", "static", "share", "deeplink", "redirect",
    )
}
