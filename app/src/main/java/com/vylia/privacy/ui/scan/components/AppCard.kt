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
import androidx.compose.material3.Icon
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.vylia.privacy.data.preferences.CardStyle
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.ScoreResult
import com.vylia.privacy.ui.components.securityLabel
import kotlin.math.roundToInt
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

/** Présentation des cartes, fournie à la racine depuis les paramètres. */
val LocalCardStyle = staticCompositionLocalOf { CardStyle.COMPACT }

/**
 * Carte d'une application, compacte ou détaillée selon [LocalCardStyle]. L'icône ouvre directement la fiche système
 * de l'app (désinstaller, gérer les autorisations), sans passer par le Play Store.
 */
@Composable
fun AppCard(auditedApp: AuditedApp, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    when (LocalCardStyle.current) {
        CardStyle.COMPACT -> CompactAppCard(auditedApp, modifier, onClick)
        CardStyle.DETAILED -> DetailedAppCard(auditedApp, modifier, onClick)
    }
}

/** Icône, nom, score et ce qui l'explique : pénalités, alerte de sécurité et plafond éventuel. */
@Composable
private fun CompactAppCard(auditedApp: AuditedApp, modifier: Modifier, onClick: () -> Unit) {
    val app = auditedApp.app
    val result = auditedApp.result
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon, label = app.label, packageName = app.packageName)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PenaltyLine(result)
                if (result.securityFindings.isNotEmpty()) SecurityLine(result)
            }
            ScoreBadge(auditedApp.score)
        }
    }
}

@Composable
private fun PenaltyLine(result: ScoreResult) {
    val permissions = result.permissionPenalty.roundToInt()
    val partial = result.confidence == Confidence.PARTIAL
    val text = if (partial) stringResource(R.string.card_penalties_partial, permissions)
    else stringResource(R.string.card_penalties, permissions, result.trackerPenalty)
    val description = if (partial) stringResource(R.string.card_penalties_partial_description, permissions)
    else stringResource(R.string.card_penalties_description, permissions, result.trackerPenalty)
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun SecurityLine(result: ScoreResult) {
    val resources = LocalResources.current
    val cap = result.securityScoreCap?.takeIf { result.isCapped }
    val text = if (cap != null) stringResource(R.string.card_security_capped, cap) else stringResource(R.string.card_security)
    val labels = result.securityFindings.joinToString { resources.securityLabel(it) }
    val description = if (cap != null) stringResource(R.string.card_security_capped_description, labels, cap)
    else stringResource(R.string.card_security_description, labels)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sym_gpp_maybe),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Paquet, traqueurs et badges de qualification. */
@Composable
private fun DetailedAppCard(auditedApp: AuditedApp, modifier: Modifier, onClick: () -> Unit) {
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
                PermissionSummary(auditedApp.result)
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

/** Permissions accordées qui coûtent des points au score. */
@Composable
private fun PermissionSummary(result: ScoreResult) {
    val count = result.permissionFindings.count { it.granted && it.penalty > 0.0 }
    val (text, color) = if (count == 0) {
        stringResource(R.string.permissions_sensitive_none) to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        pluralStringResource(R.plurals.permissions_sensitive, count, count) to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
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
