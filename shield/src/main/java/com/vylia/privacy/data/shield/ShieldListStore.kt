package com.vylia.privacy.data.shield

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.shield.AdblockListParser
import com.vylia.privacy.domain.shield.Blocklist
import com.vylia.privacy.domain.shield.BlocklistBuilder
import com.vylia.privacy.domain.shield.ShieldFilter
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Une liste publique que le bouclier sait télécharger. [proposed] : pas active d'office, l'utilisateur l'ajoute depuis
 * Paramètres › Bases de données en voyant la taille du téléchargement ([downloadBytes], compressé, et [rawBytes]).
 */
data class ShieldList(
    val id: String,
    val name: String,
    val url: String,
    val license: String,
    val filter: ShieldFilter,
    /** Taille maximale acceptée, décompressée. */
    val maxBytes: Long,
    /** En dessous, la liste est tronquée ou n'est pas la bonne : elle est refusée. */
    val minDomains: Int,
    val proposed: Boolean = false,
    val downloadBytes: Long = 0,
    val rawBytes: Long = 0,
)

/**
 * Listes du bouclier téléchargées depuis l'app. Le texte reçu n'est pas gardé : seul l'extrait utile au DNS (domaines
 * et exceptions) est écrit, compressé (`filesDir/shield/<id>.gz`), et relu seulement pour recompiler la liste. La liste
 * compilée (`compiled2.bin`, empreintes des domaines) est celle que le bouclier charge au démarrage.
 */
class ShieldListStore(private val context: Context) {

    private val dir = File(context.filesDir, "shield").apply { mkdirs() }
    /** `compiled.bin` avant le 2026-11-14 : compilé avec les listes de publicité, il n'est plus lu ([dropRetired]). */
    private val compiled = File(dir, "compiled2.bin")

    /** Ce qu'une liste installée apporte : domaines retenus, exceptions, règles écartées. */
    data class Installed(val domains: Int, val exceptions: Int, val ignored: Int, val version: String?)

    /**
     * Analyse [file] (le texte téléchargé), le refuse s'il n'est pas plausible ou si sa somme de contrôle est fausse,
     * puis remplace l'extrait de la liste de façon atomique. L'extrait précédent reste en place en cas d'échec.
     */
    fun install(list: ShieldList, file: File): Installed {
        val parsed = file.bufferedReader().useLines { AdblockListParser.parse(it) }
        require(parsed.checksumValid != false) { "somme de contrôle incorrecte" }
        require(parsed.domains.size >= list.minDomains) { "liste incomplète (${parsed.domains.size} domaines)" }
        val tmp = File(dir, "${list.id}.gz.tmp")
        try {
            GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { out ->
                parsed.domains.sorted().forEach { out.write("+"); out.write(it); out.write("\n") }
                parsed.exceptions.sorted().forEach { out.write("-"); out.write(it); out.write("\n") }
            }
            check(tmp.renameTo(extract(list.id))) { "installation de la liste impossible" }
        } finally {
            tmp.delete()
        }
        return Installed(parsed.domains.size, parsed.exceptions.size, parsed.ignoredCosmetic + parsed.ignoredModifiers + parsed.ignoredOther, parsed.version)
    }

    fun isDownloaded(id: String): Boolean = extract(id).exists()

    /**
     * Efface les extraits des listes qui ne sont plus proposées ([RETIRED]) et l'ancienne liste compilée ; s'il y avait
     * un extrait retiré, la liste compilée avec lui aussi. Renvoie `true` si quelque chose a été effacé.
     */
    fun dropRetired(): Boolean {
        val legacy = File(dir, "compiled.bin").delete()
        val retired = RETIRED.map(::extract).filter { it.exists() }
        if (retired.isEmpty()) return legacy
        retired.forEach { it.delete() }
        compiled.delete()
        return true
    }

    /** Liste retirée par l'utilisateur (liste proposée) : son extrait est effacé. */
    fun remove(id: String) {
        extract(id).delete()
    }

    /**
     * Recompile la liste du bouclier à partir des extraits des listes [enabled] et de la liste embarquée, puis l'écrit.
     * Renvoie la nouvelle liste (sans les domaines de logiciels espions mis à jour, ajoutés par le contrôleur).
     */
    fun compile(
        enabled: List<ShieldList>,
        embedded: () -> Blocklist.Snapshot,
        exodus: List<com.vylia.privacy.domain.shield.ExodusNetworkTracker>?,
    ): BlocklistBuilder.Result {
        val sources = enabled.mapNotNull { list -> readExtract(list)?.let { BlocklistBuilder.Source(list.filter, it) } }
        // Tant qu'une liste active d'office n'a jamais été téléchargée, la liste embarquée la remplace.
        val fallback = enabled.any { !it.proposed && !isDownloaded(it.id) }
        val named = exodus?.let(com.vylia.privacy.domain.shield.ExodusShieldDomains::named)?.takeIf { it.size >= MIN_EXODUS_DOMAINS }
        val result = BlocklistBuilder.build(embedded(), sources, fallback, named)
        val tmp = File(dir, "compiled.bin.tmp")
        try {
            tmp.outputStream().use { result.blocklist.write(it) }
            check(tmp.renameTo(compiled)) { "écriture de la liste impossible" }
        } finally {
            tmp.delete()
        }
        return result
    }

    /** La liste compilée, ou `null` s'il n'y en a pas (ou si elle est illisible : la liste embarquée prend le relais). */
    fun loadCompiled(): Blocklist? {
        if (!compiled.exists()) return null
        return try {
            compiled.inputStream().use(Blocklist::read)
        } catch (e: Exception) {
            Log.w(TAG, "Liste compilée illisible, liste embarquée utilisée", e)
            null
        }
    }

    /** Supprimer mes données : les listes téléchargées partent aussi, le bouclier revient à la liste embarquée. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun extract(id: String) = File(dir, "$id.gz")

    private fun readExtract(list: ShieldList): com.vylia.privacy.domain.shield.ParsedList? {
        val file = extract(list.id)
        if (!file.exists()) return null
        return try {
            val domains = HashSet<String>()
            val exceptions = HashSet<String>()
            GZIPInputStream(file.inputStream().buffered()).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("+") -> domains += line.substring(1)
                        line.startsWith("-") -> exceptions += line.substring(1)
                    }
                }
            }
            // Revalidé : l'extrait a pu être écrit par une version moins stricte.
            com.vylia.privacy.domain.shield.ParsedList(
                domains.filterTo(HashSet()) { Blocklist.DOMAIN.matches(it) },
                exceptions.filterTo(HashSet()) { Blocklist.DOMAIN.matches(it) },
                0, 0, 0, null, null,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Extrait ${list.id} illisible, ignoré", e)
            null
        }
    }

    companion object {
        private const val TAG = "ShieldListStore"
        private const val MB = 1024L * 1024
        /** La base Exodus donne plus de 500 domaines : beaucoup moins, la liste embarquée garde la main. */
        private const val MIN_EXODUS_DOMAINS = 200

        /**
         * Listes du bouclier : des traqueurs seulement. Écartées (mesures du 2026-11-08, voir docs/shield.md) : AdGuard
         * Tracking Protection et Base (déjà compilées dans le DNS filter), URL Tracking (paramètres d'URL, sans effet au
         * niveau DNS), Social Media et Fanboy Social (règles cosmétiques), Liste FR (doublon d'AdGuard French). Retirées
         * le 2026-11-14 ([RETIRED]) : les listes de publicité (EasyList, AdGuard DNS filter, Mobile Ads et French).
         */
        /** Identifiants des listes que l'app ne propose plus : leurs extraits sont effacés ([dropRetired]). */
        val RETIRED = listOf("adguard_dns", "easylist", "adguard_mobile", "adguard_french")

        val LISTS = listOf(
            ShieldList(
                "easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt",
                "GPL v3", ShieldFilter.WEB, maxBytes = 10 * MB, minDomains = 10_000, downloadBytes = 450_000, rawBytes = 1_510_000,
            ),
            ShieldList(
                "adguard_cname", "AdGuard CNAME trackers",
                "https://raw.githubusercontent.com/AdguardTeam/cname-trackers/master/data/combined_disguised_trackers.txt",
                "MIT", ShieldFilter.WEB, maxBytes = 40 * MB, minDomains = 50_000, proposed = true,
                downloadBytes = 2_360_000, rawBytes = 10_670_000,
            ),
        )
    }
}
