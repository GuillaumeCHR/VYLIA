package com.vylia.privacy.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.data.alert.ThresholdAlertRepository
import com.vylia.privacy.domain.alert.AlertState
import com.vylia.privacy.domain.alert.ThresholdCrossing
import com.vylia.privacy.ui.components.dropCauseText
import com.vylia.privacy.domain.history.EventType
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryEvent
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.history.Influence
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted
import com.vylia.privacy.ui.components.scoreGradientColor
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

private const val EventsPreview = 15

/**
 * Suivi : courbe du score moyen de vos apps dans le temps (avec la bande de l'écart-type), l'alerte de seuil (ligne,
 * franchissements, état), apps qui pèsent le plus sur la moyenne, et historique des installations, suppressions et
 * changements d'autorisations. Le seuil se règle dans Automatisations ([onOpenAutomation]).
 */
@Composable
internal fun TrackingTab(
    history: History,
    alert: ThresholdAlertRepository.State,
    onOpenAutomation: () -> Unit = {},
    onOpenReduce: () -> Unit = {},
) {
    var allEvents by rememberSaveable { mutableStateOf(false) }
    val last = history.snapshots.lastOrNull()
    val influences = remember(last) { last?.let { HistoryRecorder.influences(it) }.orEmpty() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CurveCard(history, alert, onOpenAutomation, onOpenReduce) }
        if (influences.isNotEmpty()) item { InfluenceCard(influences) }
        item {
            Text(stringResource(R.string.tracking_events_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.tracking_events_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (history.events.isEmpty()) {
            item { Text(stringResource(R.string.tracking_events_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(if (allEvents) history.events else history.events.take(EventsPreview)) { EventRow(it) }
        if (history.events.size > EventsPreview) {
            item {
                TextButton(onClick = { allEvents = !allEvents }) {
                    Text(if (allEvents) stringResource(R.string.list_collapse) else stringResource(R.string.list_show_all, history.events.size))
                }
            }
        }
    }
}

/** Score moyen et écart-type dans le temps, sur l'échelle 0–100 (zones à 50 et 80), avec l'alerte de seuil. */
@Composable
private fun CurveCard(history: History, alert: ThresholdAlertRepository.State, onOpenAutomation: () -> Unit, onOpenReduce: () -> Unit) {
    val locale = currentLocale()
    val points = history.snapshots
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tracking_curve_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val last = points.lastOrNull()
            if (last == null) {
                Text(stringResource(R.string.tracking_curve_empty), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            val first = points.first()
            Text(
                stringResource(
                    R.string.tracking_curve_now,
                    last.score.toString(),
                    last.stdDev.formatted(locale, decimals = 0),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (points.size > 1) {
                val delta = last.average - first.average
                val format = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
                Text(
                    stringResource(
                        R.string.tracking_curve_since,
                        (if (delta >= 0) "+" else "−") + abs(delta).formatted(locale, decimals = 1),
                        format.format(Date(first.at)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delta >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                )
            }
            ThresholdStatus(alert, last.score, onOpenAutomation, onOpenReduce)
            val threshold = alert.setting.threshold.takeIf { alert.setting.enabled }
            val crossings = if (alert.setting.enabled) alert.crossings else emptyList()
            var selected by remember(crossings) { mutableStateOf<ThresholdCrossing?>(null) }
            val resources = LocalResources.current
            val dates = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            val description = buildString {
                append(stringResource(R.string.tracking_chart_description, last.score))
                threshold?.let { append(' ').append(stringResource(R.string.tracking_chart_threshold, it)) }
                crossings.lastOrNull()?.let { append(' ').append(stringResource(R.string.tracking_chart_crossing, dates.format(Date(it.at)), it.score)) }
            }
            Curve(
                history,
                threshold,
                crossings,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description },
            )
            val shown = selected
            if (shown != null) {
                Text(
                    stringResource(R.string.tracking_crossing, dates.format(Date(shown.at)), shown.score, resources.dropCauseText(shown.cause)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            } else if (crossings.isNotEmpty()) {
                Text(stringResource(R.string.tracking_crossing_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.tracking_curve_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.size == 1) {
                Text(stringResource(R.string.tracking_curve_one_point), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Ligne d'état de l'alerte au-dessus de la courbe : active (seuil, score), ou sous le seuil depuis une date avec un
 * lien vers Réduire ; désactivée, un simple lien pour en définir une. « Modifier » ouvre Automatisations.
 */
@Composable
private fun ThresholdStatus(alert: ThresholdAlertRepository.State, score: Int, onOpenAutomation: () -> Unit, onOpenReduce: () -> Unit) {
    val setting = alert.setting
    if (!setting.enabled) {
        TextButton(onClick = onOpenAutomation, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.tracking_threshold_set))
        }
        return
    }
    val below = alert.state == AlertState.TRIGGERED
    Text(
        if (below) {
            stringResource(
                R.string.tracking_threshold_below,
                DateFormat.getDateInstance(DateFormat.MEDIUM, currentLocale()).format(Date(alert.since ?: System.currentTimeMillis())),
            )
        } else {
            stringResource(R.string.tracking_threshold_active, setting.threshold, score)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (below) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    Row {
        if (below) {
            TextButton(onClick = onOpenReduce, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tracking_threshold_reduce)) }
        }
        TextButton(onClick = onOpenAutomation, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tracking_threshold_edit)) }
    }
}

/**
 * La courbe. Le seuil est une ligne en pointillés longs, plus épaisse que les zones, avec son étiquette « Seuil 70 » :
 * elle ne repose pas sur la couleur. Chaque franchissement notifié est un anneau sur la courbe ; un appui à moins de
 * 24 dp le sélectionne ([onSelect]).
 */
@Composable
private fun Curve(
    history: History,
    threshold: Int?,
    crossings: List<ThresholdCrossing>,
    onSelect: (ThresholdCrossing?) -> Unit,
    modifier: Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val thresholdColor = MaterialTheme.colorScheme.onSurface
    val markerColor = MaterialTheme.colorScheme.error
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = thresholdColor)
    val label = threshold?.let { stringResource(R.string.tracking_threshold_label, it) }
    val measurer = rememberTextMeasurer()
    val points = history.snapshots
    val start = points.first().at
    val end = points.last().at
    fun xOf(at: Long, width: Float) = if (points.size == 1 || end == start) width / 2 else width * ((at.coerceIn(start, end) - start).toFloat() / (end - start))
    fun yOf(score: Double, height: Float) = height * (1f - (score.coerceIn(0.0, 100.0) / 100.0).toFloat())
    Canvas(
        modifier.pointerInput(crossings) {
            detectTapGestures { tap ->
                val radius = 24.dp.toPx()
                onSelect(
                    crossings.minByOrNull { c ->
                        val dx = xOf(c.at, size.width.toFloat()) - tap.x
                        val dy = yOf(c.score.toDouble(), size.height.toFloat()) - tap.y
                        dx * dx + dy * dy
                    }?.takeIf { c ->
                        val dx = xOf(c.at, size.width.toFloat()) - tap.x
                        val dy = yOf(c.score.toDouble(), size.height.toFloat()) - tap.y
                        dx * dx + dy * dy <= radius * radius
                    }
                )
            }
        }
    ) {
        fun y(score: Double) = yOf(score, size.height)
        fun x(at: Long) = xOf(at, size.width)
        // Seuils des zones de couleur (50 et 80), en pointillés.
        listOf(50.0, 80.0).forEach {
            drawLine(grid, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        if (points.size > 1) {
            val area = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.at), y(p.average + p.stdDev)) else lineTo(x(p.at), y(p.average + p.stdDev)) }
                points.asReversed().forEach { p -> lineTo(x(p.at), y(p.average - p.stdDev)) }
                close()
            }
            drawPath(area, band)
            val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.at), y(p.average)) else lineTo(x(p.at), y(p.average)) } }
            drawPath(line, scoreGradientColor(points.last().average.toInt()), style = Stroke(3.dp.toPx()))
        }
        points.forEach { p -> drawCircle(scoreGradientColor(p.average.toInt()), radius = 4.dp.toPx(), center = Offset(x(p.at), y(p.average))) }
        if (threshold != null && label != null) {
            val ty = y(threshold.toDouble())
            drawLine(thresholdColor, Offset(0f, ty), Offset(size.width, ty), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)))
            val text = measurer.measure(label, labelStyle)
            // Étiquette au-dessus de la ligne, ou dessous si le seuil est tout en haut.
            val top = if (ty - text.size.height - 2.dp.toPx() >= 0) ty - text.size.height - 2.dp.toPx() else ty + 2.dp.toPx()
            drawText(text, topLeft = Offset(size.width - text.size.width - 4.dp.toPx(), top))
        }
        crossings.forEach { c ->
            val center = Offset(x(c.at), y(c.score.toDouble()))
            drawCircle(markerColor, radius = 7.dp.toPx(), center = center, style = Stroke(2.5.dp.toPx()))
        }
    }
}

/** Les apps qui font le plus baisser (ou monter) la moyenne : de combien elle bougerait sans elles. */
@Composable
private fun InfluenceCard(influences: List<Influence>) {
    val locale = currentLocale()
    val down = influences.filter { it.pull < 0 }.take(5)
    val up = influences.filter { it.pull > 0 }.asReversed().take(3)
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tracking_influence_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tracking_influence_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (down + up).forEach { influence ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${influence.label} · ${influence.score}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (influence.pull >= 0) "+" else "−") + abs(influence.pull).formatted(locale, decimals = 1),
                        fontWeight = FontWeight.Bold,
                        color = if (influence.pull >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: HistoryEvent) {
    val resources = LocalResources.current
    val permission = event.permission?.let { p -> PermissionGlossary.titleOf(p)?.let(resources::getString) ?: p.substringAfterLast('.') }
    val (icon, text) = when (event.type) {
        EventType.INSTALLED -> "＋" to stringResource(R.string.tracking_event_installed, event.label)
        EventType.REMOVED -> "－" to stringResource(R.string.tracking_event_removed, event.label)
        EventType.PERMISSION_GRANTED -> "🔓" to stringResource(R.string.tracking_event_granted, permission.orEmpty(), event.label)
        EventType.PERMISSION_REVOKED -> "🔒" to stringResource(R.string.tracking_event_revoked, permission.orEmpty(), event.label)
    }
    val date = DateFormat.getDateInstance(DateFormat.SHORT, currentLocale()).format(Date(event.at))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Text(
                date + (event.score?.let { " · " + stringResource(R.string.tracking_event_score, it) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
