package com.vylia.privacy.domain.shield

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Les deux filtrages du bouclier, activables séparément. */
enum class ShieldFilter {
    /** Pubs et pistage des sites, mesure d'audience et profilage des apps. Gêne rarement les apps. */
    WEB,

    /** Régies publicitaires des apps et des jeux : leurs pubs, leur profilage, et les vidéos récompensées. */
    MOBILE,
}

/** Raison d'un blocage : la règle, son filtrage, et le traqueur connu pour elle (`null` : domaine d'une liste). */
data class BlockMatch(val rule: String, val filter: ShieldFilter, val trackerName: String?)

/**
 * Liste de blocage du bouclier : un domaine est bloqué s'il figure dans la liste, ou si l'un de ses domaines parents
 * y figure (`ads.example.com` est bloqué par `example.com`, pas l'inverse), à condition que son filtrage soit actif.
 *
 * Le filtrage mobile l'emporte : un domaine qui relève (lui ou un parent) d'une régie mobile passe quand le filtrage
 * mobile est coupé, même si une liste web le cite aussi. C'est ce qui laisse passer les vidéos récompensées.
 */
class Blocklist(private val web: Map<String, String?>, private val mobile: Map<String, String?>) {

    val size: Int get() = web.size + mobile.size

    fun match(domain: String, enabled: Set<ShieldFilter> = ShieldFilter.entries.toSet()): BlockMatch? {
        var name = domain.trimEnd('.').lowercase()
        var found: BlockMatch? = null
        while (true) {
            if (mobile.containsKey(name)) {
                if (ShieldFilter.MOBILE !in enabled) return null
                if (found == null) found = BlockMatch(name, ShieldFilter.MOBILE, mobile[name])
            } else if (found == null && ShieldFilter.WEB in enabled && web.containsKey(name)) {
                found = BlockMatch(name, ShieldFilter.WEB, web[name])
            }
            val dot = name.indexOf('.')
            // On s'arrête au dernier niveau : un TLD seul (« com ») n'est jamais une règle.
            if (dot < 0 || name.indexOf('.', dot + 1) < 0) return found
            name = name.substring(dot + 1)
        }
    }

    @Serializable
    private data class Group(
        val trackers: Map<String, List<String>> = emptyMap(),
        val domains: List<String> = emptyList(),
    )

    @Serializable
    private data class File(val version: Int, val groups: Map<String, Group> = emptyMap())

    companion object {
        val EMPTY = Blocklist(emptyMap(), emptyMap())

        /** Garde-fous : la liste est embarquée, mais une liste hors normes ne doit ni saturer la mémoire ni tout bloquer. */
        const val MAX_RULES = 200_000
        private val DOMAIN = Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        private val json = Json { ignoreUnknownKeys = true }

        /** Lit `shield_blocklist.json` (voir tools/update_shield_blocklist.py) ; les entrées invalides sont ignorées. */
        fun parse(text: String): Blocklist {
            val file = json.decodeFromString<File>(text.removePrefix("﻿"))
            require(file.version == 2) { "version de liste inconnue : ${file.version}" }
            var budget = MAX_RULES
            fun rules(group: Group?): Map<String, String?> {
                val rules = HashMap<String, String?>((group?.domains?.size ?: 0) + 1_000)
                group?.trackers?.forEach { (name, domains) ->
                    val tracker = visibleText(name, 100)
                    domains.forEach { if (budget > 0 && DOMAIN.matches(it) && rules.put(it, tracker) == null) budget-- }
                }
                group?.domains?.forEach { if (budget > 0 && DOMAIN.matches(it) && !rules.containsKey(it)) { rules[it] = null; budget-- } }
                return rules
            }
            val mobile = rules(file.groups["mobile"])
            return Blocklist(rules(file.groups["web"]), mobile)
        }
    }
}

/**
 * Nom affichable venu de la liste : sans caractères de contrôle ni de mise en forme invisibles, espaces normalisés,
 * longueur bornée. Même règle que `Untrusted.text` de l'app, recopiée pour que le module n'en dépende pas.
 */
internal fun visibleText(s: String, max: Int): String {
    val visible = s.replace(WHITESPACE, " ").filter { c ->
        val type = Character.getType(c)
        type != Character.CONTROL.toInt() && type != Character.FORMAT.toInt() &&
            type != Character.UNASSIGNED.toInt() && type != Character.PRIVATE_USE.toInt()
    }
    return visible.replace(WHITESPACE, " ").trim().take(max).ifEmpty { "?" }
}

private val WHITESPACE = Regex("\\s+")
