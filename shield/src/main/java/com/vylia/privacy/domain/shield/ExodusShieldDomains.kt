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

    fun named(trackers: List<ExodusNetworkTracker>): Map<String, Pair<String, ShieldFilter>> {
        val named = LinkedHashMap<String, Pair<String, ShieldFilter>>()
        trackers.forEach { tracker ->
            if (tracker.categories.none { it in BLOCKED }) return@forEach
            val filter = if ("Advertisement" in tracker.categories) ShieldFilter.MOBILE else ShieldFilter.WEB
            val name = visibleText(tracker.name, 100)
            tracker.networkSignature.split('|').filter { it.isNotEmpty() }.forEach { part ->
                val domain = part.replace("\\.", ".").replace("\\-", "-").lowercase()
                    .removePrefix(".*").removePrefix(".")
                if (Blocklist.DOMAIN.matches(domain) && domain !in BlocklistBuilder.NEVER_BLOCK) named.putIfAbsent(domain, name to filter)
            }
        }
        EXTRA.forEach { (name, value) -> value.second.forEach { named.putIfAbsent(it, name to value.first) } }
        return named
    }
}
