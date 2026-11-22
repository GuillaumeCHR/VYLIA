package com.vylia.privacy.domain.shield

/**
 * Domaines que l'utilisateur ne veut plus bloquer (« Ne plus bloquer » dans les derniers blocages) : ni ce domaine ni
 * ses sous-domaines ne sont bloqués par les listes. Une exception ne vaut jamais pour un logiciel espion ([cover]).
 */
class ShieldExceptions private constructor(val domains: Set<String>) {

    /** [domain] ou l'un de ses domaines parents est une exception. */
    fun covers(domain: String): Boolean {
        if (domains.isEmpty()) return false
        var name = domain.trimEnd('.').lowercase()
        while (true) {
            if (name in domains) return true
            val dot = name.indexOf('.')
            if (dot < 0 || name.indexOf('.', dot + 1) < 0) return false
            name = name.substring(dot + 1)
        }
    }

    /**
     * Le blocage [match] de [domain], sauf si une exception le lève. Les logiciels espions (filtrage `null`) restent
     * bloqués quoi qu'il arrive.
     */
    fun cover(domain: String, match: BlockMatch?): BlockMatch? =
        if (match != null && match.filter != null && covers(domain)) null else match

    /** Avec [domain] en plus ; inchangé si le domaine est invalide ou si la limite est atteinte. */
    operator fun plus(domain: String): ShieldExceptions {
        val name = domain.trimEnd('.').lowercase()
        if (!Blocklist.DOMAIN.matches(name) || domains.size >= MAX) return this
        return ShieldExceptions(domains + name)
    }

    operator fun minus(domain: String): ShieldExceptions = ShieldExceptions(domains - domain.trimEnd('.').lowercase())

    override fun equals(other: Any?): Boolean = other is ShieldExceptions && other.domains == domains

    override fun hashCode(): Int = domains.hashCode()

    companion object {
        /** De quoi couvrir quelques apps capricieuses, pas de quoi vider la liste. */
        const val MAX = 200
        val EMPTY = ShieldExceptions(emptySet())

        /** Relit les exceptions enregistrées ; les domaines invalides sont écartés. */
        fun of(domains: Collection<String>): ShieldExceptions = domains.fold(EMPTY) { acc, d -> acc + d }
    }
}
