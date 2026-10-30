package com.vylia.privacy.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.learn.PermissionInfo
import com.vylia.privacy.domain.learn.PermissionTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.vylia.privacy.domain.learn.GradeBand
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.domain.learn.gradeRiskLabel
import com.vylia.privacy.domain.scoring.Rules
import com.vylia.privacy.domain.scoring.Scoring
import com.vylia.privacy.ui.components.GradeScale
import com.vylia.privacy.ui.components.scoreGradientColor
import com.vylia.privacy.ui.components.TransparencyBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.home.HomeViewModel
import java.text.Normalizer
import java.util.Locale

/**
 * Pédagogie : 1/ comment le Privacy Score est calculé (valeurs lues dans les règles en vigueur),
 * 2/ lexique des permissions sensibles et de leurs impacts.
 */
@Composable
fun ApprendreScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val loadedRules by homeViewModel.rules.collectAsStateWithLifecycle()
    val rules = loadedRules.rules
    var query by rememberSaveable { mutableStateOf("") }
    val glossary = remember(query) { PermissionGlossary.entries.filter { it.matches(query) }.groupBy { it.theme } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Apprendre", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Comprendre votre Privacy Score et ce que chaque autorisation permet vraiment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { ScoreOverview(rules) }
        item { PermissionScoring(rules) }
        item { TrackerScoring(rules) }
        item { CategoryResolution(rules) }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Lexique des permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Touchez une permission pour savoir ce qu'elle permet, pourquoi c'est sensible et comment elle est accordée.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Rechercher (ex. SMS, READ_PHONE_STATE, position)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Effacer") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (glossary.isEmpty()) {
            item { Text("Aucune permission ne correspond à « ${query.trim()} ».", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        PermissionTheme.entries.forEach { theme ->
            val infos = glossary[theme] ?: return@forEach
            item(key = "theme-$theme") {
                Text(theme.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(infos, key = { it.permission }) { info ->
                PermissionCard(info, weight = rules.permissionWeights[info.permission])
            }
        }
    }
}

// --- 1. Le score ------------------------------------------------------------------------------

@Composable
private fun ScoreOverview(rules: Rules) {
    val scoring = rules.scoring
    LearnCard("Le Privacy Score en bref") {
        Paragraph(
            "Chaque application part de ${scoring.base} points. Elle en perd selon les autorisations qu'elle " +
                "demande et les traqueurs qu'elle embarque. Tout est calculé sur votre téléphone, sans connexion."
        )
        Formula("Score = ${scoring.base} − permissions (max ${scoring.permissionCap}) − traqueurs (max ${scoring.trackerCap})")
        Paragraph(
            "La pénalité des permissions n'est pas la même pour toutes les apps : elle est pondérée par l'usage " +
                "attendu pour la catégorie de l'app. Le GPS ne coûte rien à une app de navigation, qui en a besoin, " +
                "mais coûte cher à un jeu, qui n'en a aucune raison (détails en 1 et 3 ci-dessous)."
        )
        Paragraph(
            "Les plafonds évitent qu'un seul aspect écrase tout : une app ne perd jamais plus de " +
                "${scoring.permissionCap} points pour ses permissions ni plus de ${scoring.trackerCap} pour ses traqueurs."
        )
        Subtitle("La note et le risque associé")
        val bands = GradeBands.from(scoring)
        GradeScale(bands, base = scoring.base, modifier = Modifier.padding(vertical = 4.dp))
        bands.forEach { band -> GradeRiskLine(band, scoring) }
        Paragraph(
            "Un « ${scoring.unanalyzedPolicy.gradeSuffix} » après la note (ex. B${scoring.unanalyzedPolicy.gradeSuffix}) " +
                "signale un score provisoire : les traqueurs n'ont pas encore pu être analysés."
        )
        Subtitle("Les zones de couleur")
        BulletLine("Zone verte : 80 et plus")
        BulletLine("Zone orange : de 50 à 79")
        BulletLine("Zone rouge : moins de 50")
    }
}

@Composable
private fun PermissionScoring(rules: Rules) {
    val multipliers = rules.scoring.contextMultipliers
    LearnCard("1. Les permissions") {
        val weights = rules.permissionWeights
        val lightest = weights.minByOrNull { it.value }
        val heaviest = weights.values.maxOrNull()
        if (lightest != null && heaviest != null) {
            val heaviestTitles = weights.filterValues { it == heaviest }.keys.map { PermissionGlossary.find(it)?.title ?: it }
            Paragraph(
                "Chaque permission sensible a un poids selon sa gravité : de ${lightest.value} " +
                    "(${PermissionGlossary.find(lightest.key)?.title ?: lightest.key}) à $heaviest " +
                    "(${heaviestTitles.joinToString().lowercase(Locale.FRANCE)}). Ce poids est ensuite ajusté selon deux questions."
            )
        }
        Formula("Pénalité = poids × contexte × octroi")
        Subtitle("A-t-elle un rapport avec ce que fait l'app ? (contexte)")
        BulletLine("Justifiée (×${multipliers["allowed"].fr()}) : nécessaire à la fonction, ex. le GPS pour une app de navigation.")
        BulletLine("Tolérée (×${multipliers["tolerated"].fr()}) : compréhensible mais pas indispensable.")
        BulletLine("Inattendue (×${multipliers["unexpected"].fr()}) : sans lien évident avec la catégorie de l'app.")
        BulletLine("Interdite (×${multipliers["forbidden"].fr()}) : sans aucune justification, ex. les contacts pour un jeu.")
        Subtitle("L'avez-vous accordée ? (octroi)")
        Paragraph(
            "Une permission accordée compte entièrement. Une permission seulement déclarée (l'app la prévoit mais " +
                "vous ne l'avez pas acceptée) compte pour ×${rules.scoring.declaredOnlyFactor.fr()} : elle reste un signal d'intention."
        )
        val fine = rules.permissionWeights["ACCESS_FINE_LOCATION"]
        val forbidden = multipliers["forbidden"]
        if (fine != null && forbidden != null) {
            Subtitle("Exemple")
            Paragraph(
                "La position précise pèse $fine. Accordée à une app de navigation, où elle est justifiée : " +
                    "$fine × ${multipliers["allowed"].fr()} = 0 point perdu. Accordée à un jeu, où elle est interdite : " +
                    "$fine × ${forbidden.fr()} = ${(fine * forbidden).fr()} points perdus."
            )
        }
        Subtitle("Pour ne pas compter deux fois")
        BulletLine("Position précise et approximative ne comptent qu'une fois (la plus lourde).")
        BulletLine("Photos, vidéos, audio et ancien accès aux fichiers ne comptent qu'une fois.")
        BulletLine("Pour les SMS, la plus lourde compte entièrement, les autres à moitié.")
        BulletLine(
            "L'ancien accès aux fichiers n'est compté que pour les apps conçues pour Android 12 ou antérieur : " +
                "au-delà, Android l'ignore."
        )
    }
}

@Composable
private fun TrackerScoring(rules: Rules) {
    val trackerRules = rules.trackerRules
    val policy = rules.scoring.unanalyzedPolicy
    LearnCard("2. Les traqueurs") {
        Paragraph(
            "Un traqueur est un morceau de code fourni par une autre entreprise (souvent une régie publicitaire) " +
                "et intégré à l'app. Il collecte des informations sur vous et les envoie à ses propres serveurs, " +
                "en plus de l'éditeur de l'app."
        )
        Paragraph(
            "VYLIA les repère en cherchant leurs signatures dans le code de chaque app, à partir de la base " +
                "publique Exodus Privacy. Chaque traqueur coûte des points selon ce qu'il fait ; un traqueur " +
                "qui a plusieurs usages compte pour le plus grave."
        )
        trackerRules.categoryWeights.entries.sortedByDescending { it.value }.forEach { (category, weight) ->
            WeightLine(trackerCategoryName(category), weight, trackerCategoryExplanation(category))
        }
        WeightLine("Sans catégorie connue", trackerRules.defaultWeight, "Traqueur dont l'usage n'est pas documenté.")
        Paragraph("Les traqueurs ne peuvent pas faire perdre plus de ${rules.scoring.trackerCap} points au total.")
        Subtitle("Si l'analyse n'a pas pu se faire")
        Paragraph(
            "En attendant l'analyse (ou si l'app n'a pas pu être lue), on retire forfaitairement " +
                "${policy.assumedTrackerPenalty} points et la note porte un « ${policy.gradeSuffix} »."
        )
        Subtitle("Limite")
        Paragraph(
            "La détection repose sur des noms de code connus. Un traqueur caché (code obfusqué) ou trop récent " +
                "peut passer inaperçu : « aucun traqueur détecté » n'est pas une garantie absolue."
        )
    }
}

@Composable
private fun CategoryResolution(rules: Rules) {
    LearnCard("3. La catégorie de l'app") {
        Paragraph(
            "Ce qui est « justifié » dépend de ce que fait l'app : le micro est normal pour Shazam, pas pour une " +
                "calculatrice. VYLIA classe chaque app dans l'une de ${rules.categories.size} catégories, d'après une liste " +
                "d'apps connues ou, à défaut, la catégorie déclarée par l'éditeur."
        )
        Paragraph(
            "Une app « Non catégorisée » n'a aucun passe-droit : chaque permission sensible y est considérée comme inattendue."
        )
    }
}

private fun trackerCategoryName(category: String): String = when (category) {
    "Advertisement" -> "Publicité"
    "Profiling" -> "Profilage"
    "Location" -> "Localisation"
    "Identification" -> "Identification"
    "Analytics" -> "Mesure d'audience"
    "Crash reporting" -> "Rapports de plantage"
    else -> category
}

private fun trackerCategoryExplanation(category: String): String = when (category) {
    "Advertisement" -> "Affiche des publicités ciblées et suit vos réactions pour mieux vous cibler."
    "Profiling" -> "Construit un profil de vos centres d'intérêt et comportements, souvent croisé ou revendu."
    "Location" -> "Collecte votre position pour la publicité ou la revente de données."
    "Identification" -> "Relie votre activité à un identifiant pour vous reconnaître d'une app ou d'un service à l'autre."
    "Analytics" -> "Mesure votre usage de l'app (écrans vus, clics, durée) pour le compte de l'éditeur et du fournisseur."
    "Crash reporting" -> "Envoie un rapport technique quand l'app plante. Peu intrusif, mais il décrit votre appareil."
    else -> ""
}

@Composable
private fun GradeRiskLine(band: GradeBand, scoring: Scoring) {
    val color = scoreGradientColor((band.min + band.max) / 2, scoring.base)
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Surface(color = color, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(band.grade, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${gradeRiskLabel(band.grade)} · ${band.min} à ${band.max}", fontWeight = FontWeight.Bold, color = color)
            Text(gradeRiskDescription(band, scoring), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Description du risque, complétée par ce que la formule impose réellement pour tomber dans la tranche. */
private fun gradeRiskDescription(band: GradeBand, scoring: Scoring): String {
    val profile = when (band.grade) {
        "A" -> "Peu ou pas de permissions sensibles, et elles servent la fonction de l'app ; aucun traqueur ou presque."
        "B" -> "Quelques permissions tolérées ou un petit nombre de traqueurs, souvent de mesure d'audience ou de rapports de plantage."
        "C" -> "Des permissions sans rapport avec la fonction de l'app, ou plusieurs traqueurs dont des publicitaires. À surveiller."
        "D" -> "Typiquement de nombreux traqueurs publicitaires ou de profilage, souvent avec des permissions intrusives. Cherchez une alternative."
        "E" -> "Collecte massive de données. À remplacer si possible."
        else -> ""
    }
    val permissions = band.permissionPenaltyAbove
    val trackers = band.trackerPenaltyAbove
    val constraint = when {
        permissions != null && trackers != null ->
            "Sous ${band.max + 1}, une app cumule forcément des permissions intrusives (plus de $permissions points) " +
                "et des traqueurs importants (plus de $trackers points)."
        trackers != null ->
            "Les permissions seules (plafonnées à ${scoring.permissionCap}) ne peuvent pas faire descendre une app sous " +
                "${band.max + 1} : les traqueurs pèsent forcément" + (if (trackers > 0) ", plus de $trackers points." else ".")
        else -> ""
    }
    return listOf(profile, constraint).filter { it.isNotEmpty() }.joinToString(" ")
}

// --- 2. Lexique -------------------------------------------------------------------------------

@Composable
private fun PermissionCard(info: PermissionInfo, weight: Int?) {
    var expanded by rememberSaveable(info.permission) { mutableStateOf(false) }
    VyliaCard(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.title, fontWeight = FontWeight.Bold)
                    Text(
                        info.permission,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransparencyBadge(info.grant.label)
                if (weight != null) TransparencyBadge("Poids $weight")
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                PermissionExplanation(info)
            }
        }
    }
}

/** Explication d'une permission, partagée avec l'écran de détail d'une app. */
@Composable
fun PermissionExplanation(info: PermissionInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledText("Ce que ça permet", info.allows)
        LabeledText("Pourquoi c'est sensible", info.whySensitive)
        LabeledText("Comment elle est accordée", info.grant.explanation)
    }
}

@Composable
private fun LabeledText(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun PermissionInfo.matches(query: String): Boolean {
    val needle = query.trim().normalized()
    return needle.isEmpty() || listOf(title, permission, allows, theme.title).any { needle in it.normalized() }
}

private fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

// --- Briques communes -------------------------------------------------------------------------

@Composable
private fun LearnCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Subtitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BulletLine(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Formula(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeightLine(label: String, weight: Int, explanation: String) {
    Row {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            if (explanation.isNotEmpty()) {
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("−$weight", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

/** Nombre au format français, sans décimale inutile (0,4 ; 12,8 ; 0). */
private fun Double?.fr(): String {
    if (this == null) return "?"
    return String.format(Locale.FRANCE, "%.2f", this).trimEnd('0').trimEnd(',')
}
