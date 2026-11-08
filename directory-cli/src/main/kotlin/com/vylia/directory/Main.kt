package com.vylia.directory

import com.vylia.privacy.domain.scoring.RulesLoader
import java.io.File
import kotlin.system.exitProcess

/**
 * Outil de l'annuaire public VYLIA (voir directory/README.md) :
 *
 * - `generate` : rapports Exodus → notes → `directory.json` (le jeton est lu dans `EXODUS_API_TOKEN`) ;
 * - `validate` : `directory.json` complet et cohérent avec la liste des apps ;
 * - `guard` : refuse la publication si trop d'apps ont perdu leur note depuis la semaine précédente ;
 * - `site` : site statique à partir de `directory.json`.
 *
 * Chemins relatifs à la racine du dépôt, valeurs par défaut adaptées à la GitHub Action.
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: usage()
    val options = parseOptions(args.drop(1))
    fun opt(name: String, default: String? = null): String = options[name] ?: default ?: usage("option --$name manquante")
    try {
        when (command) {
            "generate" -> generate(options, ::opt)
            "validate" -> validate(File(opt("directory", DIRECTORY)), File(opt("apps", APPS)), File(opt("rules", RULES)))
            "guard" -> guard(options["previous"]?.let(::File), File(opt("directory", DIRECTORY)))
            "site" -> site(::opt)
            else -> usage("commande inconnue : $command")
        }
    } catch (e: Exception) {
        System.err.println("Erreur : ${e.message}")
        exitProcess(1)
    }
}

private const val APPS = "directory/apps.json"
private const val RULES = "app/src/main/assets/vylia_rules_v3.json"
private const val OVERRIDES = "app/src/main/assets/exodus_category_overrides.json"
private const val ALTERNATIVES = "app/src/main/assets/alternatives.json"
private const val CACHE = "directory/cache"
private const val DIRECTORY = "directory/out/directory.json"
private const val SITE = "directory/out/site"

private fun generate(options: Map<String, String>, opt: (String, String?) -> String) {
    val date = opt("date", null)
    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { "--date attendu au format AAAA-MM-JJ" }
    val apps = lenientJson.decodeFromString(AppList.serializer(), File(opt("apps", APPS)).readText()).apps
    val rulesText = File(opt("rules", RULES)).readText()
    val rules = RulesLoader.parse(rulesText)
    val overrides = ReportConverter.overrides(File(opt("overrides", OVERRIDES)).readText())
    val cache = ReportCache(File(opt("cache", CACHE)))
    val offline = "offline" in options
    val source: ExodusSource? = if (offline) null else ExodusClient(
        System.getenv("EXODUS_API_TOKEN")?.takeIf { it.isNotBlank() } ?: error("jeton EXODUS_API_TOKEN absent (ou --offline)"),
    )

    val trackersText = if (source == null) cache.readTrackers() ?: error("pas de traqueurs en cache") else source.trackers().also(cache::writeTrackers)
    val trackers = ReportConverter.trackers(trackersText, overrides)

    var unchanged = 0
    var failed = 0
    val reports = apps.associate { app ->
        val cached = cache.read(app.packageName)
        val text = if (source == null) cached else try {
            source.details(app.packageName)?.also { fresh ->
                if (cached != null && ReportConverter.latestReport(cached)?.report == ReportConverter.latestReport(fresh)?.report) unchanged++
                cache.write(app.packageName, fresh)
            }
        } catch (e: Exception) {
            // Pas de repli sur le cache : une panne doit se voir (garde-fou de publication), pas publier des données périmées.
            failed++
            System.err.println("${app.packageName} : ${e.message}")
            null
        }
        app.packageName to text?.let(ReportConverter::latestReport)
    }
    val directory = DirectoryBuilder.build(apps, reports, trackers, rules, rulesText, date)
    val out = File(opt("out", DIRECTORY)).apply { parentFile?.mkdirs() }
    out.writeText(DirectoryBuilder.encode(directory))
    val scored = directory.apps.count { it.status == "SCORED" }
    println("$scored apps notées sur ${apps.size} ; rapports inchangés : $unchanged ; échecs : $failed -> $out")
}

private fun validate(directoryFile: File, appsFile: File, rulesFile: File) {
    val directory = DirectoryBuilder.decode(directoryFile.readText())
    val apps = lenientJson.decodeFromString(AppList.serializer(), appsFile.readText()).apps
    val overrides = RulesLoader.parse(rulesFile.readText()).packageOverrides
    val problems = DirectoryValidator.problems(directory, apps, overrides)
    if (problems.isNotEmpty()) error(problems.joinToString("\n", "directory.json invalide :\n"))
    println("directory.json valide : ${directory.apps.size} apps, ${directory.apps.count { it.status == "SCORED" }} notées.")
}

private fun guard(previousFile: File?, currentFile: File) {
    val previous = previousFile?.takeIf { it.exists() }?.readText()?.let(DirectoryBuilder::decode)
    val verdict = PublishGuard.check(previous, DirectoryBuilder.decode(currentFile.readText()))
    println("Notes perdues depuis la semaine précédente : ${verdict.lost} sur ${verdict.previouslyScored}.")
    if (!verdict.ok) error("plus de ${(PublishGuard.MAX_LOST * 100).toInt()} % des apps ont perdu leur note : publication bloquée, l'ancienne version reste en ligne")
}

private fun site(opt: (String, String?) -> String) {
    val directory = DirectoryBuilder.decode(File(opt("directory", DIRECTORY)).readText())
    val rules = RulesLoader.parse(File(opt("rules", RULES)).readText())
    val alternatives = File(opt("alternatives", ALTERNATIVES)).takeIf { it.exists() }?.readText()
    val history = HistoryReader.read(File(opt("history", "directory/history")), current = directory)
    val out = File(opt("out", SITE))
    SiteGenerator(directory, rules, alternatives, history).write(out)
    println("Site écrit dans $out (${directory.apps.size} fiches).")
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val options = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val name = args[i].removePrefix("--")
        val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
        options[name] = value ?: ""
        i += if (value == null) 1 else 2
    }
    return options
}

private fun usage(message: String? = null): Nothing {
    message?.let { System.err.println(it) }
    System.err.println("Usage : generate --date AAAA-MM-JJ [--offline] | validate | guard [--previous fichier] | site [--history dossier]")
    exitProcess(2)
}

/** Contrôles de `directory.json` avant publication. */
object DirectoryValidator {
    fun problems(directory: Directory, apps: List<AppEntry>, overrides: Map<String, String>): List<String> = buildList {
        val listed = apps.map { it.packageName }.toSet()
        val published = directory.apps.map { it.packageName }.toSet()
        if (listed != published) add("apps manquantes ou en trop : ${(listed - published) + (published - listed)}")
        if (directory.license != Directory.LICENSE) add("licence inattendue")
        directory.apps.forEach { a ->
            if (a.status == "SCORED") {
                if (a.score == null || a.score !in 0..100) add("${a.packageName} : score invalide")
                if (a.grade == null || a.grade !in setOf("A", "B", "C", "D", "E")) add("${a.packageName} : note invalide")
                if (a.exodus == null) add("${a.packageName} : rapport Exodus manquant")
            } else if (a.status != "NOT_ANALYZED") add("${a.packageName} : statut inconnu ${a.status}")
            if (overrides[a.packageName] != a.category) add("${a.packageName} : catégorie ${a.category} absente de package_overrides (ou différente)")
        }
    }
}

/** Historique des notes (fichiers `AAAA-MM-JJ.json` des semaines précédentes), pour la courbe des fiches. */
object HistoryReader {
    fun read(dir: File, current: Directory): Map<String, List<Pair<String, Int>>> {
        val weeks = (dir.listFiles { f -> f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.json")) }.orEmpty().sortedBy { it.name }
            .mapNotNull { runCatching { DirectoryBuilder.decode(it.readText()) }.getOrNull() } + current)
            .distinctBy { it.generated }
            .sortedBy { it.generated }
        val points = HashMap<String, MutableList<Pair<String, Int>>>()
        weeks.forEach { week -> week.apps.forEach { a -> a.score?.let { points.getOrPut(a.packageName) { mutableListOf() } += week.generated to it } } }
        return points
    }
}
