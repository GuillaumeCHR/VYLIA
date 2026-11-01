package com.vylia.privacy.domain.scoring

/**
 * Évalue les patterns de `security.patterns` sur une app.
 *
 * Syntaxe des conditions (champ "when") :
 *   atomes      SIGNAL                signal présent dans AppScan.signals (ex. INTERNET)
 *               granted:PERM          permission accordée
 *               declared:PERM         permission déclarée
 *               suspicious:PERM       accordée ET inattendue ou interdite pour la catégorie
 *               category:ID           catégorie résolue de l'app
 *   opérateurs  !a   a & b   a | b   ( … )   atleast(n: a, b, c)
 *   priorité    ! > & > |
 *
 * Les conditions sont compilées une fois à la construction : une faute de frappe
 * dans le JSON (signal inconnu, parenthèse manquante) lève une exception au démarrage
 * plutôt que de rendre un pattern silencieusement inopérant.
 */
class SecurityPatternDetector(private val rules: Rules) {

    private val compiled: Map<String, Cond> = rules.security.patterns.mapValues { (id, p) ->
        try {
            ConditionParser(p.condition, knownSignals()).parse()
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Pattern $id : ${e.message}", e)
        }
    }

    init {
        rules.security.patterns.forEach { (id, p) ->
            require(p.severity in rules.security.severityEffects) {
                "Pattern $id : sévérité « ${p.severity} » absente de security.severity_effects"
            }
        }
    }

    private fun knownSignals(): Set<String> =
        rules.security.signals.keys + PrivacyScorer.SIGNAL_IOC_MATCH

    fun detect(scan: AppScan, categoryId: String, contextOf: (String) -> Context): List<SecurityFinding> {
        val resolver = AtomResolver { atom ->
            val (prefix, value) = atom.split(':', limit = 2).let {
                if (it.size == 2) it[0] to it[1] else "" to it[0]
            }
            when (prefix) {
                "" -> value in scan.signals
                "granted" -> value in scan.grantedPermissions
                "declared" -> value in scan.declaredPermissions
                "suspicious" -> value in scan.grantedPermissions &&
                    contextOf(value).let { it == Context.UNEXPECTED || it == Context.FORBIDDEN }
                "category" -> value == categoryId
                else -> false // impossible : refusé à la compilation
            }
        }
        return rules.security.patterns.mapNotNull { (id, p) ->
            if (scan.packageName in p.exemptPackages || categoryId in p.exemptCategories) return@mapNotNull null
            if (!compiled.getValue(id).eval(resolver)) return@mapNotNull null
            SecurityFinding(id, p.label, p.severity, p.description)
        }.sortedBy { severityRank(it.severity) }
    }

    private fun severityRank(s: String) =
        rules.security.severityEffects[s]?.scoreCap ?: Int.MAX_VALUE
}

// ---------------------------------------------------------------------------
// Mini-langage de conditions
// ---------------------------------------------------------------------------

fun interface AtomResolver { fun resolve(atom: String): Boolean }

sealed interface Cond {
    fun eval(r: AtomResolver): Boolean

    data class Atom(val name: String) : Cond {
        override fun eval(r: AtomResolver) = r.resolve(name)
    }
    data class Not(val c: Cond) : Cond {
        override fun eval(r: AtomResolver) = !c.eval(r)
    }
    data class And(val cs: List<Cond>) : Cond {
        override fun eval(r: AtomResolver) = cs.all { it.eval(r) }
    }
    data class Or(val cs: List<Cond>) : Cond {
        override fun eval(r: AtomResolver) = cs.any { it.eval(r) }
    }
    data class AtLeast(val n: Int, val cs: List<Cond>) : Cond {
        override fun eval(r: AtomResolver) = cs.count { it.eval(r) } >= n
    }
}

class ConditionParser(private val src: String, private val knownSignals: Set<String>) {
    private var pos = 0

    fun parse(): Cond {
        val c = parseOr()
        skipWs()
        require(pos == src.length) { "caractère inattendu « ${src[pos]} » en position $pos dans « $src »" }
        return c
    }

    private fun parseOr(): Cond {
        val parts = mutableListOf(parseAnd())
        while (accept('|')) parts += parseAnd()
        return if (parts.size == 1) parts[0] else Cond.Or(parts)
    }

    private fun parseAnd(): Cond {
        val parts = mutableListOf(parseUnary())
        while (accept('&')) parts += parseUnary()
        return if (parts.size == 1) parts[0] else Cond.And(parts)
    }

    private fun parseUnary(): Cond {
        if (accept('!')) return Cond.Not(parseUnary())
        if (accept('(')) {
            val c = parseOr()
            expect(')')
            return c
        }
        val word = readWord()
        if (word == "atleast" && accept('(')) {
            val n = readWord().toIntOrNull()
            require(n != null && n > 0) { "atleast attend un entier positif dans « $src »" }
            expect(':')
            val items = mutableListOf(parseOr())
            while (accept(',')) items += parseOr()
            expect(')')
            require(n <= items.size) { "atleast($n) sur ${items.size} éléments seulement dans « $src »" }
            return Cond.AtLeast(n, items)
        }
        validateAtom(word)
        return Cond.Atom(word)
    }

    private fun validateAtom(atom: String) {
        val parts = atom.split(':', limit = 2)
        if (parts.size == 2) {
            require(parts[0] in PREFIXES) { "préfixe inconnu « ${parts[0]} » dans « $src »" }
            require(parts[1].isNotEmpty()) { "atome vide « $atom » dans « $src »" }
        } else if (knownSignals.isNotEmpty()) {
            require(atom in knownSignals) { "signal inconnu « $atom » (absent de security.signals) dans « $src »" }
        }
    }

    private fun readWord(): String {
        skipWs()
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] in "_:.")) {
            // ':' n'appartient au mot que s'il est suivi d'un identifiant (granted:X), pas dans atleast(2: …)
            if (src[pos] == ':' && (pos + 1 >= src.length || !src[pos + 1].isLetterOrDigit())) break
            pos++
        }
        require(pos > start) {
            if (pos < src.length) "atome attendu en position $pos (« ${src[pos]} ») dans « $src »"
            else "condition incomplète « $src »"
        }
        return src.substring(start, pos)
    }

    private fun skipWs() { while (pos < src.length && src[pos].isWhitespace()) pos++ }

    private fun accept(c: Char): Boolean {
        skipWs()
        return if (pos < src.length && src[pos] == c) { pos++; true } else false
    }

    private fun expect(c: Char) = require(accept(c)) { "« $c » attendu en position $pos dans « $src »" }

    companion object {
        val PREFIXES = setOf("granted", "declared", "suspicious", "category")
    }
}
