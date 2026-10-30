package com.vylia.privacy.ui.main

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.vylia.privacy.domain.learn.TrackerGlossary
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.home.HomeViewModel
import com.vylia.privacy.ui.home.components.ScoreHeader
import com.vylia.privacy.ui.theme.color

/** Nombre de lignes affichées avant "Tout voir" dans chaque liste. */
private const val PreviewCount = 5

/**
 * Résultats transversaux : score global, zones de risque, adéquation permissions / finalité,
 * apps par permission sensible et cartographie des traqueurs.
 */
@Composable
fun AnalyseScreen(
    modifier: Modifier = Modifier,
    onAppClick: (AuditedApp) -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val analysis = remember(homeState.scannedApps) { DeviceAnalysis.from(homeState.scannedApps) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Analyse", style = MaterialTheme.typography.headlineMedium)
        }

        // Score de confidentialité de l'appareil.
        item {
            ScoreHeader(
                averageScore = homeState.userAverageScore,
                appCount = homeState.userAppCount,
                filter = AppFilter.USER
            )
        }
        if (homeState.advancedMode) {
            item {
                ScoreHeader(
                    averageScore = homeState.systemAverageScore,
                    appCount = homeState.systemAppCount,
                    filter = AppFilter.SYSTEM
                )
            }
        }
        if (homeState.isLoading || homeState.isAnalyzingTrackers) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (homeState.isLoading) "Scan en cours…" else "Analyse des traqueurs en cours : les résultats se complètent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { ZonesSection(analysis, onAppClick) }
        item { AnomaliesSection(analysis.anomalies, homeViewModel::categoryName, onAppClick) }
        item { PermissionsSection(analysis.permissions, onAppClick) }
        item { TrackersSection(analysis, onAppClick) }

        item {
            Text(
                text = "Analyse 100% locale basée sur le référentiel Exodus Privacy du ${settingsState.lastUpdateDate}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settingsState.isObsolete) {
                Text(
                    text = "⚠️ La base de données de traqueurs n'est plus à jour (>90 jours).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// --- Zones de risque -------------------------------------------------------------------------

@Composable
private fun ZonesSection(analysis: DeviceAnalysis, onAppClick: (AuditedApp) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(ScoreRating.POOR) }
    val apps = analysis.zones[selected].orEmpty()
    Section(title = "Zones de risque", subtitle = "Rouge : moins de 50 · Orange : 50 à 79 · Verte : 80 et plus") {
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
        ExpandableList(apps, key = "zone-$selected", empty = "Aucune application dans cette zone.") { app ->
            AppLine(app, subtitle = "Note ${app.result.grade}", onClick = { onAppClick(app) })
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
            Text(rating.zoneName, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val ScoreRating.zoneName: String
    get() = when (this) {
        ScoreRating.POOR -> "Zone rouge"
        ScoreRating.FAIR -> "Zone orange"
        ScoreRating.GOOD -> "Zone verte"
    }

// --- Adéquation permissions / finalité --------------------------------------------------------

@Composable
private fun AnomaliesSection(
    anomalies: List<PurposeAnomaly>,
    categoryName: (String) -> String,
    onAppClick: (AuditedApp) -> Unit
) {
    Section(
        title = "Anomalies de finalité (${anomalies.size})",
        subtitle = "Permissions sans rapport avec la catégorie de l'app : interdites, ou inattendues pour une catégorie connue."
    ) {
        ExpandableList(anomalies, key = "anomalies", empty = "Aucune surexposition de données détectée.") { anomaly ->
            val details = anomaly.findings.joinToString { finding ->
                val label = WatchedPermission.entries.find { it.permission == finding.permission }?.label ?: finding.permission
                val context = if (finding.context == Context.FORBIDDEN) "interdite" else "inattendue"
                "$label ($context${if (finding.granted) "" else ", déclarée"})"
            }
            AppLine(
                app = anomaly.app,
                subtitle = "${categoryName(anomaly.app.result.categoryId)} · $details",
                trailing = "−" + "%.1f".format(anomaly.penalty).removeSuffix(".0").removeSuffix(",0"),
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
    Section(title = "Par permission", subtitle = "Quelles apps peuvent accéder au micro, à la caméra, à votre position…") {
        if (usage == null) {
            EmptyLine("Aucune permission sensible demandée.")
            return@Section
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            permissions.forEach { item ->
                FilterChip(
                    selected = item == usage,
                    onClick = { selected = item.permission },
                    label = { Text("${item.permission.label} · ${item.total}") }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${usage.granted.size} accordée(s), ${usage.declaredOnly.size} seulement déclarée(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val apps = usage.granted.map { it to true } + usage.declaredOnly.map { it to false }
        ExpandableList(apps, key = "perm-${usage.permission}", empty = "") { (app, granted) ->
            AppLine(
                app = app,
                subtitle = if (granted) "Accordée" else "Déclarée, non accordée",
                onClick = { onAppClick(app) }
            )
        }
    }
}

private val WatchedPermission.label: String
    get() = when (this) {
        WatchedPermission.MICROPHONE -> "Micro"
        WatchedPermission.CAMERA -> "Caméra"
        WatchedPermission.BACKGROUND_LOCATION -> "Position en arrière-plan"
        WatchedPermission.FINE_LOCATION -> "Position précise"
        WatchedPermission.CONTACTS -> "Contacts"
        WatchedPermission.SMS -> "SMS"
        WatchedPermission.CALL_LOG -> "Journal d'appels"
        WatchedPermission.CALENDAR -> "Agenda"
        WatchedPermission.ACCESSIBILITY -> "Accessibilité"
        WatchedPermission.NOTIFICATIONS -> "Lecture des notifications"
        WatchedPermission.OVERLAY -> "Superposition"
        WatchedPermission.USAGE_STATS -> "Statistiques d'usage"
        WatchedPermission.ALL_FILES -> "Tous les fichiers"
        WatchedPermission.BODY_SENSORS -> "Capteurs corporels"
    }

// --- Par traqueurs ----------------------------------------------------------------------------

@Composable
private fun TrackersSection(analysis: DeviceAnalysis, onAppClick: (AuditedApp) -> Unit) {
    var expandedTracker by rememberSaveable { mutableStateOf<Int?>(null) }
    Section(
        title = "Traqueurs (${analysis.trackers.size})",
        subtitle = "${analysis.appsWithTrackers} app(s) sur ${analysis.analyzedAppCount} analysée(s) embarquent au moins un traqueur."
    ) {
        if (analysis.trackerCategories.isNotEmpty()) {
            val max = analysis.trackerCategories.first().second.coerceAtLeast(1)
            analysis.trackerCategories.forEach { (category, count) ->
                CategoryBar(TrackerGlossary.displayName(category), count, count / max.toFloat())
            }
            Spacer(Modifier.height(12.dp))
        }
        ExpandableList(analysis.trackers, key = "trackers", empty = "Aucun traqueur détecté pour l'instant.") { usage ->
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
            Text("$count app(s)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(usage.tracker.name, fontWeight = FontWeight.Bold)
                Text(
                    usage.tracker.categories.ifEmpty { listOf(DeviceAnalysis.UNCATEGORIZED) }.joinToString { TrackerGlossary.displayName(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${usage.apps.size} app(s) ${if (expanded) "▲" else "▼"}", style = MaterialTheme.typography.labelLarge)
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
            Text(if (expanded) "Réduire" else "Tout voir (${items.size})")
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
