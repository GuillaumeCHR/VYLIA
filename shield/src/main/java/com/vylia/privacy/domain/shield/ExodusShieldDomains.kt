package com.vylia.privacy.domain.shield

/** Un traqueur Exodus, transmis par l'app : son nom, ses catégories (complétées) et sa `network_signature`. */
data class ExodusNetworkTracker(val name: String, val categories: List<String>, val networkSignature: String)

/**
 * Domaines nommés du bouclier, tirés de la base Exodus que l'app télécharge, sur le modèle de exodus_domains() dans
 * tools/update_shield_blocklist.py : la liste suit ainsi la base Exodus sans attendre une nouvelle version de l'app.
 *
 * - Seuls les traqueurs de catégorie Advertisement, Analytics, Profiling ou Identification sont bloqués ; un SDK
 *   seulement Location (cartes) ou Crash reporting ne l'est pas. Advertisement → filtrage mobile, le reste → web.
 * - Une signature qui n'est pas un nom de domaine (expression régulière, adresse IP, mot seul) est écartée.
 * - [EXTRA] : complément maison, domaine par domaine, pour ce qu'aucune liste ne couvre.
 */
object ExodusShieldDomains {

    private val BLOCKED = setOf("Advertisement", "Analytics", "Profiling", "Identification")

    /**
     * Complément maison, identique à EXTRA dans tools/update_shield_blocklist.py : nom affiché → (filtrage, domaines).
     * Google Analytics for Firebase : mesure d'audience des apps ; le bloquer n'empêche ni la connexion, ni les
     * notifications, ni la base de données Firebase.
     */
    val EXTRA: Map<String, Pair<ShieldFilter, List<String>>> = mapOf(
        "Google Firebase Analytics" to (ShieldFilter.WEB to listOf("app-measurement.com")),
    )

    /**
     * Serveurs connus de traqueurs dont la `network_signature` Exodus manque ou ne sert pas (expression régulière,
     * domaine de plateforme) : ils ne servent qu'à dire ce que le bouclier bloque de ces traqueurs (score, fiche).
     * Jamais ajoutés à la liste de blocage : le bouclier bloque exactement la même chose avec ou sans eux. Les serveurs
     * des régies publicitaires ne sont pas bloqués (pas de filtrage mobile dans cette version : le score les compte en
     * entier), les autres le sont déjà par les listes (vérifié par ExodusShieldDomainsTest) ; un traqueur cité ici n'est pas réduit à
     * ces serveurs, sa signature Exodus compte toujours (Facebook Ads reste bloqué en partie : `facebook.com` passe).
     */
    val KNOWN_SERVERS: Map<String, List<String>> = mapOf(
        "Amazon Advertisement" to listOf("amazon-adsystem.com"),
        "Pangle" to listOf("pangle.io"),
        "Appodeal Stack" to listOf("appodeal.com"),
        "BidMachine" to listOf("bidmachine.io"),
        "ironSource" to listOf("supersonicads.com"),
        "Singular" to listOf("sdk-api-v1.singular.net"),
        "Adobe Experience Cloud" to listOf("omtrdc.net", "demdex.net"),
        "Nielsen" to listOf("imrworldwide.com"),
        // Signature Exodus « adservice\.google\..* » (expression régulière) : son domaine principal.
        "Google AdMob" to listOf("adservice.google.com"),
        // Audience Network, la régie de Facebook ; graph.facebook.com sert aussi à la connexion et n'est pas bloqué.
        "Facebook Ads" to listOf("an.facebook.com"),
        "Facebook Audience" to listOf("an.facebook.com"),
    )

    fun named(trackers: List<ExodusNetworkTracker>): Map<String, Pair<String, ShieldFilter>> {
        val named = LinkedHashMap<String, Pair<String, ShieldFilter>>()
        trackers.forEach { tracker ->
            if (tracker.categories.none { it in BLOCKED }) return@forEach
            val filter = if ("Advertisement" in tracker.categories) ShieldFilter.MOBILE else ShieldFilter.WEB
            val name = visibleText(tracker.name, 100)
            tracker.networkSignature.split('|').filter { it.isNotEmpty() }.forEach { part ->
                val domain = signatureDomain(part)
                if (domain != null && domain !in BlocklistBuilder.NEVER_BLOCK) named.putIfAbsent(domain, name to filter)
            }
        }
        EXTRA.forEach { (name, value) -> value.second.forEach { named.putIfAbsent(it, name to value.first) } }
        return named
    }
}
