package com.vylia.privacy.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.learn.PermissionClassifier
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.learn.PermissionTheme
import com.vylia.privacy.domain.scoring.Rules
import com.vylia.privacy.ui.components.categoryName
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted

/**
 * La grille de score en vigueur, telle quelle, pour la transparence : poids de chaque permission par thème, groupes
 * qui évitent de compter deux fois, et pour chaque catégorie d'app les permissions justifiées, tolérées ou interdites.
 * Tout est lu dans les règles chargées (intégrées ou importées).
 */
@Composable
fun ScoringGridScreen(modifier: Modifier, rules: Rules, onBack: () -> Unit) {
    SubPage(modifier, R.string.grid_title, onBack) {
        Hint(stringResource(R.string.grid_intro, rules.version))
        ContextLegend(rules)
        WeightsCard(rules)
        GroupsCard(rules)
        if (rules.permissionRequires.isNotEmpty() || rules.consentEachTime.isNotEmpty()) ConditionalCard(rules)
        CategoriesCard(rules)
        if (rules.neutralPermissions.isNotEmpty()) NeutralCard(rules)
    }
}

@Composable
private fun ContextLegend(rules: Rules) {
    val locale = currentLocale()
    val m = rules.scoring.contextMultipliers
    InfoCard(R.string.grid_legend_title) {
        Text(stringResource(R.string.learn_permissions_formula), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        ContextLine(R.string.grid_allowed, m["allowed"].formatted(locale), Allowed)
        ContextLine(R.string.grid_tolerated, m["tolerated"].formatted(locale), Tolerated)
        ContextLine(R.string.grid_unexpected, m["unexpected"].formatted(locale), MaterialTheme.colorScheme.onSurfaceVariant)
        ContextLine(R.string.grid_forbidden, m["forbidden"].formatted(locale), Forbidden)
        Hint(stringResource(R.string.grid_declared_only, rules.scoring.declaredOnlyFactor.formatted(locale), rules.scoring.permissionCap))
    }
}

@Composable
private fun ContextLine(label: Int, multiplier: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(stringResource(label), color)
        Text("×$multiplier", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

/** Poids de chaque permission notée, par thème (repliés : un toucher les ouvre). */
@Composable
private fun WeightsCard(rules: Rules) {
    val byTheme = remember(rules) { rules.permissionWeights.entries.groupBy { PermissionClassifier.theme(it.key) } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    InfoCard(R.string.grid_weights_title) {
        Hint(stringResource(R.string.grid_weights_hint, rules.permissionWeights.size))
        PermissionTheme.entries.forEach { theme ->
            val entries = byTheme[theme] ?: return@forEach
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = if (open == theme.name) null else theme.name }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(theme.title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${entries.size}" + if (open == theme.name) "  ▲" else "  ▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open == theme.name) {
                entries.sortedByDescending { it.value }.forEach { (key, weight) -> WeightRow(key, weight) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun WeightRow(key: String, weight: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(permissionTitle(key), style = MaterialTheme.typography.bodyMedium)
            Text(key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$weight", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/** Groupes : une seule permission compte (la plus lourde), ou les autres à moitié. */
@Composable
private fun GroupsCard(rules: Rules) {
    InfoCard(R.string.grid_groups_title) {
        rules.permissionRules.groups.forEach { group ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    stringResource(if (group.mode == "max_plus_half") R.string.grid_group_half else R.string.grid_group_max),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                val resources = LocalResources.current
                Text(group.members.joinToString { resources.permissionTitle(it) }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Capacités qui ne comptent entièrement que si l'accès dont elles dépendent est accordé. */
@Composable
private fun ConditionalCard(rules: Rules) {
    val resources = LocalResources.current
    InfoCard(R.string.grid_conditional_title) {
        Hint(stringResource(R.string.grid_conditional_hint))
        rules.permissionRequires.forEach { (perm, required) ->
            Text(
                stringResource(R.string.grid_requires, resources.permissionTitle(perm), required.joinToString(" / ") { resources.permissionTitle(it) }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        rules.consentEachTime.forEach {
            Text(stringResource(R.string.grid_consent_each_time, resources.permissionTitle(it)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Permissions volontairement non comptées, et pourquoi. */
@Composable
private fun NeutralCard(rules: Rules) {
    val resources = LocalResources.current
    InfoCard(R.string.grid_neutral_title) {
        Text(stringResource(R.string.grid_neutral_hint), style = MaterialTheme.typography.bodyMedium)
        Text(rules.neutralPermissions.joinToString(" · ") { resources.permissionTitle(it) }, style = MaterialTheme.typography.bodySmall)
    }
}

/** Une catégorie à la fois : ce qu'elle justifie, tolère ou interdit ; le reste est inattendu. */
@Composable
private fun CategoriesCard(rules: Rules) {
    val resources = LocalResources.current
    val categories = remember(rules, resources) {
        rules.categories.keys.filter { it != "DEFAULT" }.sortedBy { resources.categoryName(it, rules) }
    }
    var selected by rememberSaveable { mutableStateOf(categories.firstOrNull()) }
    val category = selected?.let { rules.categories[it] } ?: return
    InfoCard(R.string.grid_categories_title) {
        Hint(stringResource(R.string.grid_categories_hint, categories.size))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { id ->
                FilterChip(selected = id == selected, onClick = { selected = id }, label = { Text(resources.categoryName(id, rules)) })
            }
        }
        ContextList(R.string.grid_allowed, category.allowed, Allowed)
        ContextList(R.string.grid_tolerated, category.tolerated, Tolerated)
        ContextList(R.string.grid_forbidden, category.forbidden, Forbidden)
        Hint(stringResource(R.string.grid_others_unexpected))
    }
}

@Composable
private fun ContextList(label: Int, permissions: Set<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
        Tag("${stringResource(label)} (${permissions.size})", color)
        if (permissions.isEmpty()) {
            Hint(stringResource(R.string.expectations_none))
        } else {
            val resources = LocalResources.current
            Text(permissions.map { resources.permissionTitle(it) }.sorted().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun permissionTitle(key: String): String = LocalResources.current.permissionTitle(key)

private fun android.content.res.Resources.permissionTitle(key: String): String =
    PermissionGlossary.titleOf(key)?.let(::getString) ?: key.substringAfterLast('.')

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

private val Allowed = Color(0xFF10B981)
private val Tolerated = Color(0xFFF59E0B)
private val Forbidden = Color(0xFFEF4444)
