package com.vylia.privacy.domain.security

import com.vylia.privacy.domain.scoring.IocDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Base des stalkerwares connus, `assets/stalkerware_iocs.json`, produite par tools/update_stalkerware_iocs.py depuis
 * stalkerware-indicators d'Echap (CC BY 4.0). Embarquée dans l'app : rien n'est téléchargé, et un import de règles ne
 * peut pas la modifier. Lue comme une donnée externe : une entrée mal formée est écartée, pas reprise telle quelle.
 */
object StalkerwareDatabase {
    const val ASSET = "stalkerware_iocs.json"
    private const val MAX_APPS = 2_000
    private const val MAX_PER_APP = 500
    private val CERTIFICATE = Regex("[0-9A-Fa-f]{40}|[0-9A-Fa-f]{64}")

    @Serializable
    private data class File(val commit: String = "", val apps: List<App> = emptyList())

    @Serializable
    private data class App(val name: String, val packages: List<String> = emptyList(), val certificates: List<String> = emptyList())

    /** [commit] : version de la base embarquée (vide pour une base téléchargée) ; [apps] : nombre de stalkerwares. */
    class Loaded(val iocs: IocDatabase, val commit: String, val apps: Int)

    private val json = Json { ignoreUnknownKeys = true }

    /** La base embarquée (format compact produit par tools/update_stalkerware_iocs.py). */
    fun parse(content: String): Loaded {
        val file = json.decodeFromString<File>(content)
        val commit = file.commit.takeIf { it.matches(Regex("[0-9a-f]{7,40}")) }.orEmpty()
        return build(file.apps, commit)
    }

    /** Une base téléchargée : `ioc.yaml` d'Echap, dont seuls les stalkerwares sont repris (pas les watchwares). */
    fun parseIoc(content: String): Loaded = build(
        EchapIocParser.parse(content)
            .filter { it.type == "stalkerware" }
            .map { App(it.name, it.packages, it.certificates) },
        commit = "",
    )

    private fun build(entries: List<App>, commit: String): Loaded {
        val packages = HashMap<String, String>()
        val certificates = HashMap<String, String>()
        // Triées par nom, comme le script de génération : un paquet cité deux fois reste au même stalkerware.
        val apps = entries.take(MAX_APPS).filter { it.packages.isNotEmpty() || it.certificates.isNotEmpty() }
            .sortedBy { it.name.lowercase() }
        // Une app ne compte que si elle apporte un indicateur : ses paquets ont pu être tous pris par une autre.
        val counted = apps.count { app ->
            val name = Untrusted.text(app.name, 80)
            val added = app.packages.take(MAX_PER_APP).filter(Untrusted::isPackage).count { packages.putIfAbsent(it, name) == null } +
                app.certificates.take(MAX_PER_APP).filter(CERTIFICATE::matches).count { certificates.putIfAbsent(it, name) == null }
            added > 0
        }
        return Loaded(IocDatabase(packages, certificates), commit, counted)
    }
}
