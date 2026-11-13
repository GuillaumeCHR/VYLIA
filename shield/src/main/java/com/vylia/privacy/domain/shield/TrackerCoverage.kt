package com.vylia.privacy.domain.shield

/** Ce que le bouclier bloque des serveurs connus d'un traqueur. */
enum class TrackerCoverage {
    /** Tous ses domaines connus sont bloqués avec les filtrages actifs. */
    BLOCKED,

    /** Une partie seulement : le reste (domaine de plateforme, adresse IP, filtrage coupé) passe. */
    PARTIAL,

    /** Aucun de ses domaines connus n'est bloqué. */
    NONE,

    /** Aucun domaine connu (Exodus ne donne pas de signature réseau) : le bouclier ne sait pas l'atteindre. */
    UNKNOWN,
}

/**
 * Couverture des traqueurs par le bouclier, d'après leur `network_signature` Exodus, le complément maison
 * ([ExodusShieldDomains.EXTRA]) et les serveurs connus ([ExodusShieldDomains.KNOWN_SERVERS]). Une partie de signature qui n'est pas un nom de domaine (adresse IP, expression
 * régulière) compte comme un serveur que le bouclier laisse passer : un SDK qui vise une IP lui échappe.
 */
object TrackerCoverages {

    /**
     * @return la couverture de chaque traqueur de [trackers] (et de ceux du complément maison), par nom. Un traqueur
     *   absent du résultat n'a aucun domaine connu : [TrackerCoverage.UNKNOWN].
     */
    fun of(trackers: List<ExodusNetworkTracker>, blocklist: Blocklist, filters: Set<ShieldFilter>): Map<String, TrackerCoverage> {
        // Domaines de chaque traqueur ; null : une partie de signature qui n'est pas un domaine.
        val parts = LinkedHashMap<String, MutableSet<String?>>()
        trackers.forEach { tracker ->
            val set = parts.getOrPut(visibleText(tracker.name, 100)) { LinkedHashSet() }
            tracker.networkSignature.split('|').filter { it.isNotEmpty() }.mapTo(set, ::signatureDomain)
        }
        ExodusShieldDomains.EXTRA.forEach { (name, value) -> parts.getOrPut(name) { LinkedHashSet() } += value.second }
        ExodusShieldDomains.KNOWN_SERVERS.forEach { (name, domains) ->
            // Ces serveurs remplacent les parties de signature qui ne sont pas des domaines (expression régulière).
            parts.getOrPut(name) { LinkedHashSet() }.apply { remove(null); addAll(domains) }
        }
        return parts.filterValues { it.isNotEmpty() }.mapValues { (_, domains) ->
            val blocked = domains.count { it != null && blocklist.match(it, filters) != null }
            when (blocked) {
                domains.size -> TrackerCoverage.BLOCKED
                0 -> TrackerCoverage.NONE
                else -> TrackerCoverage.PARTIAL
            }
        }
    }

    fun of(name: String, coverage: Map<String, TrackerCoverage>): TrackerCoverage = coverage[name] ?: TrackerCoverage.UNKNOWN
}

/**
 * Domaine d'une partie de `network_signature` (`\.flurry\.com`, `.*\.ads\.com`), `null` si ce n'en est pas un
 * (adresse IP, expression régulière).
 */
internal fun signatureDomain(part: String): String? =
    part.replace("\\.", ".").replace("\\-", "-").lowercase().removePrefix(".*").removePrefix(".")
        .takeIf { Blocklist.DOMAIN.matches(it) }
