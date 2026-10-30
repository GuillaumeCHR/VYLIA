package com.vylia.privacy.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.DetectedPermission
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.ui.components.ScoreBadge

private val IconSize = 44.dp

/** Carte d'une application : icône, nom, badge de score et permissions critiques. */
@Composable
fun AppCard(auditedApp: AuditedApp, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val app = auditedApp.app
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TrackerSummary(auditedApp.trackerAnalysis)
                PermissionSummary(app.permissions)
            }
            ScoreBadge(auditedApp.score)
        }
    }
}

@Composable
private fun AppIcon(drawable: android.graphics.drawable.Drawable) {
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    // Rasterisation mémorisée : faite une seule fois par icône visible.
    val bitmap = remember(drawable, sizePx) { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(IconSize))
}

/** Rien n'est affiché tant que l'analyse DEX n'a pas eu lieu. */
@Composable
private fun TrackerSummary(analysis: TrackerAnalysis) {
    val (text, color) = when (analysis) {
        TrackerAnalysis.NotAnalyzed -> return
        TrackerAnalysis.Failed -> stringResource(R.string.trackers_failed) to MaterialTheme.colorScheme.onSurfaceVariant
        is TrackerAnalysis.Analyzed -> if (analysis.trackers.isEmpty()) {
            stringResource(R.string.trackers_none) to MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            pluralStringResource(R.plurals.trackers_found, analysis.trackers.size, analysis.trackers.size) to
                MaterialTheme.colorScheme.error
        }
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
private fun PermissionSummary(permissions: List<DetectedPermission>) {
    if (permissions.isEmpty()) {
        Text(
            text = stringResource(R.string.app_no_critical_permission),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        permissions.forEach { PermissionChip(it) }
    }
}

/** Puce pleine si la permission est accordée, simple contour si elle n'est que demandée. */
@Composable
private fun PermissionChip(permission: DetectedPermission) {
    val label = permission.risk.title
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (permission.isGranted) colors.secondaryContainer else colors.surface,
        contentColor = if (permission.isGranted) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (permission.isGranted) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            text = if (permission.isGranted) label else stringResource(R.string.permission_requested_only, label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
