package com.vylia.privacy.domain.shield

import java.security.MessageDigest
import java.util.Base64

/**
 * Ce qu'une liste au format Adblock Plus / AdGuard (ou hosts) apporte à un filtrage DNS, et ce qui en est écarté.
 *
 * Un filtrage DNS ne voit qu'un nom de domaine : seules les règles qui visent un domaine entier ont un sens. Retenues :
 * `||domaine^`, `||domaine^$important`, `0.0.0.0 domaine` (hosts) et un domaine seul. Exceptions : `@@||domaine^`, et
 * `@@||domaine^|` ou `@@|domaine^|` (forme du DNS filter).
 * Écartées et comptées : les règles cosmétiques (masquage d'éléments, scripts), celles à modificateurs (`$third-party`
 * bloquerait au niveau DNS le site lui-même, `$script` ou `$popup` n'y ont pas de sens), et celles à chemin ou en
 * expression régulière. `$badfilter` annule la règle qu'il désigne (AdGuard French annule ainsi `||stripchat.com^`).
 */
data class ParsedList(
    val domains: Set<String>,
    val exceptions: Set<String>,
    val ignoredCosmetic: Int,
    val ignoredModifiers: Int,
    val ignoredOther: Int,
    /** `! Version:` de la liste, ou `null`. */
    val version: String?,
    /** `null` : la liste ne publie pas de somme de contrôle ; sinon, le résultat de la vérification. */
    val checksumValid: Boolean?,
)

object AdblockListParser {

    private val RULE = Regex("^\\|\\|([a-z0-9.-]+)\\^(?:\\$(.+))?$")
    private val EXCEPTION = Regex("^@@\\|\\|?([a-z0-9.-]+)\\^\\|?(?:\\$(.+))?$")
    private val HOSTS = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1|::|::1)\\s+([a-z0-9.-]+)(?:\\s*#.*)?$")
    private val COSMETIC = Regex("#[@?$%]*#|#@?[$%]#|#\\+js")
    private val CHECKSUM = Regex("^! Checksum:\\s*(\\S+)\\s*$")
    private val VERSION = Regex("^! Version:\\s*(.+)$")

    /** Modificateurs sans effet sur le domaine visé : la règle reste valable au niveau DNS. */
    private val DNS_MODIFIERS = setOf("important")

    /** Analyse la liste ligne par ligne, sans la garder en mémoire (certaines font 10 Mo). */
    fun parse(lines: Sequence<String>): ParsedList {
        val domains = HashSet<String>()
        val exceptions = HashSet<String>()
        val cancelled = HashSet<String>()
        var cosmetic = 0
        var modifiers = 0
        var other = 0
        var version: String? = null
        var checksum: String? = null
        // Somme de contrôle des listes AdGuard : MD5 du texte sans la ligne de somme ni les lignes vides, en base64.
        val md5 = MessageDigest.getInstance("MD5")
        var first = true
        var index = 0

        for (raw in lines) {
            val line = raw.trimEnd('\r')
            if (index++ < 50) {
                CHECKSUM.matchEntire(line)?.let { checksum = it.groupValues[1]; continue }
                if (version == null) VERSION.matchEntire(line)?.let { version = it.groupValues[1].trim() }
            }
            if (line.isNotEmpty()) {
                if (!first) md5.update('\n'.code.toByte())
                md5.update(line.toByteArray(Charsets.UTF_8))
                first = false
            }
            val rule = line.trim().lowercase()
            if (rule.isEmpty() || rule[0] == '!' || rule[0] == '[') continue
            if (COSMETIC.containsMatchIn(rule)) { cosmetic++; continue }
            if (rule[0] == '#') continue // commentaire d'un fichier hosts
            EXCEPTION.matchEntire(rule)?.let { m ->
                if (dnsOnly(m.groupValues[2])) exceptions += m.groupValues[1] else modifiers++
                continue
            }
            if (rule.startsWith("@@")) { other++; continue }
            RULE.matchEntire(rule)?.let { m ->
                val mods = m.groupValues[2].split(',').filter { it.isNotEmpty() }.map { it.substringBefore('=').removePrefix("~") }
                when {
                    "badfilter" in mods -> if (mods.all { it == "badfilter" || it in DNS_MODIFIERS }) cancelled += m.groupValues[1]
                    mods.all { it in DNS_MODIFIERS } -> domains += m.groupValues[1]
                    else -> modifiers++
                }
                continue
            }
            HOSTS.matchEntire(rule)?.let { domains += it.groupValues[1]; continue }
            if (Blocklist.DOMAIN.matches(rule)) { domains += rule; continue }
            other++
        }

        val valid = domains.filterTo(HashSet()) { Blocklist.DOMAIN.matches(it) && it !in cancelled }
        val checksumValid = checksum?.let { want ->
            val withoutTrailing = (md5.clone() as MessageDigest).digest()
            val withTrailing = md5.apply { update('\n'.code.toByte()) }.digest()
            want == base64(withoutTrailing) || want == base64(withTrailing)
        }
        return ParsedList(
            domains = valid,
            exceptions = exceptions.filterTo(HashSet()) { Blocklist.DOMAIN.matches(it) },
            ignoredCosmetic = cosmetic,
            ignoredModifiers = modifiers,
            ignoredOther = other,
            version = version?.let { visibleText(it, 40) },
            checksumValid = checksumValid,
        )
    }

    fun parse(text: String): ParsedList = parse(text.lineSequence())

    private fun dnsOnly(mods: String): Boolean = mods.isEmpty() || mods.split(',').all { it in DNS_MODIFIERS }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes).trimEnd('=')
}
