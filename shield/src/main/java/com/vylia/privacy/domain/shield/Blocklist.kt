package com.vylia.privacy.domain.shield

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Les deux filtrages du bouclier, activables séparément. */
enum class ShieldFilter {
    /** Pistage des sites, mesure d'audience et profilage des apps. Gêne rarement les apps. */
    WEB,

    /**
     * Réseaux de profilage des apps et des jeux (traqueurs Exodus de catégorie Advertisement). Pas proposé dans cette
     * version ([AVAILABLE]) : leurs domaines ne sont pas bloqués, et la liste embarquée n'a pas de groupe mobile.
     */
    MOBILE,
    ;

    companion object {
        /**
         * Filtrages proposés dans cette version : le bouclier bloque les traqueurs, pas les régies publicitaires des
         * apps (règles Google Play sur les apps qui gênent l'affichage des pubs d'autres apps). Le filtrage mobile
         * pourra revenir en option, désactivée d'office.
         */
        val AVAILABLE: Set<ShieldFilter> = setOf(WEB)
    }
}

/**
 * Raison d'un blocage : la règle, son filtrage (`null` : logiciel espion, toujours bloqué), et le traqueur ou la
 * campagne connus pour elle (`null` : domaine d'une liste).
 */
data class BlockMatch(val rule: String, val filter: ShieldFilter?, val trackerName: String?)

/**
 * Liste de blocage du bouclier : un domaine est bloqué s'il figure dans la liste, ou si l'un de ses domaines parents
 * y figure (`ads.example.com` est bloqué par `example.com`, pas l'inverse), à condition que son filtrage soit actif.
 *
 * Le filtrage mobile l'emporte : un domaine qui relève (lui ou un parent) d'une régie mobile passe quand le filtrage
 * mobile est coupé, même si une liste web le cite aussi : couper le filtrage mobile suffit à rétablir une app qui en dépend.
 */
class Blocklist(
    private val web: DomainHashSet,
    private val mobile: DomainHashSet,
    /** Nom du traqueur connu (Exodus, complément maison) pour certains domaines des deux groupes. */
    private val names: Map<String, String> = emptyMap(),
    /** Domaines de logiciels espions (indicateurs MVT) : bloqués dès que le bouclier tourne, quels que soient les filtrages. */
    private val spyware: Map<String, String?> = emptyMap(),
    /**
     * Exceptions des listes (`@@||domaine^`) : ni ce domaine ni ses sous-domaines ne sont bloqués, même si un domaine
     * parent est listé. Les logiciels espions restent bloqués.
     */
    private val allow: DomainHashSet = DomainHashSet.EMPTY,
) {

    val size: Int get() = web.size + mobile.size + spyware.size

    /**
     * La même liste avec d'autres domaines de logiciels espions (indicateurs MVT mis à jour depuis l'app) ; les
     * domaines invalides sont écartés, et le nombre en est borné comme pour la liste embarquée.
     */
    fun withSpyware(domains: Map<String, String>): Blocklist = Blocklist(
        web,
        mobile,
        names,
        domains.entries.asSequence().filter { DOMAIN.matches(it.key) }.take(MAX_SPYWARE)
            .associate { it.key to visibleText(it.value, 100) },
        allow,
    )

    fun match(domain: String, enabled: Set<ShieldFilter> = ShieldFilter.entries.toSet()): BlockMatch? {
        var name = domain.trimEnd('.').lowercase()
        var found: BlockMatch? = null
        var allowed = false
        while (true) {
            if (spyware.containsKey(name)) return BlockMatch(name, null, spyware[name])
            if (!allowed && name in allow) allowed = true
            if (name in mobile) {
                if (ShieldFilter.MOBILE !in enabled) return null
                if (found == null) found = BlockMatch(name, ShieldFilter.MOBILE, names[name])
            } else if (found == null && ShieldFilter.WEB in enabled && name in web) {
                found = BlockMatch(name, ShieldFilter.WEB, names[name])
            }
            val dot = name.indexOf('.')
            // On s'arrête au dernier niveau : un TLD seul (« com ») n'est jamais une règle.
            if (dot < 0 || name.indexOf('.', dot + 1) < 0) return if (allowed) null else found
            name = name.substring(dot + 1)
        }
    }

    /** Écrit la liste compilée, relue par [read] au démarrage du bouclier sans réanalyser les listes. */
    fun write(out: java.io.OutputStream) {
        val data = java.io.DataOutputStream(java.io.BufferedOutputStream(out))
        data.writeInt(MAGIC)
        data.writeInt(FORMAT)
        web.write(data)
        mobile.write(data)
        data.writeInt(names.size)
        names.forEach { (domain, name) -> data.writeUTF(domain); data.writeUTF(name) }
        data.writeInt(spyware.size)
        spyware.forEach { (domain, name) -> data.writeUTF(domain); data.writeUTF(name ?: "") }
        allow.write(data)
        data.flush()
    }

    @Serializable
    private data class Group(
        val trackers: Map<String, List<String>> = emptyMap(),
        val domains: List<String> = emptyList(),
    )

    @Serializable
    private data class File(val version: Int, val groups: Map<String, Group> = emptyMap())

    /** Contenu de la liste embarquée, avant compilation : sert de base à [BlocklistBuilder]. */
    data class Snapshot(
        /** domaine -> (nom du traqueur, filtrage) : Exodus et complément maison. */
        val named: Map<String, Pair<String, ShieldFilter>>,
        val web: Set<String>,
        val mobile: Set<String>,
        val spyware: Map<String, String?>,
        /** Exceptions des listes (groupe « allow ») : protègent aussi les sous-domaines. */
        val allow: Set<String> = emptySet(),
    )

    companion object {
        val EMPTY = Blocklist(DomainHashSet.EMPTY, DomainHashSet.EMPTY)

        /** Garde-fous : une liste hors normes ne doit ni saturer la mémoire ni tout bloquer. */
        const val MAX_RULES = 400_000
        const val MAX_SPYWARE = 50_000
        private const val MAX_NAMES = 50_000
        private const val MAGIC = 0x5659424c // « VYBL »
        private const val FORMAT = 2
        internal val DOMAIN = Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        private val json = Json { ignoreUnknownKeys = true }

        /** Lit `shield_blocklist.json` (voir tools/update_shield_blocklist.py) ; les entrées invalides sont ignorées. */
        fun parse(text: String): Blocklist = snapshot(text).let { s ->
            val names = s.named.mapValues { it.value.first }
            Blocklist(DomainHashSet.of(s.web), DomainHashSet.of(s.mobile), names, s.spyware, DomainHashSet.of(s.allow))
        }

        /** Le contenu de `shield_blocklist.json`, groupe par groupe, dans la limite de [MAX_RULES] domaines. */
        fun snapshot(text: String): Snapshot {
            val file = json.decodeFromString<File>(text.removePrefix("\uFEFF"))
            require(file.version == 2) { "version de liste inconnue : ${file.version}" }
            var budget = MAX_RULES
            val named = HashMap<String, Pair<String, ShieldFilter>>()
            fun rules(group: Group?, filter: ShieldFilter?): Map<String, String?> {
                val rules = HashMap<String, String?>((group?.domains?.size ?: 0) + 1_000)
                group?.trackers?.forEach { (name, domains) ->
                    val tracker = visibleText(name, 100)
                    domains.forEach {
                        if (budget > 0 && DOMAIN.matches(it) && rules.put(it, tracker) == null) {
                            budget--
                            if (filter != null) named.putIfAbsent(it, tracker to filter)
                        }
                    }
                }
                group?.domains?.forEach { if (budget > 0 && DOMAIN.matches(it) && !rules.containsKey(it)) { rules[it] = null; budget-- } }
                return rules
            }
            // Les logiciels espions d'abord : le budget de règles ne doit jamais les écarter.
            val spyware = rules(file.groups["spyware"], null)
            val mobile = rules(file.groups["mobile"], ShieldFilter.MOBILE)
            val web = rules(file.groups["web"], ShieldFilter.WEB)
            val allow = file.groups["allow"]?.domains.orEmpty().asSequence().filter { DOMAIN.matches(it) }.take(MAX_SPYWARE).toSet()
            return Snapshot(named, web.keys, mobile.keys, spyware, allow)
        }

        /** Relit une liste écrite par [write] ; un fichier abîmé lève une exception (la liste embarquée prend le relais). */
        fun read(input: java.io.InputStream): Blocklist {
            val data = java.io.DataInputStream(java.io.BufferedInputStream(input))
            require(data.readInt() == MAGIC && data.readInt() == FORMAT) { "liste compilée inconnue" }
            val web = DomainHashSet.read(data, MAX_RULES)
            val mobile = DomainHashSet.read(data, MAX_RULES)
            fun pairs(max: Int): Map<String, String> {
                val n = data.readInt()
                require(n in 0..max) { "liste compilée invalide" }
                return HashMap<String, String>(n * 2).apply { repeat(n) { put(data.readUTF(), data.readUTF()) } }
            }
            val names = pairs(MAX_NAMES)
            val spyware = pairs(MAX_SPYWARE).mapValues { it.value.ifEmpty { null } }
            val allow = DomainHashSet.read(data, MAX_SPYWARE)
            return Blocklist(web, mobile, names, spyware, allow)
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
