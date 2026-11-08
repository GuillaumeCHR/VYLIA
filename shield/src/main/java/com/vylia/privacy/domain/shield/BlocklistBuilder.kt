package com.vylia.privacy.domain.shield

/**
 * Fusion des listes téléchargées depuis l'app, sur le modèle de tools/update_shield_blocklist.py (qui produit la liste
 * embarquée) : mêmes groupes, mêmes protections, même dédoublonnage.
 *
 * - Les traqueurs nommés (Exodus, complément maison) viennent de la base Exodus téléchargée par l'app ([exodus],
 *   voir [ExodusShieldDomains]), sinon de la liste embarquée, et gardent leur nom.
 * - Groupe mobile : les listes de rôle [ShieldFilter.MOBILE] (AdGuard Mobile Ads) et les traqueurs nommés mobiles ;
 *   un domaine des deux groupes est mobile (c'est le réglage mobile qui décide pour lui).
 * - Les exceptions `@@||domaine^` de chaque liste s'appliquent à toutes les sources, sous-domaines compris, même si un
 *   domaine parent est listé ; [NEVER_BLOCK] retire aussi ses domaines des listes.
 * - Un sous-domaine d'un domaine déjà présent est retiré (la recherche se fait par suffixe).
 * - Les domaines non nommés de la liste embarquée ne servent que si une liste active n'a pas encore été téléchargée
 *   ([fallback]) : ils viennent des mêmes listes, dans une version plus ancienne.
 */
object BlocklistBuilder {

    /** Une liste analysée et le groupe dans lequel ranger ses domaines. */
    data class Source(val filter: ShieldFilter, val list: ParsedList)

    /**
     * Domaines de plateformes jamais bloqués, ni eux ni leurs sous-domaines déclarés en entier : bloquer l'un d'eux
     * casserait des apps entières. Même liste que NEVER_BLOCK dans tools/update_shield_blocklist.py.
     */
    val NEVER_BLOCK = setOf(
        "google.com", "googleapis.com", "gstatic.com", "youtube.com", "android.com",
        "facebook.com", "fbcdn.net", "instagram.com", "whatsapp.net", "whatsapp.com",
        "apple.com", "icloud.com", "microsoft.com", "live.com", "amazon.com", "amazonaws.com",
        "cloudfront.net", "akamaihd.net", "akamaized.net", "cloudflare.com", "cloudflare.net",
        "github.com", "githubusercontent.com", "twitter.com", "x.com", "tiktok.com", "yahoo.com", "qq.com", "baidu.com",
    )

    data class Result(val blocklist: Blocklist, val web: Int, val mobile: Int, val exceptions: Int)

    fun build(
        embedded: Blocklist.Snapshot,
        sources: List<Source>,
        fallback: Boolean,
        exodus: Map<String, Pair<String, ShieldFilter>>? = null,
    ): Result {
        val exceptions = HashSet<String>()
        sources.forEach { exceptions += it.list.exceptions }
        if (fallback) exceptions += embedded.allow
        fun allowed(domain: String) = domain !in exceptions && domain !in NEVER_BLOCK

        val named = (exodus ?: embedded.named).filterKeys(::allowed)
        val mobileAll = HashSet<String>()
        val webAll = HashSet<String>()
        named.forEach { (domain, value) -> if (value.second == ShieldFilter.MOBILE) mobileAll += domain else webAll += domain }
        if (fallback) {
            embedded.mobile.filterTo(mobileAll, ::allowed)
            embedded.web.filterTo(webAll, ::allowed)
        }
        sources.forEach { source ->
            val target = if (source.filter == ShieldFilter.MOBILE) mobileAll else webAll
            source.list.domains.filterTo(target, ::allowed)
        }
        webAll.removeAll(mobileAll)

        val mobile = dropRedundant(mobileAll, emptySet())
        // Un domaine web sous un domaine mobile suit déjà le réglage mobile : inutile de le garder.
        val web = dropRedundant(webAll, mobileAll)
        require(web.size + mobile.size <= Blocklist.MAX_RULES) { "liste trop grande (${web.size + mobile.size} domaines)" }
        val names = named.filterKeys { it in web || it in mobile }.mapValues { it.value.first }
        return Result(
            Blocklist(DomainHashSet.of(web), DomainHashSet.of(mobile), names, embedded.spyware, DomainHashSet.of(exceptions)),
            web = web.size,
            mobile = mobile.size,
            exceptions = exceptions.size,
        )
    }

    private fun dropRedundant(domains: Set<String>, alsoCoveredBy: Set<String>): Set<String> =
        domains.filterTo(HashSet()) { domain -> parents(domain).none { it in domains || it in alsoCoveredBy } }

    /** Domaines parents, sans le TLD seul : `a.b.example.com` → `b.example.com`, `example.com`. */
    private fun parents(domain: String): Sequence<String> = sequence {
        var rest = domain
        while (true) {
            val dot = rest.indexOf('.')
            if (dot < 0) return@sequence
            rest = rest.substring(dot + 1)
            if (rest.indexOf('.') < 0) return@sequence
            yield(rest)
        }
    }
}
