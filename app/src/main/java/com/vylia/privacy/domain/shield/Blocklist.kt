package com.vylia.privacy.domain.shield

import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Raison d'un blocage : la règle qui l'a déclenché et le traqueur Exodus connu pour elle (`null` : EasyList). */
data class BlockMatch(val rule: String, val trackerName: String?)

/**
 * Liste de blocage du bouclier : un domaine est bloqué s'il figure dans la liste, ou si l'un de ses domaines parents
 * y figure (`ads.example.com` est bloqué par `example.com`, pas l'inverse).
 */
class Blocklist(private val rules: Map<String, String?>) {

    val size: Int get() = rules.size

    fun match(domain: String): BlockMatch? {
        var name = domain.trimEnd('.').lowercase()
        while (true) {
            if (rules.containsKey(name)) return BlockMatch(name, rules[name])
            val dot = name.indexOf('.')
            // On s'arrête au dernier niveau : un TLD seul (« com ») n'est jamais une règle.
            if (dot < 0 || name.indexOf('.', dot + 1) < 0) return null
            name = name.substring(dot + 1)
        }
    }

    @Serializable
    private data class File(
        val version: Int,
        val trackers: Map<String, List<String>> = emptyMap(),
        val domains: List<String> = emptyList(),
    )

    companion object {
        val EMPTY = Blocklist(emptyMap())

        /** Garde-fous : la liste est embarquée, mais une liste hors normes ne doit ni saturer la mémoire ni tout bloquer. */
        const val MAX_RULES = 200_000
        private val DOMAIN = Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        private val json = Json { ignoreUnknownKeys = true }

        /** Lit `shield_blocklist.json` (voir tools/update_shield_blocklist.py) ; les entrées invalides sont ignorées. */
        fun parse(text: String): Blocklist {
            val file = json.decodeFromString<File>(text.removePrefix("﻿"))
            require(file.version == 1) { "version de liste inconnue : ${file.version}" }
            val rules = HashMap<String, String?>(file.domains.size + 1_000)
            file.trackers.forEach { (name, domains) ->
                val tracker = Untrusted.text(name, 100)
                domains.forEach { if (rules.size < MAX_RULES && DOMAIN.matches(it)) rules[it] = tracker }
            }
            file.domains.forEach { if (rules.size < MAX_RULES && DOMAIN.matches(it)) rules.putIfAbsent(it, null) }
            return Blocklist(rules)
        }
    }
}
