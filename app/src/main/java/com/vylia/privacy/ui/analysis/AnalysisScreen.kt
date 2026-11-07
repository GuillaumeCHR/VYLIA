package com.vylia.privacy.ui.analysis

import com.vylia.privacy.ui.settings.SettingsViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.domain.analysis.DeviceAnalysis
import com.vylia.privacy.domain.analysis.PermissionUsage
import com.vylia.privacy.domain.analysis.PurposeAnomaly
import com.vylia.privacy.domain.analysis.TrackerUsage
import com.vylia.privacy.domain.analysis.WatchedPermission
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.scan.ScanViewModel
import com.vylia.privacy.ui.scan.components.ScoreHeader
import com.vylia.privacy.ui.theme.color
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import com.vylia.privacy.ui.components.categoryName
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted
import com.vylia.privacy.ui.components.trackerCategoryName
import java.text.DateFormat
import java.util.Date

/** Nombre de lignes affichées avant "Tout voir" dans chaque liste. */
private const val PreviewCount = 5

/**
 * Résultats transversaux : score global, puis trois sous-onglets : zones de risque, anomalies (adéquation
 * permissions / finalité et apps par permission sensible) et cartographie des traqueurs.
 */
@Composable
fun AnalysisScreen(
    modifier: Modifier = Modifier,
    onAppClick: (AuditedApp) -> Unit = {},
    /** Lien « Modifier » ou « Définir un seuil d'alerte » du Suivi : Paramètres › Automatisations. */
    onOpenAutomation: () -> Unit = {},
    onOpenReduce: () -> Unit = {},
    /** Vue d'ensemble ou Suivi, gardé par l'écran principal (une notification peut ouvrir le Suivi). */
    tab: AnalyzeSection = AnalyzeSection.OVERVIEW,
    onTab: (AnalyzeSection) -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    homeViewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory)
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val analysis = remember(homeState.scannedApps) { DeviceAnalysis.from(homeState.scannedApps) }
    val history by homeViewModel.history.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
    Text(
        text = stringResource(R.string.analysis_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
    // Deux vues : l'état actuel de l'appareil, et son suivi dans le temps (icône de graphique).
    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
        Tab(selected = tab == AnalyzeSection.OVERVIEW, onClick = { onTab(AnalyzeSection.OVERVIEW) }, text = { Text(stringResource(R.string.analysis_tab_overview)) })
        Tab(
            selected = tab == AnalyzeSection.TRACKING,
            onClick = { onTab(AnalyzeSection.TRACKING) },
            text = { Text(stringResource(R.string.analysis_tab_tracking)) },
            icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) },
        )
    }
    if (tab == AnalyzeSection.TRACKING) {
        val alert by settingsViewModel.thresholdAlert.collectAsStateWithLifecycle()
        TrackingTab(history, alert, onOpenAutomation, onOpenReduce)
        return@Column
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Un seul score de confidentialité pour l'appareil : en mode Avancé, il fusionne apps utilisateur et système,
        // comme le reste de l'analyse (zones, traqueurs, anomalies).
        item {
            if (homeState.advancedMode) {
                ScoreHeader(
                    averageScore = homeState.allAverageScore,
                    appCount = homeState.userAppCount + homeState.systemAppCount,
                    filter = AppFilter.ALL,
                    systemAppCount = homeState.systemAppCount,
                )
            } else {
                ScoreHeader(
                    averageScore = homeState.userAverageScore,
                    appCount = homeState.userAppCount,
                    filter = AppFilter.USER,
                )
            }
        }
        if (homeState.isLoading || homeState.isAnalyzingTrackers) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (homeState.isLoading) R.string.analysis_scanning else R.string.analysis_trackers_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Trois vues sous le score, pour ne pas faire défiler tout l'écran : zones de risque, anomalies (avec les apps
        // par permission sensible, qui parlent aussi d'autorisations) et traqueurs.
        item {
            SecondaryTabRow(selectedTabIndex = section, containerColor = Color.Transparent) {
                listOf(R.string.analysis_section_zones, R.string.analysis_section_anomalies, R.string.analysis_section_trackers)
                    .forEachIndexed { index, label ->
                        Tab(selected = section == index, onClick = { section = index }, text = { Text(stringResource(label), textAlign = TextAlign.Center) })
                    }
            }
        }
        when (section) {
            0 -> item { ZonesSection(analysis, onAppClick) }
            1 -> {
                item {
                    val rules by homeViewModel.rules.collectAsStateWithLifecycle()
                    val resources = LocalResources.current
                    AnomaliesSection(analysis.anomalies, { resources.categoryName(it, rules.rules) }, onAppClick)
                }
                item { PermissionsSection(analysis.permissions, onAppClick) }
            }
            else -> item { TrackersSection(analysis, onAppClick) }
        }

        item {
            Text(
                text = stringResource(
                    R.string.analysis_footer,
                    DateFormat.getDateInstance(DateFormat.SHORT, currentLocale()).format(Date(settingsState.lastUpdateTimestamp)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settingsState.isObsolete) {
                Text(
                    text = stringResource(R.string.analysis_database_obsolete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
}

// --- Zones de risque -------------------------------------------------------------------------

@Composable
private fun ZonesSection(analysis: DeviceAnalysis, onAppClick: (AuditedApp) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(ScoreRating.POOR) }
    val apps = analysis.zones[selected].orEmpty()
    Section(title = stringResource(R.string.analysis_zones), subtitle = stringResource(R.string.analysis_zones_hint)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ScoreRating.POOR, ScoreRating.FAIR, ScoreRating.GOOD).forEach { rating ->
                ZoneTab(
                    rating = rating,
                    count = analysis.zones[rating].orEmpty().size,
                    selected = rating == selected,
                    onClick = { selected = rating },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ExpandableList(apps, key = "zone-$selected", empty = stringResource(R.string.analysis_zone_empty)) { app ->
            AppLine(app, subtitle = stringResource(R.string.grade_label, app.result.grade), onClick = { onAppClick(app) })
        }
    }
}

@Composable
private fun ZoneTab(rating: ScoreRating, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val riskColor = rating.color
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) riskColor.container else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) riskColor.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(rating.zoneName), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@get:StringRes
private val ScoreRating.zoneName: Int
    get() = when (this) {
        ScoreRating.POOR -> R.string.analysis_zone_red
        ScoreRating.FAIR -> R.string.analysis_zone_orange
        ScoreRating.GOOD -> R.string.analysis_zone_green
    }

// --- Adéquation permissions / finalité --------------------------------------------------------

@Composable
private fun AnomaliesSection(
    anomalies: List<PurposeAnomaly>,
    categoryName: (String) -> String,
    onAppClick: (AuditedApp) -> Unit
) {
    Section(
        title = stringResource(R.string.analysis_anomalies, anomalies.size),
        subtitle = stringResource(R.string.analysis_anomalies_hint)
    ) {
        val resources = LocalResources.current
        val locale = currentLocale()
        ExpandableList(anomalies, key = "anomalies", empty = stringResource(R.string.analysis_anomalies_empty)) { anomaly ->
            val details = anomaly.findings.joinToString { finding ->
                val label = WatchedPermission.entries.find { it.permission == finding.permission }?.label?.let(resources::getString)
                    ?: finding.permission
                resources.getString(
                    when {
                        finding.context == Context.FORBIDDEN && finding.granted -> R.string.analysis_anomaly_forbidden
                        finding.context == Context.FORBIDDEN -> R.string.analysis_anomaly_forbidden_declared
                        finding.granted -> R.string.analysis_anomaly_unexpected
                        else -> R.string.analysis_anomaly_unexpected_declared
                    },
                    label,
                )
            }
            AppLine(
                app = anomaly.app,
                subtitle = "${categoryName(anomaly.app.result.categoryId)} · $details",
                trailing = "−" + anomaly.penalty.formatted(locale, decimals = 1),
                onClick = { onAppClick(anomaly.app) }
            )
        }
    }
}

// --- Par permission ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionsSection(permissions: List<PermissionUsage>, onAppClick: (AuditedApp) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<WatchedPermission?>(null) }
    val usage = permissions.find { it.permission == selected } ?: permissions.firstOrNull()
    Section(title = stringResource(R.string.analysis_by_permission), subtitle = stringResource(R.string.analysis_by_permission_hint)) {
        if (usage == null) {
            EmptyLine(stringResource(R.string.analysis_by_permission_empty))
            return@Section
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            permissions.forEach { item ->
                FilterChip(
                    selected = item == usage,
                    onClick = { selected = item.permission },
                    label = { Text("${stringResource(item.permission.label)} · ${item.total}") }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = pluralStringResource(R.plurals.analysis_granted_count, usage.granted.size, usage.granted.size) + ", " +
                pluralStringResource(R.plurals.analysis_declared_count, usage.declaredOnly.size, usage.declaredOnly.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val apps = usage.granted.map { it to true } + usage.declaredOnly.map { it to false }
        ExpandableList(apps, key = "perm-${usage.permission}", empty = "") { (app, granted) ->
            AppLine(
                app = app,
                subtitle = stringResource(if (granted) R.string.analysis_granted else R.string.analysis_declared_only),
                onClick = { onAppClick(app) }
            )
        }
    }
}

@get:StringRes
private val WatchedPermission.label: Int
    get() = when (this) {
        WatchedPermission.MICROPHONE -> R.string.watched_microphone
        WatchedPermission.CAMERA -> R.string.watched_camera
        WatchedPermission.BACKGROUND_LOCATION -> R.string.watched_background_location
        WatchedPermission.FINE_LOCATION -> R.string.watched_fine_location
        WatchedPermission.CONTACTS -> R.string.watched_contacts
        WatchedPermission.SMS -> R.string.watched_sms
        WatchedPermission.CALL_LOG -> R.string.watched_call_log
        WatchedPermission.CALENDAR -> R.string.watched_calendar
        WatchedPermission.ACCESSIBILITY -> R.string.watched_accessibility
        WatchedPermission.NOTIFICATIONS -> R.string.watched_notifications
        WatchedPermission.OVERLAY -> R.string.watched_overlay
        WatchedPermission.USAGE_STATS -> R.string.watched_usage_stats
        WatchedPermission.ALL_FILES -> R.string.watched_all_files
        WatchedPermission.BODY_SENSORS -> R.string.watched_body_sensors
    }

// --- Par traqueurs ----------------------------------------------------------------------------

@Composable
private fun TrackersSection(analysis: DeviceAnalysis, onAppClick: (AuditedApp) -> Unit) {
    var expandedTracker by rememberSaveable { mutableStateOf<Int?>(null) }
    Section(
        title = stringResource(R.string.analysis_trackers, analysis.trackers.size),
        subtitle = pluralStringResource(
            R.plurals.analysis_trackers_hint, analysis.appsWithTrackers, analysis.appsWithTrackers, analysis.analyzedAppCount,
        )
    ) {
        val resources = LocalResources.current
        if (analysis.trackerCategories.isNotEmpty()) {
            val max = analysis.trackerCategories.first().second.coerceAtLeast(1)
            analysis.trackerCategories.forEach { (category, count) ->
                CategoryBar(resources.trackerCategoryName(category), count, count / max.toFloat())
            }
            Spacer(Modifier.height(12.dp))
        }
        ExpandableList(analysis.trackers, key = "trackers", empty = stringResource(R.string.analysis_trackers_empty)) { usage ->
            TrackerLine(
                usage = usage,
                expanded = expandedTracker == usage.tracker.id,
                onToggle = { expandedTracker = if (expandedTracker == usage.tracker.id) null else usage.tracker.id },
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun CategoryBar(label: String, count: Int, fraction: Float) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.app_count, count, count), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun TrackerLine(usage: TrackerUsage, expanded: Boolean, onToggle: () -> Unit, onAppClick: (AuditedApp) -> Unit) {
    val resources = LocalResources.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(usage.tracker.name, fontWeight = FontWeight.Bold)
                Text(
                    usage.tracker.categories.ifEmpty { listOf(DeviceAnalysis.UNCATEGORIZED) }.joinToString { resources.trackerCategoryName(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${pluralStringResource(R.plurals.app_count, usage.apps.size, usage.apps.size)} ${if (expanded) "▲" else "▼"}", style = MaterialTheme.typography.labelLarge)
        }
        if (expanded) {
            usage.apps.forEach { app ->
                AppLine(app, subtitle = null, onClick = { onAppClick(app) }, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

// --- Briques communes -------------------------------------------------------------------------

@Composable
private fun Section(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Les [PreviewCount] premières lignes, puis un bouton pour tout afficher. */
@Composable
private fun <T> ExpandableList(items: List<T>, key: String, empty: String, row: @Composable (T) -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    if (items.isEmpty()) {
        if (empty.isNotEmpty()) EmptyLine(empty)
        return
    }
    (if (expanded) items else items.take(PreviewCount)).forEach { row(it) }
    if (items.size > PreviewCount) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) stringResource(R.string.list_collapse) else stringResource(R.string.list_show_all, items.size))
        }
    }
}

@Composable
private fun AppLine(
    app: AuditedApp,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIcon(app.app.icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Text(trailing, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        ScoreDot(app)
    }
}

/** Score compact, coloré selon la zone. */
@Composable
private fun ScoreDot(app: AuditedApp) {
    val riskColor = app.score.rating.color
    Box(
        modifier = Modifier.size(36.dp).background(riskColor.container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(app.score.value.toString(), color = riskColor.onContainer, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallIcon(drawable: android.graphics.drawable.Drawable) {
    val sizePx = with(LocalDensity.current) { 32.dp.roundToPx() }
    val bitmap = remember(drawable, sizePx) { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(32.dp))
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Onglets d'Analyser. */
enum class AnalyzeSection { OVERVIEW, TRACKING }
