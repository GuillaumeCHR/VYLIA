package com.vylia.privacy.ui.scan.components

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
import com.vylia.privacy.ui.components.BadgeRow
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.openAppSettings
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext

private val IconSize = 44.dp

/**
 * Carte d'une application : icône, nom, traqueurs, badges de qualification et score. L'icône ouvre directement
 * la fiche système de l'app (désinstaller, gérer les autorisations), sans passer par le Play Store.
 */
@Composable
fun AppCard(auditedApp: AuditedApp, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val app = auditedApp.app
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon, label = app.label, packageName = app.packageName)
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
                if (auditedApp.result.displayedBadges.isNotEmpty()) BadgeRow(auditedApp.result.displayedBadges)
            }
            ScoreBadge(auditedApp.score)
        }
    }
}

@Composable
private fun AppIcon(drawable: android.graphics.drawable.Drawable, label: String, packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    // Rasterisation mémorisée : faite une seule fois par icône visible.
    val bitmap = remember(drawable, sizePx) { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.app_icon_manage, label),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { openAppSettings(context, packageName) }
            .size(IconSize),
    )
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
