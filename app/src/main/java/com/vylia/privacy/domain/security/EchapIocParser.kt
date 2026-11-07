package com.vylia.privacy.domain.security

/**
 * Lecteur d'`ioc.yaml` (stalkerware-indicators d'Echap), téléchargé depuis les Paramètres. Ce n'est pas un lecteur
 * YAML général : il ne comprend que la forme de ce fichier, et n'en garde que ce que VYLIA compare.
 *
 * ```
 * - name: TheTruthSpy        ← une entrée, en colonne 0
 *   type: stalkerware        ← une clé de l'entrée, indentée de 2
 *   packages:                ← une liste…
 *   - com.thetruth           ← …dont les éléments sont au retrait de la clé, ou de 4
 *   c2:                      ← une sous-clé (ici ips, domains) et son contenu sont ignorés
 *     ips:
 *     - 69.64.74.239
 * ```
 *
 * Les commentaires (`# …`) et les guillemets simples ou doubles sont retirés. Une ligne d'une autre forme fait
 * échouer la lecture : le fichier a changé de structure, mieux vaut garder la base en place que deviner.
 */
object EchapIocParser {
    data class Entry(val name: String, val type: String, val packages: List<String>, val certificates: List<String>)

    private const val MAX_LINES = 100_000
    private const val MAX_LINE = 1_000
    private val ENTRY = Regex("^- name\\s*:\\s*(.+)$")
    private val KEY = Regex("^ {2}([a-z][a-z0-9_]*)\\s*:\\s*(.*)$")
    private val ITEM = Regex("^ {2}(?: {2})?- (.+)$")
    private val SUBKEY = Regex("^ {4}[a-z][a-z0-9_]*\\s*:")

    fun parse(text: String): List<Entry> {
        val lines = text.removePrefix("﻿").lines()
        require(lines.size <= MAX_LINES) { "fichier trop long" }
        val entries = mutableListOf<Entry>()
        var name: String? = null
        var type = ""
        var key: String? = null
        var inSubkey = false
        val lists = HashMap<String, MutableList<String>>()

        fun close() {
            val current = name ?: return
            entries += Entry(current, type, lists["packages"].orEmpty(), lists["certificates"].orEmpty())
            name = null; type = ""; key = null; inSubkey = false; lists.clear()
        }

        lines.forEachIndexed { index, raw ->
            require(raw.length <= MAX_LINE) { "ligne ${index + 1} trop longue" }
            val line = stripComment(raw).trimEnd()
            if (line.isBlank() || line == "---") return@forEachIndexed
            ENTRY.find(line)?.let { match ->
                close()
                name = unquote(match.groupValues[1])
                return@forEachIndexed
            }
            require(name != null) { "ligne ${index + 1} hors d'une entrée" }
            val keyMatch = KEY.find(line)
            val item = ITEM.find(line)
            when {
                keyMatch != null -> {
                    key = keyMatch.groupValues[1]
                    inSubkey = false
                    val value = keyMatch.groupValues[2]
                    if (key == "type") type = unquote(value)
                }
                SUBKEY.containsMatchIn(line) -> inSubkey = true
                item != null && !inSubkey -> {
                    val list = key ?: throw IllegalArgumentException("ligne ${index + 1} : élément sans clé")
                    if (list == "packages" || list == "certificates") lists.getOrPut(list) { mutableListOf() } += unquote(item.groupValues[1])
                }
                line.startsWith("    ") -> Unit // contenu d'une sous-clé (c2 : ips, domains…) : sans usage ici
                else -> throw IllegalArgumentException("ligne ${index + 1} de forme inattendue")
            }
        }
        close()
        return entries
    }

    /** Retire un commentaire `#` qui suit une espace ; un `#` dans un nom (rare) est gardé s'il n'est pas précédé d'une espace. */
    private fun stripComment(line: String): String {
        if (line.trimStart().startsWith("#")) return ""
        val at = line.indexOf(" #")
        return if (at >= 0) line.substring(0, at) else line
    }

    private fun unquote(value: String): String {
        val v = value.trim()
        return if (v.length >= 2 && (v.first() == '"' && v.last() == '"' || v.first() == '\'' && v.last() == '\'')) {
            v.substring(1, v.length - 1)
        } else {
            v
        }
    }
}
