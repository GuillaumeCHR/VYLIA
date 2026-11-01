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
import com.vylia.privacy.domain.analysis.DeviceAnalysis
import com.vylia.privacy.domain.analysis.TrackerUsage
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.domain.learn.TrackerCategoryInfo
import com.vylia.privacy.domain.learn.TrackerGlossary
import com.vylia.privacy.domain.learn.gradeRiskLabel
import com.vylia.privacy.domain.scoring.Rules
import com.vylia.privacy.domain.scoring.Scoring
import com.vylia.privacy.ui.components.GradeScale
import com.vylia.privacy.ui.components.scoreGradientColor
import com.vylia.privacy.ui.components.TransparencyBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.home.HomeViewModel
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import androidx.annotation.StringRes
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted
import com.vylia.privacy.ui.components.trackerCategoryName
import android.content.res.Resources
import java.text.Normalizer
import java.util.Locale

/** Onglets de l'écran Comprendre : chaque contenu est à un toucher, sans long défilement. */
private enum class UnderstandTab(@param:StringRes val title: Int) {
    SCORE(R.string.understand_tab_score),
    TRACKERS(R.string.understand_tab_trackers),
    PERMISSIONS(R.string.understand_tab_permissions),
}

/**
 * Pédagogie, en trois onglets : 1/ comment le Privacy Score est calculé (valeurs lues dans les règles en vigueur),
 * 2/ lexique des traqueurs, 3/ lexique des permissions sensibles et de leurs impacts.
 */
@Composable
fun ComprendreScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val loadedRules by homeViewModel.rules.collectAsStateWithLifecycle()
    val rules = loadedRules.rules
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(UnderstandTab.SCORE) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.learn_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.learn_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            UnderstandTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(stringResource(entry.title)) })
            }
        }
        when (tab) {
            UnderstandTab.SCORE -> ScoreTab(rules)
            UnderstandTab.TRACKERS -> TrackersTab(rules, homeState.scannedApps)
            UnderstandTab.PERMISSIONS -> PermissionsTab(rules)
        }
    }
}

private val TabContentPadding = PaddingValues(16.dp)

@Composable
private fun ScoreTab(rules: Rules) {
    LazyColumn(contentPadding = TabContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScoreOverview(rules) }
        item { PermissionScoring(rules) }
        item { TrackerScoring(rules) }
        item { CategoryResolution(rules) }
    }
}

@Composable
private fun TrackersTab(rules: Rules, scannedApps: List<AuditedApp>) {
    val detected = remember(scannedApps) { DeviceAnalysis.from(scannedApps).trackers }
    LazyColumn(contentPadding = TabContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(R.string.learn_trackers_glossary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(TrackerGlossary.entries, key = { "tracker-${it.category}" }) { info ->
            val weight = if (info.category == TrackerGlossary.UNCATEGORIZED) rules.trackerRules.defaultWeight
            else rules.trackerRules.categoryWeights[info.category]
            val onDevice = detected.filter { usage ->
                usage.tracker.categories.ifEmpty { listOf(TrackerGlossary.UNCATEGORIZED) }.contains(info.category)
            }
            TrackerCategoryCard(info, weight, onDevice)
        }
    }
}

@Composable
private fun PermissionsTab(rules: Rules) {
    var query by rememberSaveable { mutableStateOf("") }
    val resources = LocalResources.current
    val glossary = remember(query, resources) {
        PermissionGlossary.entries.filter { it.matches(query, resources) }.groupBy { it.theme }
    }
    LazyColumn(contentPadding = TabContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(R.string.learn_permissions_glossary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.learn_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (glossary.isEmpty()) {
            item {
                Text(stringResource(R.string.learn_search_empty, query.trim()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PermissionTheme.entries.forEach { theme ->
            val infos = glossary[theme] ?: return@forEach
            item(key = "theme-$theme") {
                Text(stringResource(theme.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
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
    LearnCard(stringResource(R.string.learn_overview_title)) {
        Paragraph(stringResource(R.string.learn_overview_intro, scoring.base))
        Formula(stringResource(R.string.learn_overview_formula, scoring.base, scoring.permissionCap, scoring.trackerCap))
        Paragraph(stringResource(R.string.learn_overview_weighting))
        Paragraph(stringResource(R.string.learn_overview_caps, scoring.permissionCap, scoring.trackerCap))
        Subtitle(stringResource(R.string.learn_overview_grades))
        val bands = GradeBands.from(scoring)
        GradeScale(bands, base = scoring.base, modifier = Modifier.padding(vertical = 4.dp))
        bands.forEach { band -> GradeRiskLine(band, scoring) }
        val suffix = scoring.unanalyzedPolicy.gradeSuffix
        Paragraph(stringResource(R.string.learn_overview_partial, suffix, "B$suffix"))
        Subtitle(stringResource(R.string.learn_overview_zones))
        BulletLine(stringResource(R.string.learn_zone_green))
        BulletLine(stringResource(R.string.learn_zone_orange))
        BulletLine(stringResource(R.string.learn_zone_red))
    }
}

@Composable
private fun PermissionScoring(rules: Rules) {
    val multipliers = rules.scoring.contextMultipliers
    val locale = currentLocale()
    LearnCard(stringResource(R.string.learn_permissions_title)) {
        val weights = rules.permissionWeights
        val lightest = weights.minByOrNull { it.value }
        val heaviest = weights.values.maxOrNull()
        val resources = LocalResources.current
        fun title(permission: String) = PermissionGlossary.find(permission)?.title?.let(resources::getString) ?: permission
        if (lightest != null && heaviest != null) {
            val heaviestTitles = weights.filterValues { it == heaviest }.keys.map(::title)
            Paragraph(
                stringResource(
                    R.string.learn_permissions_weights,
                    lightest.value, title(lightest.key), heaviest, heaviestTitles.joinToString().lowercase(locale),
                )
            )
        }
        Formula(stringResource(R.string.learn_permissions_formula))
        Subtitle(stringResource(R.string.learn_context_title))
        BulletLine(stringResource(R.string.learn_context_allowed, multipliers["allowed"].formatted(locale)))
        BulletLine(stringResource(R.string.learn_context_tolerated, multipliers["tolerated"].formatted(locale)))
        BulletLine(stringResource(R.string.learn_context_unexpected, multipliers["unexpected"].formatted(locale)))
        BulletLine(stringResource(R.string.learn_context_forbidden, multipliers["forbidden"].formatted(locale)))
        Subtitle(stringResource(R.string.learn_grant_title))
        Paragraph(stringResource(R.string.learn_grant_text, rules.scoring.declaredOnlyFactor.formatted(locale)))
        val fine = rules.permissionWeights["ACCESS_FINE_LOCATION"]
        val forbidden = multipliers["forbidden"]
        if (fine != null && forbidden != null) {
            Subtitle(stringResource(R.string.learn_example_title))
            Paragraph(
                stringResource(
                    R.string.learn_example_text,
                    fine, multipliers["allowed"].formatted(locale), forbidden.formatted(locale), (fine * forbidden).formatted(locale),
                )
            )
        }
        Subtitle(stringResource(R.string.learn_no_double_count))
        BulletLine(stringResource(R.string.learn_no_double_location))
        BulletLine(stringResource(R.string.learn_no_double_media))
        BulletLine(stringResource(R.string.learn_no_double_sms))
        BulletLine(stringResource(R.string.learn_no_double_storage))
    }
}

@Composable
private fun TrackerScoring(rules: Rules) {
    val trackerRules = rules.trackerRules
    val policy = rules.scoring.unanalyzedPolicy
    val resources = LocalResources.current
    LearnCard(stringResource(R.string.learn_trackers_title)) {
        Paragraph(stringResource(R.string.learn_trackers_what))
        Paragraph(stringResource(R.string.learn_trackers_how))
        trackerRules.categoryWeights.entries.sortedByDescending { it.value }.forEach { (category, weight) ->
            WeightLine(resources.trackerCategoryName(category), weight, "")
        }
        WeightLine(stringResource(R.string.tracker_cat_uncategorized), trackerRules.defaultWeight, "")
        Paragraph(stringResource(R.string.learn_trackers_see_glossary))
        Paragraph(stringResource(R.string.learn_trackers_cap, rules.scoring.trackerCap))
        Subtitle(stringResource(R.string.learn_trackers_unanalyzed_title))
        Paragraph(stringResource(R.string.learn_trackers_unanalyzed_text, policy.assumedTrackerPenalty, policy.gradeSuffix))
        Subtitle(stringResource(R.string.learn_limit_title))
        Paragraph(stringResource(R.string.learn_limit_text))
    }
}

@Composable
private fun CategoryResolution(rules: Rules) {
    LearnCard(stringResource(R.string.learn_category_title)) {
        Paragraph(stringResource(R.string.learn_category_text, rules.categories.size))
        Paragraph(stringResource(R.string.learn_category_default))
    }
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
            Text(
                stringResource(R.string.learn_grade_line, stringResource(gradeRiskLabel(band.grade)), band.min, band.max),
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(gradeRiskDescription(band, scoring), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Description du risque, complétée par ce que la formule impose réellement pour tomber dans la tranche. */
@Composable
private fun gradeRiskDescription(band: GradeBand, scoring: Scoring): String {
    val profile = when (band.grade) {
        "A" -> stringResource(R.string.learn_grade_a)
        "B" -> stringResource(R.string.learn_grade_b)
        "C" -> stringResource(R.string.learn_grade_c)
        "D" -> stringResource(R.string.learn_grade_d)
        "E" -> stringResource(R.string.learn_grade_e)
        else -> ""
    }
    val permissions = band.permissionPenaltyAbove
    val trackers = band.trackerPenaltyAbove
    val constraint = when {
        permissions != null && trackers != null ->
            stringResource(R.string.learn_grade_both, band.max + 1, permissions, trackers)
        trackers != null && trackers > 0 ->
            stringResource(R.string.learn_grade_trackers_more, scoring.permissionCap, band.max + 1, trackers)
        trackers != null ->
            stringResource(R.string.learn_grade_trackers, scoring.permissionCap, band.max + 1)
        else -> ""
    }
    return listOf(profile, constraint).filter { it.isNotEmpty() }.joinToString(" ")
}

// --- Lexique des traqueurs -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackerCategoryCard(info: TrackerCategoryInfo, weight: Int?, onDevice: List<TrackerUsage>) {
    var expanded by rememberSaveable(info.category) { mutableStateOf(false) }
    VyliaCard(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(info.name), fontWeight = FontWeight.Bold)
                    Text(stringResource(info.riskLevel), style = MaterialTheme.typography.labelMedium, color = riskLevelColor(weight))
                }
                Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (weight != null) TransparencyBadge(stringResource(R.string.learn_points_per_tracker, weight))
                TransparencyBadge(
                    if (onDevice.isEmpty()) stringResource(R.string.learn_none_on_device)
                    else pluralStringResource(R.plurals.learn_detected_on_device, onDevice.size, onDevice.size)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledText(stringResource(R.string.learn_tracker_risk), stringResource(info.risk))
                    if (info.examples.isNotEmpty()) {
                        Text(stringResource(R.string.learn_tracker_examples), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            info.examples.forEach { TransparencyBadge(it) }
                        }
                    }
                    if (onDevice.isNotEmpty()) {
                        Text(stringResource(R.string.learn_tracker_on_device), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        onDevice.take(5).forEach { usage ->
                            Text(
                                pluralStringResource(R.plurals.learn_tracker_apps, usage.apps.size, usage.tracker.name, usage.apps.size),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (onDevice.size > 5) {
                            Text(
                                pluralStringResource(R.plurals.learn_tracker_more, onDevice.size - 5, onDevice.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Couleur du niveau de risque, sur l'échelle du score (poids 12 ≈ rouge, 2 ≈ vert). */
@Composable
private fun riskLevelColor(weight: Int?): Color =
    if (weight == null) MaterialTheme.colorScheme.onSurfaceVariant else scoreGradientColor(100 - weight * 7)

// --- 2. Lexique -------------------------------------------------------------------------------

@Composable
private fun PermissionCard(info: PermissionInfo, weight: Int?) {
    var expanded by rememberSaveable(info.permission) { mutableStateOf(false) }
    VyliaCard(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(info.title), fontWeight = FontWeight.Bold)
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
                TransparencyBadge(stringResource(info.grant.label))
                TransparencyBadge(if (weight != null) stringResource(R.string.learn_weight, weight) else stringResource(R.string.learn_not_scored))
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
        LabeledText(stringResource(R.string.learn_perm_allows), stringResource(info.allows))
        LabeledText(stringResource(R.string.learn_perm_why), stringResource(info.whySensitive))
        LabeledText(stringResource(R.string.learn_perm_grant), stringResource(info.grant.explanation))
    }
}

@Composable
private fun LabeledText(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Recherche dans les textes de la langue affichée, ainsi que dans le nom technique de la permission. */
private fun PermissionInfo.matches(query: String, resources: Resources): Boolean {
    val needle = query.trim().normalized()
    return needle.isEmpty() ||
        listOf(resources.getString(title), permission, resources.getString(allows), resources.getString(theme.title))
            .any { needle in it.normalized() }
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
