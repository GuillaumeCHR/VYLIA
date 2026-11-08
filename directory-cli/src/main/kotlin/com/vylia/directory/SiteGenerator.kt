package com.vylia.directory

import com.vylia.privacy.domain.scoring.Rules
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Site statique de l'annuaire, généré depuis `directory.json` : HTML et CSS, un petit script local pour la recherche
 * et les filtres (le tableau reste complet et lisible sans lui). Aucun traqueur, aucune ressource externe.
 *
 * Vocabulaire : l'annuaire décrit ce qu'une app **peut** faire, jamais ce qu'elle fait.
 */
class SiteGenerator(
    private val directory: Directory,
    private val rules: Rules,
    /** `alternatives.json` de l'app, pour proposer des alternatives sur la fiche. */
    private val alternativesJson: String?,
    /** Points d'historique par paquet, du plus ancien au plus récent : (date, note). */
    private val history: Map<String, List<Pair<String, Int>>> = emptyMap(),
) {
    fun write(out: File) {
        out.deleteRecursively()
        File(out, "apps").mkdirs()
        File(out, "site.css").writeText(resource("site.css"))
        File(out, "search.js").writeText(resource("search.js"))
        File(out, "favicon.svg").writeText(resource("favicon.svg"))
        File(out, "directory.json").writeText(DirectoryBuilder.encode(directory))
        File(out, ".nojekyll").writeText("")
        File(out, "index.html").writeText(index())
        File(out, "methodologie.html").writeText(methodology())
        directory.apps.forEach { File(out, "apps/${it.packageName}.html").writeText(appPage(it)) }
    }

    // --- Pages ---------------------------------------------------------------------------------------------------

    private fun index(): String {
        val categories = directory.apps.map { it.category }.distinct().sortedBy { categoryName(it) }
        val countries = directory.apps.flatMap { it.countries }.distinct().sorted()
        val scored = directory.apps.count { it.status == "SCORED" }
        val rows = directory.apps.sortedWith(compareBy<DirectoryApp> { it.score ?: 999 }.thenBy { it.name.lowercase() }).joinToString("\n") { app ->
            val grade = app.grade ?: "–"
            """<tr data-name="${esc(app.name.lowercase())} ${esc(app.packageName)}" data-category="${esc(app.category)}" data-grade="${esc(grade)}" data-countries="${esc(app.countries.joinToString(" "))}" data-score="${app.score ?: -1}">
  <td><a href="apps/${esc(app.packageName)}.html">${esc(app.name)}</a><br><span class="muted pkg">${esc(app.packageName)}</span></td>
  <td>${esc(categoryName(app.category))}</td>
  <td class="num">${app.score?.toString() ?: "<span class=\"muted\">pas encore analysée</span>"}</td>
  <td>${if (app.grade != null) "<span class=\"grade grade-${app.grade}\">${app.grade}</span>" else ""}</td>
</tr>"""
        }
        return page(
            title = "Annuaire VYLIA : 100 apps populaires en Europe",
            depth = 0,
            body = """
<h1>Annuaire VYLIA</h1>
<p class="lead">Les notes de vie privée de $scored apps parmi 100 apps populaires en Europe, calculées avec le moteur de
l'app <a href="https://github.com/GuillaumeCHR/VYLIA">VYLIA</a> à partir des rapports publics d'Exodus Privacy. Ce n'est
pas un classement officiel : la liste est choisie à la main d'après des classements publics de téléchargements.</p>
<p class="notice">Note d'exposition maximale : l'annuaire considère que toutes les permissions déclarées sont
accordées. Sur votre téléphone, VYLIA tient compte de ce que vous avez vraiment accordé.
<a href="methodologie.html">Méthodologie</a></p>

<form class="filters" role="search" aria-label="Filtrer les apps">
  <label>Rechercher <input type="search" id="q" autocomplete="off" placeholder="Nom ou paquet"></label>
  <label>Catégorie <select id="category"><option value="">Toutes</option>${categories.joinToString("") { "<option value=\"${esc(it)}\">${esc(categoryName(it))}</option>" }}</select></label>
  <label>Note <select id="grade"><option value="">Toutes</option>${listOf("A", "B", "C", "D", "E").joinToString("") { "<option>$it</option>" }}</select></label>
  <label>Pays <select id="country"><option value="">Tous</option>${countries.joinToString("") { "<option value=\"${esc(it)}\">${esc(countryName(it))}</option>" }}</select></label>
</form>
<p id="count" class="muted" aria-live="polite"></p>
<div class="table-wrap">
<table id="apps">
  <caption class="sr-only">Notes des apps, de la plus basse à la plus haute</caption>
  <thead><tr>
    <th scope="col"><button type="button" data-sort="name">App</button></th>
    <th scope="col">Catégorie</th>
    <th scope="col" class="num"><button type="button" data-sort="score">Score</button></th>
    <th scope="col">Note</th>
  </tr></thead>
  <tbody>
$rows
  </tbody>
</table>
</div>
<script src="search.js"></script>
""",
        )
    }

    private fun appPage(app: DirectoryApp): String {
        val body = StringBuilder()
        body.append("<p><a href=\"../index.html\">← Toutes les apps</a></p>\n")
        body.append("<h1>${esc(app.name)}</h1>\n<p class=\"muted pkg\">${esc(app.packageName)} · ${esc(categoryName(app.category))}</p>\n")
        if (app.status != "SCORED") {
            body.append("<p class=\"notice\">Pas encore analysée : Exodus Privacy n'a pas de rapport pour cette app.</p>\n")
            return page("${app.name} : annuaire VYLIA", 1, body.toString())
        }
        body.append(
            """
<div class="score-card">
  <span class="grade big grade-${app.grade}">${app.grade}</span>
  <div><p class="big-score">${app.score}<span class="muted">/100</span></p>
  <p class="muted">Note d'exposition maximale : toutes les permissions déclarées sont considérées comme accordées.</p></div>
</div>
""",
        )
        history[app.packageName]?.takeIf { it.size >= 2 }?.let { body.append(sparkline(it.takeLast(12))) }
        app.penalties?.let {
            body.append("<h2>Détail du calcul</h2>\n<p>On part de 100. Permissions : −${fmt(it.permissions)} points. Traqueurs : −${it.trackers} points.</p>\n")
        }
        if (app.badges.isNotEmpty()) {
            body.append("<h2>Badges</h2>\n<ul class=\"badges\">")
            app.badges.forEach { body.append("<li class=\"badge ${esc(it.polarity)}\">${polarityMark(it.polarity)} ${esc(it.label)}</li>") }
            body.append("</ul>\n")
        }
        body.append("<h2>Permissions que l'app peut demander</h2>\n")
        val counted = app.permissions.filter { it.counted > 0.0 }
        if (counted.isEmpty()) body.append("<p>Aucune permission ne coûte de points dans cette catégorie.</p>\n")
        else {
            body.append("<table><thead><tr><th scope=\"col\">Permission</th><th scope=\"col\">Pour cette catégorie</th><th scope=\"col\" class=\"num\">Points</th></tr></thead><tbody>")
            counted.forEach { body.append("<tr><td><code>${esc(it.name)}</code></td><td>${contextName(it.context)}</td><td class=\"num\">−${fmt(it.counted)}</td></tr>") }
            body.append("</tbody></table>\n")
        }
        val free = app.permissions.filter { it.counted == 0.0 }.map { it.name }
        if (free.isNotEmpty()) body.append("<details><summary>${free.size} autres permissions, sans coût ici</summary><p>${free.joinToString(", ") { "<code>${esc(it)}</code>" }}</p></details>\n")
        body.append("<h2>Traqueurs détectés dans le code</h2>\n")
        if (app.trackers.isEmpty()) body.append("<p>Aucun traqueur connu d'Exodus Privacy dans la version analysée.</p>\n")
        else {
            body.append("<table><thead><tr><th scope=\"col\">Traqueur</th><th scope=\"col\">Catégories</th><th scope=\"col\" class=\"num\">Points</th></tr></thead><tbody>")
            app.trackers.forEach { body.append("<tr><td>${esc(it.name)}</td><td>${esc(it.categories.joinToString(", "))}</td><td class=\"num\">−${it.penalty}</td></tr>") }
            body.append("</tbody></table>\n")
        }
        alternatives(app.packageName).takeIf { it.isNotEmpty() }?.let { alts ->
            body.append("<h2>Alternatives</h2>\n<ul>")
            alts.forEach { (name, url) -> body.append("<li>${if (url != null) "<a href=\"${esc(url)}\">${esc(name)}</a>" else esc(name)}</li>") }
            body.append("</ul>\n<p class=\"muted\">Aucune ne rend exactement le même service : à vous de choisir.</p>\n")
        }
        app.exodus?.let { e ->
            body.append(
                """
<h2>Source</h2>
<ul>
  <li>Version analysée : ${esc(e.versionName)} (code ${esc(e.versionCode)}), rapport Exodus du ${esc(e.updated)} :
      <a href="${esc(e.url)}">voir le rapport</a></li>
  <li>SDK cible inconnu : l'accès historique au stockage (<code>READ_EXTERNAL_STORAGE</code>) est compté.</li>
  <li>Motifs de sécurité (capacités d'espionnage) non évalués : ils demandent l'analyse de l'application elle-même.</li>
  <li>Règles VYLIA ${esc(directory.rulesVersion)}, calcul du ${esc(directory.generated)}.</li>
</ul>
""",
            )
        }
        return page("${app.name} : annuaire VYLIA", 1, body.toString())
    }

    private fun methodology(): String = page(
        "Méthodologie : annuaire VYLIA",
        0,
        """
<p><a href="index.html">← Toutes les apps</a></p>
<h1>Méthodologie</h1>
<h2>D'où viennent les notes</h2>
<p>Chaque semaine, l'annuaire lit le dernier rapport d'<a href="https://reports.exodus-privacy.eu.org/">Exodus Privacy</a>
de chaque app : les permissions qu'elle déclare et les traqueurs trouvés dans son code. Il les note avec le moteur et la
grille de l'app VYLIA (règles ${esc(directory.rulesVersion)}), les mêmes que sur votre téléphone : on part de 100, on retire
les permissions (50 points au plus) et les traqueurs (60 points au plus). Une permission est jugée selon la catégorie
de l'app : le GPS ne coûte rien à une app de navigation, il coûte cher à un jeu.</p>
<h2>Une note d'exposition maximale</h2>
<p>Hors du téléphone, on ne sait pas ce que vous avez accordé. L'annuaire considère donc que <strong>toutes les
permissions déclarées sont accordées</strong>. Sur votre téléphone, VYLIA ne compte entièrement que ce que vous avez
réellement accordé : sa note peut être plus haute que celle de l'annuaire.</p>
<h2>Ce que l'annuaire ne mesure pas</h2>
<ul>
  <li>Le comportement réel d'une app : l'annuaire décrit ce qu'elle <strong>peut</strong> faire, pas ce qu'elle fait.</li>
  <li>Le trafic réseau et les données réellement envoyées.</li>
  <li>Les motifs de sécurité (lecture de la saisie, enregistrement en arrière-plan…), qui demandent d'analyser l'application elle-même.</li>
  <li>Le SDK cible, que les rapports ne donnent pas : l'accès historique au stockage est compté.</li>
  <li>Les traqueurs inconnus d'Exodus Privacy.</li>
</ul>
<h2>La liste des apps</h2>
<p>Environ 100 apps populaires en Europe, choisies à la main d'après des classements publics de téléchargements
(France, Allemagne, Espagne, Italie, Pologne, Pays-Bas) et revues chaque trimestre. Ce n'est pas un classement officiel.</p>
<h2>Signaler une erreur, droit de réponse</h2>
<p>Une erreur, une app à ajouter, une réponse d'éditeur : <a href="mailto:yogistudiofr@gmail.com">yogistudiofr@gmail.com</a>
ou <a href="https://github.com/GuillaumeCHR/VYLIA/issues">une issue sur GitHub</a>.</p>
<h2>Licence</h2>
<p>L'annuaire est une base de données dérivée des données d'Exodus Privacy. Il est publié sous
<a href="https://opendatacommons.org/licenses/odbl/1-0/">Open Database License (ODbL 1.0)</a> :
<a href="directory.json">télécharger les données</a>. Le code de VYLIA et de ce site est sous GPL v3.</p>
""",
    )

    // --- Briques ------------------------------------------------------------------------------------------------

    private fun page(title: String, depth: Int, body: String): String {
        val up = "../".repeat(depth)
        return """<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'">
<meta name="referrer" content="no-referrer">
<title>${esc(title)}</title>
<link rel="icon" href="${up}favicon.svg" type="image/svg+xml">
<link rel="stylesheet" href="${up}site.css">
</head>
<body>
<a class="skip" href="#main">Aller au contenu</a>
<header><a class="brand" href="${up}index.html">VYLIA · annuaire</a> <nav><a href="${up}methodologie.html">Méthodologie</a> <a href="https://guillaumechr.github.io/vylia-site/fr/">L'app VYLIA</a></nav></header>
<main id="main">
$body
</main>
<footer>
<p>${esc(Directory.ATTRIBUTION)}</p>
<p>Annuaire publié sous <a href="https://opendatacommons.org/licenses/odbl/1-0/">ODbL 1.0</a> :
<a href="${up}directory.json">données</a>. Code sous <a href="https://www.gnu.org/licenses/gpl-3.0.html">GPL v3</a>.
Calcul du ${esc(directory.generated)}. Aucun traqueur, aucune mesure d'audience sur ce site.</p>
<p><a href="${up}methodologie.html">Méthodologie, contact et droit de réponse</a></p>
</footer>
</body>
</html>
"""
    }

    /** Courbe des dernières notes, en SVG statique (sans script), avec son résumé pour les lecteurs d'écran. */
    private fun sparkline(points: List<Pair<String, Int>>): String {
        val w = 240
        val h = 48
        val step = if (points.size > 1) w.toDouble() / (points.size - 1) else 0.0
        val path = points.mapIndexed { i, (_, s) -> "${fmt(i * step)},${fmt(h - s * h / 100.0)}" }.joinToString(" ")
        val summary = "Évolution de la note : " + points.joinToString(", ") { (d, s) -> "$s le $d" }
        return """<figure class="history"><svg viewBox="0 0 $w $h" width="$w" height="$h" role="img" aria-label="${esc(summary)}"><polyline points="$path" fill="none" stroke="currentColor" stroke-width="2"/></svg><figcaption class="muted">Note des ${points.size} dernières semaines</figcaption></figure>
"""
    }

    private fun alternatives(packageName: String): List<Pair<String, String?>> {
        val text = alternativesJson ?: return emptyList()
        val groups = runCatching { lenientJson.parseToJsonElement(text).jsonObject["groups"] as? JsonArray }.getOrNull() ?: return emptyList()
        return groups.mapNotNull { it as? JsonObject }
            .filter { g -> (g["replaces"] as? JsonArray)?.any { it.jsonPrimitive.contentOrNull == packageName } == true }
            .flatMap { g -> (g["alternatives"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }
            .mapNotNull { a ->
                val name = a["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val play = a["play"]?.jsonPrimitive?.contentOrNull
                val fdroid = a["fdroid"]?.jsonPrimitive?.contentOrNull
                name to when {
                    fdroid != null -> "https://f-droid.org/packages/$fdroid/"
                    play != null -> "https://play.google.com/store/apps/details?id=$play"
                    else -> null
                }
            }
            .distinctBy { it.first }
            .filter { it.first.isNotBlank() }
            .take(8)
    }

    private fun categoryName(id: String): String = rules.categories[id]?.displayName ?: id

    private fun contextName(context: String): String = when (context) {
        "ALLOWED" -> "justifiée"
        "TOLERATED" -> "tolérée"
        "UNEXPECTED" -> "inattendue"
        "FORBIDDEN" -> "injustifiée"
        else -> context
    }

    private fun countryName(code: String): String = when (code) {
        "EU" -> "Union européenne"
        "FR" -> "France"
        "DE" -> "Allemagne"
        "ES" -> "Espagne"
        "IT" -> "Italie"
        "PL" -> "Pologne"
        "NL" -> "Pays-Bas"
        else -> code
    }

    private fun polarityMark(polarity: String): String = when (polarity) {
        "positive" -> "<span aria-hidden=\"true\">✓</span>"
        "negative" -> "<span aria-hidden=\"true\">!</span>"
        else -> "<span aria-hidden=\"true\">·</span>"
    }

    private fun resource(name: String): String =
        checkNotNull(SiteGenerator::class.java.getResource("/site/$name")) { "ressource manquante : $name" }.readText()

    private fun fmt(value: Double): String = DirectoryBuilder.round1(value).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }

    companion object {
        fun esc(s: String): String = buildString(s.length) {
            s.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }
    }
}
