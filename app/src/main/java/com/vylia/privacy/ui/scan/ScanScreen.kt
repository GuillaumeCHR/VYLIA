package com.vylia.privacy.ui.scan

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import android.Manifest
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.Normalizer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.DetectedPermission
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.scoring.PermissionRegistry
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.ScoreResult
import androidx.compose.material3.TextButton
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.domain.scoring.Rules
import com.vylia.privacy.ui.scan.components.AppCard
import com.vylia.privacy.ui.scan.components.CategoryExpectationsCard
import com.vylia.privacy.ui.scan.components.GradeFilterRow
import com.vylia.privacy.ui.scan.components.GradeFilterSummary
import com.vylia.privacy.ui.scan.components.LastScanLine
import com.vylia.privacy.ui.theme.VyliaTheme
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.ui.res.pluralStringResource
import com.vylia.privacy.ui.components.BadgeChip
import com.vylia.privacy.ui.components.badgeDescription
import com.vylia.privacy.ui.components.badgeEmoji
import com.vylia.privacy.ui.components.badgeLabel
import com.vylia.privacy.ui.components.SearchField
import com.vylia.privacy.ui.components.VyliaLogotype
import androidx.compose.ui.platform.LocalResources
import com.vylia.privacy.ui.components.categoryName

private val Tabs = listOf(AppFilter.USER, AppFilter.SYSTEM)

@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    onAppClick: (AuditedApp) -> Unit = {},
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedRules by viewModel.rules.collectAsStateWithLifecycle()
    var gradeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var scanTab by rememberSaveable { mutableStateOf(ScanTab.RISK) }
    var badgeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    ScanContent(
        modifier = modifier,
        state = state,
        rules = loadedRules.rules,
        gradeFilter = gradeFilter,
        onGradeFilter = { gradeFilter = it },
        scanTab = scanTab,
        onScanTab = { scanTab = it },
        badgeFilter = badgeFilter,
        onBadgeFilter = { badgeFilter = it },
        categoryFilter = categoryFilter,
        onCategoryFilter = { categoryFilter = it },
        onFilterSelected = viewModel::selectFilter,
        onRefresh = viewModel::refresh,
        onAppClick = onAppClick,
    )
}

/** Version sans ViewModel, pour les previews et les tests d'UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContent(
    modifier: Modifier = Modifier,
    state: ScanUiState,
    rules: Rules? = null,
    gradeFilter: String? = null,
    onGradeFilter: (String?) -> Unit = {},
    scanTab: ScanTab = ScanTab.RISK,
    onScanTab: (ScanTab) -> Unit = {},
    badgeFilter: String? = null,
    onBadgeFilter: (String?) -> Unit = {},
    categoryFilter: String? = null,
    onCategoryFilter: (String?) -> Unit = {},
    onFilterSelected: (AppFilter) -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (AuditedApp) -> Unit = {},
) {
    // La barre d'en-tête (logotype, bouclier, recherche globale) est celle de l'écran principal.
    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = scanTab.ordinal) {
            ScanTab.entries.forEach { tab ->
                Tab(selected = scanTab == tab, onClick = { onScanTab(tab) }, text = { Text(stringResource(tab.title)) })
            }
        }

        LastScanLine(
            lastScanAt = state.lastScanAt,
            scanning = state.isLoading || state.isAnalyzingTrackers,
            onRefresh = onRefresh,
            refreshEnabled = !state.isLoading,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        )

        // Mode Avancé : choix entre apps utilisateur et apps système.
        if (state.advancedMode) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tabs.forEach { filter ->
                    FilterChip(
                        selected = filter == state.selectedFilter,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    AppFilter.SYSTEM -> stringResource(R.string.tab_system_apps, state.systemAppCount)
                                    else -> stringResource(R.string.tab_user_apps, state.userAppCount)
                                }
                            )
                        },
                    )
                }
            }
        }

        val contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
        when {
            state.isLoading -> CenteredMessage(stringResource(R.string.home_scanning), showProgress = true)
            state.errorMessage != null -> CenteredMessage(
                message = stringResource(R.string.home_error, state.errorMessage),
                actionLabel = stringResource(R.string.home_retry),
                onAction = onRefresh,
            )
            state.apps.isEmpty() -> CenteredMessage(stringResource(R.string.home_empty))
            scanTab == ScanTab.RISK -> RiskTab(state.apps, rules, gradeFilter, onGradeFilter, onAppClick, contentPadding)
            scanTab == ScanTab.CATEGORY -> CategoryTab(state.apps, rules, categoryFilter, onCategoryFilter, onAppClick, contentPadding)
            else -> BadgeTab(state.apps, rules, badgeFilter, onBadgeFilter, onAppClick, contentPadding)
        }
    }
}

/** Onglets de l'écran Scan : trois façons de parcourir les mêmes applis. */
enum class ScanTab(@param:StringRes val title: Int) {
    RISK(R.string.scan_tab_risk),
    CATEGORY(R.string.scan_tab_category),
    BADGES(R.string.scan_tab_badges),
}

/** Par niveau de risque : de la plus intrusive à la plus vertueuse, filtrable par note. */
@Composable
private fun RiskTab(
    apps: List<AuditedApp>,
    rules: Rules?,
    gradeFilter: String?,
    onGradeFilter: (String?) -> Unit,
    onAppClick: (AuditedApp) -> Unit,
    contentPadding: PaddingValues,
) {
    val gradeSuffix = rules?.scoring?.unanalyzedPolicy?.gradeSuffix ?: "?"
    val bands = remember(rules) { rules?.let { GradeBands.from(it.scoring) }.orEmpty() }
    fun AuditedApp.letter() = result.grade.removeSuffix(gradeSuffix)
    val results = apps.filter { gradeFilter == null || it.letter() == gradeFilter }
    AppList(
        apps = results,
        onAppClick = onAppClick,
        contentPadding = contentPadding,
        header = {
            if (rules != null) {
                GradeFilterRow(
                    bands = bands,
                    scoreBase = rules.scoring.base,
                    counts = apps.groupingBy { it.letter() }.eachCount(),
                    selected = gradeFilter,
                    onSelect = onGradeFilter,
                )
            }
            bands.find { it.grade == gradeFilter }?.let { band ->
                GradeFilterSummary(band, rules?.scoring?.base ?: 100, results.size)
            }
        },
        emptyContent = if (gradeFilter != null) {
            {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(stringResource(R.string.home_filters_empty), textAlign = TextAlign.Center)
                    TextButton(onClick = { onGradeFilter(null) }) { Text(stringResource(R.string.home_filters_reset)) }
                }
            }
        } else null,
    )
}

/**
 * Par catégorie d'app : une section par catégorie (ordre alphabétique), apps les plus à risque d'abord.
 * Toucher l'en-tête affiche ce que la catégorie justifie ou tolère.
 */
@Composable
private fun CategoryTab(
    apps: List<AuditedApp>,
    rules: Rules?,
    categoryFilter: String?,
    onCategoryFilter: (String?) -> Unit,
    onAppClick: (AuditedApp) -> Unit,
    contentPadding: PaddingValues,
) {
    val resources = LocalResources.current
    val groups = remember(apps, rules, resources) {
        apps.groupBy { it.result.categoryId }
            .map { (id, list) -> Triple(id, resources.categoryName(id, rules), list) }
            .sortedBy { it.second }
    }
    // Une catégorie choisie qui n'a plus d'app (recherche, désinstallation) revient à « Toutes ».
    val selected = categoryFilter?.takeIf { id -> groups.any { it.first == id } }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Accès rapide, comme l'onglet Par badge : une ligne de pastilles qui défile, les plus fournies d'abord.
        item(key = "category-chips") {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onCategoryFilter(null) },
                    label = { Text("${stringResource(R.string.filter_all_grades)} · ${apps.size}") },
                )
                groups.sortedByDescending { it.third.size }.forEach { (id, name, list) ->
                    FilterChip(
                        selected = selected == id,
                        onClick = { onCategoryFilter(if (selected == id) null else id) },
                        label = { Text("$name · ${list.size}") },
                    )
                }
            }
        }
        groups.filter { selected == null || it.first == selected }.forEach { (id, name, list) ->
            item(key = "category-$id") {
                CategoryHeader(
                    name = name,
                    count = list.size,
                    expanded = expanded == id,
                    onToggle = { expanded = if (expanded == id) null else id },
                )
                val category = rules?.categories?.get(id)
                if (expanded == id && category != null) {
                    CategoryExpectationsCard(id, category, rules, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(list, key = { "$id-${it.app.packageName}" }) { AppCard(it, onClick = { onAppClick(it) }) }
        }
    }
}

/**
 * Par badges : vue transversale des qualifications. On choisit un badge (bonnes pratiques d'abord),
 * et la liste montre les apps qui l'ont obtenu.
 */
@Composable
private fun BadgeTab(
    apps: List<AuditedApp>,
    rules: Rules?,
    badgeFilter: String?,
    onBadgeFilter: (String?) -> Unit,
    onAppClick: (AuditedApp) -> Unit,
    contentPadding: PaddingValues,
) {
    val counts = remember(apps) { apps.flatMap { app -> app.result.badges.map { it.id } }.groupingBy { it }.eachCount() }
    val polarityOrder = listOf("positive", "negative", "neutral")
    val present = rules?.badges?.definitions.orEmpty()
        .filter { it.id in counts }
        .sortedBy { polarityOrder.indexOf(it.polarity) }
    val selected = present.find { it.id == badgeFilter } ?: present.firstOrNull()
    val resources = LocalResources.current
    AppList(
        apps = selected?.let { badge -> apps.filter { app -> app.result.badges.any { it.id == badge.id } } }.orEmpty(),
        onAppClick = onAppClick,
        contentPadding = contentPadding,
        header = {
            // Une seule ligne qui défile : la liste des apps reste visible sans faire défiler l'écran.
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                present.forEach { badge ->
                    FilterChip(
                        selected = badge == selected,
                        onClick = { onBadgeFilter(badge.id) },
                        label = { Text("${badgeEmoji(badge)} ${resources.badgeLabel(badge)} · ${counts[badge.id]}") },
                    )
                }
            }
            selected?.let { badge ->
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    BadgeChip(badge)
                    resources.badgeDescription(badge)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun CategoryHeader(name: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            pluralStringResource(R.plurals.app_count, count, count) + if (expanded) "  ▲" else "  ▼",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppList(
    apps: List<AuditedApp>,
    onAppClick: (AuditedApp) -> Unit,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit = {},
    emptyContent: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { header() } }
        if (apps.isEmpty() && emptyContent != null) item(key = "empty") { emptyContent() }
        items(apps, key = { it.app.packageName }) { AppCard(it, onClick = { onAppClick(it) }) }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showProgress) CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanContentPreview() {
    fun fakeApp(label: String, color: Int, score: Int, vararg permissions: Pair<String, Boolean>): AuditedApp {
        val app = InstalledApp(
            label = label,
            packageName = "com.example.${label.lowercase()}",
            icon = ColorDrawable(color),
            isSystemApp = false,
            category = null,
            apkPaths = emptyList(),
            lastUpdateTime = 0L,
            requestedPermissions = permissions.map { it.first },
            permissions = PermissionRegistry.strongestByFamily(permissions.map { it.first }).map { risk ->
                DetectedPermission(risk, isGranted = permissions.any { (name, granted) -> granted && name == risk.permission })
            },
        )
        val result = ScoreResult(score, "?", Confidence.PARTIAL, "DEFAULT", 0.0, 0, emptyList(), emptyList())
        return AuditedApp(app, PrivacyScore(score), result = result)
    }
    val apps = listOf(
        fakeApp(
            "Messagerie", AndroidColor.BLUE, 41,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to true,
            Manifest.permission.READ_SMS to false,
            Manifest.permission.READ_CONTACTS to true,
        ),
        fakeApp("Photo", AndroidColor.MAGENTA, 74, Manifest.permission.CAMERA to true, Manifest.permission.ACCESS_FINE_LOCATION to false),
        fakeApp("Calculatrice", AndroidColor.GRAY, 88),
    ).sortedBy { it.score.value }

    VyliaTheme(dynamicColor = false) {
        ScanContent(
        modifier = Modifier,
            state = ScanUiState(
                isLoading = false,
                advancedMode = true,
                apps = apps,
                averageScore = PrivacyScore.average(apps.map { it.score }),
                userAppCount = apps.size,
                systemAppCount = 42,
            ),
            onFilterSelected = {},
            onRefresh = {},
        )
    }
}
