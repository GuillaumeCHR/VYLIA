package com.vylia.privacy.ui.understand

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
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
import com.vylia.privacy.domain.analysis.GlossaryUsage
import com.vylia.privacy.domain.analysis.GlossaryUsages
import com.vylia.privacy.ui.components.SearchField
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
import com.vylia.privacy.ui.scan.ScanViewModel
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted
import com.vylia.privacy.ui.components.trackerCategoryName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.vylia.privacy.ui.components.categoryName
import com.vylia.privacy.domain.learn.CategoryRationale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import com.vylia.privacy.ui.components.securityLabel
import com.vylia.privacy.domain.scoring.SecurityFinding
import android.content.res.Resources
import java.text.Normalizer
import java.util.Locale

/**
 * Onglets de l'Aide : chaque contenu est à un toucher, sans long défilement.
 * [searchHint] : texte du champ de recherche ; `null` pour un onglet sans recherche (Écrans, Score).
 */
internal enum class UnderstandTab(@param:StringRes val title: Int, @param:StringRes val searchHint: Int?) {
    SCREENS(R.string.understand_tab_screens, null),
    SCORE(R.string.understand_tab_score, null),
    BADGES(R.string.understand_tab_badges, R.string.learn_search_badges_hint),
    TRACKERS(R.string.understand_tab_trackers, R.string.learn_search_trackers_hint),
    PERMISSIONS(R.string.understand_tab_permissions, R.string.learn_search_hint),
}

/**
 * Contenu de l'Aide, le même d'où qu'on l'ouvre : titre, introduction, puis tous les onglets (valeurs lues dans les
 * règles en vigueur). [initialTab] : l'onglet affiché à l'ouverture.
 */
@Composable
internal fun HelpContent(
    initialTab: UnderstandTab = UnderstandTab.SCREENS,
    modifier: Modifier = Modifier,
    homeViewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory)
) {
    val loadedRules by homeViewModel.rules.collectAsStateWithLifecycle()
    val rules = loadedRules.rules
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val tabs = UnderstandTab.entries
    val title = stringResource(R.string.help_title)
    val intro = stringResource(R.string.help_intro)
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchHint = tab.searchHint?.let { stringResource(it) }

    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            // Recherche repliée en loupe ; ouverte, elle remplace le titre.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    if (searchOpen && searchHint != null) {
                        SearchField(query, { query = it }, searchHint)
                    } else {
                        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    }
                }
                if (searchHint != null) {
                    if (searchOpen) {
                        IconButton(onClick = { query = ""; searchOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_clear))
                        }
                    } else {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = searchHint)
                        }
                    }
                }
            }
            Text(
                intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Cinq onglets : la barre défile plutôt que de couper « Traqueurs » ou « Permissions » sur un petit écran.
        PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 16.dp) {
            tabs.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    // Chaque onglet a sa propre recherche : on repart d'un champ vide.
                    onClick = {
                        if (tab != entry) {
                            query = ""
                            searchOpen = false
                        }
                        tab = entry
                    },
                    text = { Text(stringResource(entry.title), maxLines = 1, softWrap = false) },
                )
            }
        }
        when (tab) {
            UnderstandTab.SCREENS -> HowItWorksTab()
            UnderstandTab.SCORE -> ScoreTab(rules, stalkerwareCount = loadedRules.iocs.size)
            UnderstandTab.BADGES -> BadgesTab(rules, homeState.scannedApps, query)
            UnderstandTab.TRACKERS -> TrackersTab(rules, homeState.scannedApps, query)
            UnderstandTab.PERMISSIONS -> PermissionsTab(rules, homeState.scannedApps, query)
        }
    }
}

private val TabContentPadding = PaddingValues(16.dp)

@Composable
private fun ScoreTab(rules: Rules, stalkerwareCount: Int) {
    // Six sections longues : en accordéon, leurs titres tiennent sur un écran et l'on va droit à celle qu'on cherche.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalAccordion provides Accordion(open) { title -> open = if (open == title) null else title }) {
        LazyColumn(contentPadding = TabContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScoreOverview(rules) }
            item { PermissionScoring(rules) }
            item { TrackerScoring(rules) }
            item { CategoryResolution(rules) }
            item { SecurityCapabilities(rules, stalkerwareCount) }
            item { BadgesSummary() }
        }
    }
}

/**
 * « Comment fonctionne VYLIA » : la surface d'exposition et la démarche en étapes (numérotées à l'affichage), puis le mode d'emploi de chaque écran, dans l'ordre de la barre du bas.
 */
@Composable
private fun HowItWorksTab() {
    val steps = buildList {
        add(R.string.howto_identify_title to R.string.howto_identify_text)
        add(R.string.howto_reduce_title to R.string.howto_reduce_text)
        add(R.string.howto_protect_title to R.string.howto_protect_text)
        add(R.string.howto_follow_title to R.string.howto_follow_text)
        add(R.string.howto_celebrate_title to R.string.howto_celebrate_text)
    }
    val screens = buildList {
        add(R.string.help_scan_title to R.string.help_scan_intro)
        add(R.string.help_analyze_title to R.string.help_analyze_intro)
        add(R.string.help_reduce_title to R.string.help_reduce_intro)
        add(R.string.help_replace_title to R.string.help_replace_intro)
        if (BuildConfig.SHIELD_ENABLED) add(R.string.help_protect_title to R.string.help_protect_intro)
        add(R.string.help_webapk_title to R.string.help_webapk_intro)
        add(R.string.help_celebrate_title to R.string.help_celebrate_intro)
    }
    LazyColumn(contentPadding = TabContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LearnCard(stringResource(R.string.howto_exposure_title)) {
                Paragraph(stringResource(R.string.howto_exposure_text))
                steps.forEachIndexed { index, (title, text) ->
                    Subtitle(stringResource(R.string.howto_step, index + 1, stringResource(title)))
                    Paragraph(stringResource(text))
                }
                Text(
                    stringResource(R.string.howto_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.howto_screens),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
        }
        items(screens, key = { it.first }) { (title, text) ->
            LearnCard(stringResource(title)) { Paragraph(stringResource(text)) }
        }
    }
}

@Composable
private fun TrackersTab(rules: Rules, scannedApps: List<AuditedApp>, query: String) {
    val resources = LocalResources.current
    val detected = remember(scannedApps) { DeviceAnalysis.from(scannedApps).trackers }
    fun onDevice(info: TrackerCategoryInfo) = detected.filter { usage ->
        usage.tracker.categories.ifEmpty { listOf(TrackerGlossary.UNCATEGORIZED) }.contains(info.category)
    }
    // Recherche dans la catégorie, ses exemples et les traqueurs trouvés chez vous (ex. « Facebook »).
    val entries = remember(query, detected, resources) {
        TrackerGlossary.entries.filter { info ->
            query.matchesAny(
                listOf(resources.getString(info.name), resources.getString(info.riskLevel), resources.getString(info.risk)) +
                    info.examples + onDevice(info).map { it.tracker.name }
            )
        }
    }
    LazyColumn(contentPadding = TabContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(R.string.learn_trackers_glossary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.learn_search_empty_trackers, query.trim()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(entries, key = { "tracker-${it.category}" }) { info ->
            val weight = if (info.category == TrackerGlossary.UNCATEGORIZED) rules.trackerRules.defaultWeight
            else rules.trackerRules.categoryWeights[info.category]
            TrackerCategoryCard(info, weight, onDevice(info))
        }
    }
}

@Composable
private fun PermissionsTab(rules: Rules, scannedApps: List<AuditedApp>, query: String) {
    val resources = LocalResources.current
    val usages = remember(scannedApps) { GlossaryUsages.of(PermissionGlossary.entries.map { it.permission }, scannedApps) }
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
                PermissionCard(info, weight = rules.permissionWeights[info.permission], usage = usages.getValue(info.permission))
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
        Paragraph(stringResource(R.string.learn_overview_rules))
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
        fun title(permission: String) = PermissionGlossary.titleOf(permission)?.let(resources::getString) ?: permission
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
        if (rules.permissionRequires.isNotEmpty() || rules.consentEachTime.isNotEmpty()) {
            Subtitle(stringResource(R.string.learn_conditional_title))
            Paragraph(stringResource(R.string.learn_conditional_text, rules.scoring.declaredOnlyFactor.formatted(locale)))
        }
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
        BulletLine(stringResource(R.string.learn_no_double_background))
        BulletLine(stringResource(R.string.learn_no_double_health))
        BulletLine(stringResource(R.string.learn_no_double_storage))
        if (rules.neutralPermissions.isNotEmpty()) Paragraph(stringResource(R.string.learn_ads_neutral))
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
        if (rules.neutralPermissions.isNotEmpty()) Paragraph(stringResource(R.string.learn_trackers_ads))
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

/** Patterns de sécurité des règles et le plafond qu'ils imposent au score. */
@Composable
private fun SecurityCapabilities(rules: Rules, stalkerwareCount: Int) {
    val security = rules.security
    if (security.patterns.isEmpty()) return
    val resources = LocalResources.current
    LearnCard(stringResource(R.string.learn_security_title)) {
        // Stalkerwares connus (base d'Echap) : ce que VYLIA vérifie, ses limites, et quoi faire si l'un est trouvé.
        if (stalkerwareCount > 0) {
            Subtitle(stringResource(R.string.learn_stalkerware_title))
            Paragraph(stringResource(R.string.learn_stalkerware_text, stalkerwareCount))
            Paragraph(stringResource(R.string.learn_stalkerware_limit))
            Subtitle(stringResource(R.string.learn_stalkerware_help_title))
            Paragraph(stringResource(R.string.learn_stalkerware_help))
        }
        Paragraph(stringResource(R.string.learn_security_text))
        security.patterns.entries
            .mapNotNull { (id, pattern) -> security.severityEffects[pattern.severity]?.scoreCap?.let { Triple(id, pattern, it) } }
            .sortedBy { it.third }
            .forEach { (id, pattern, cap) ->
                val label = resources.securityLabel(SecurityFinding(id, pattern.label, pattern.severity, pattern.description))
                BulletLine(stringResource(R.string.learn_security_line, label, cap))
            }
    }
}

@Composable
private fun BadgesSummary() {
    LearnCard(stringResource(R.string.learn_badges_title)) {
        Paragraph(stringResource(R.string.learn_badges_text))
    }
}

@Composable
private fun CategoryResolution(rules: Rules) {
    val resources = LocalResources.current
    val locale = currentLocale()
    val multipliers = rules.scoring.contextMultipliers
    // « Non catégorisée » est expliquée au-dessus ; les autres, par ordre alphabétique de leur nom affiché.
    val categories = remember(rules, resources) {
        rules.categories.keys.filter { it != "DEFAULT" }.sortedBy { resources.categoryName(it, rules) }
    }
    var selected by rememberSaveable { mutableStateOf(categories.firstOrNull()) }
    LearnCard(stringResource(R.string.learn_category_title)) {
        Paragraph(stringResource(R.string.learn_category_text, rules.categories.size))
        Paragraph(stringResource(R.string.learn_category_default))
        if (categories.isEmpty()) return@LearnCard
        Subtitle(stringResource(R.string.learn_category_grid_title))
        Paragraph(
            stringResource(
                R.string.learn_category_grid_hint,
                categories.size,
                multipliers["allowed"].formatted(locale),
                multipliers["tolerated"].formatted(locale),
                multipliers["forbidden"].formatted(locale),
                multipliers["unexpected"].formatted(locale),
            )
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { id ->
                FilterChip(
                    selected = id == selected,
                    onClick = { selected = id },
                    label = { Text(resources.categoryName(id, rules)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        val id = selected ?: return@LearnCard
        val category = rules.categories[id] ?: return@LearnCard
        Text(resources.categoryName(id, rules), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        (CategoryRationale.of(id)?.let { stringResource(it) } ?: category.description)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CategoryContext(R.string.learn_category_allowed, category.allowed)
        CategoryContext(R.string.learn_category_tolerated, category.tolerated)
        CategoryContext(R.string.learn_category_forbidden, category.forbidden)
        if (rules.packageExceptions.isNotEmpty()) {
            val exceptions = rules.packageExceptions.entries.joinToString { (pkg, exception) ->
                resources.getString(R.string.learn_category_exception, pkg, exception.allowedExtra.joinToString { resources.permissionLabel(it) }.lowercase(locale))
            }
            Paragraph(stringResource(R.string.learn_category_exceptions, exceptions))
        }
    }
}

/** Une colonne de la grille pour une catégorie : son nom et son nombre (pas seulement une couleur), puis les permissions. */
@Composable
private fun CategoryContext(@StringRes label: Int, permissions: Set<String>) {
    val resources = LocalResources.current
    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label, permissions.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            if (permissions.isEmpty()) stringResource(R.string.expectations_none)
            else permissions.map { resources.permissionLabel(it) }.sorted().joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Nom d'une permission ou d'un ensemble de permissions (« @… ») tel que le lexique le présente. */
private fun Resources.permissionLabel(key: String): String =
    PermissionGlossary.titleOf(key)?.let(::getString) ?: key.removePrefix("@").substringAfterLast('.')

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

/** Apps listées dans une fiche de permission ouverte (accordées d'abord, les plus intrusives en tête). */
private const val PERMISSION_APPS_SHOWN = 8

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionCard(info: PermissionInfo, weight: Int?, usage: GlossaryUsage) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TransparencyBadge(stringResource(info.grant.label))
                TransparencyBadge(if (weight != null) stringResource(R.string.learn_weight, weight) else stringResource(R.string.learn_not_scored))
                // Comme pour les traqueurs : combien de vos apps sont concernées.
                TransparencyBadge(
                    if (usage.declared == 0) stringResource(R.string.learn_perm_no_app)
                    else pluralStringResource(R.plurals.learn_perm_on_device, usage.declared, usage.declared, usage.granted.size)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                PermissionExplanation(info)
                if (usage.declared > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.learn_tracker_on_device), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val shown = (usage.granted.map { it to true } + usage.refused.map { it to false }).take(PERMISSION_APPS_SHOWN)
                    shown.forEach { (app, granted) ->
                        Text(
                            stringResource(
                                if (granted) R.string.learn_perm_app_granted else R.string.learn_perm_app_refused,
                                app.app.label,
                                app.score.value,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (usage.declared > PERMISSION_APPS_SHOWN) {
                        val more = usage.declared - PERMISSION_APPS_SHOWN
                        Text(
                            pluralStringResource(R.plurals.learn_perm_more, more, more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
private fun PermissionInfo.matches(query: String, resources: Resources): Boolean =
    query.matchesAny(listOf(resources.getString(title), permission, resources.getString(allows), resources.getString(theme.title)))

/** Vrai si la recherche est vide ou figure dans l'un des [texts], sans tenir compte de la casse ni des accents. */
internal fun String.matchesAny(texts: List<String>): Boolean {
    val needle = trim().normalized()
    return needle.isEmpty() || texts.any { needle in it.normalized() }
}

private val Diacritics = Regex("\\p{Mn}+")

private fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Diacritics, "").lowercase()

// --- Briques communes -------------------------------------------------------------------------

@Composable
private fun LearnCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val accordion = LocalAccordion.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        if (accordion == null) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            }
            return@VyliaCard
        }
        // Dans un accordéon : le titre seul, touché pour déplier la section (et replier celle qui était ouverte).
        val open = accordion.open == title
        Column(modifier = Modifier.fillMaxWidth()) {
            AccordionHeader(title, open) { accordion.toggle(title) }
            AnimatedVisibility(visible = open) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { content() }
            }
        }
    }
}

/**
 * Titre d'une section repliable de l'Aide (onglets Score et Badges) : 56 dp de haut, lu comme un titre avec son état
 * (déplié ou replié), et une flèche qui le montre.
 */
@Composable
internal fun AccordionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (open) R.string.help_section_expanded else R.string.help_section_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                heading()
                stateDescription = state
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Accordéon de l'onglet Score : une seule section ouverte à la fois, repérée par son titre. */
private class Accordion(val open: String?, val toggle: (String) -> Unit)

private val LocalAccordion = staticCompositionLocalOf<Accordion?> { null }

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
