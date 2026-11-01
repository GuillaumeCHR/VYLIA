package com.vylia.privacy.ui.act

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.act.RemovalSuggestion
import com.vylia.privacy.domain.act.RevokeSuggestion
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.ui.components.AppIcon
import com.vylia.privacy.ui.components.BadgeChip
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.openAppSettings

/** Lignes affichées avant « Tout voir » dans chaque liste. */
private const val PreviewCount = 8

/**
 * Réduire : les actions à fort impact. D'abord les autorisations injustifiées à retirer (gain exact recalculé par le
 * moteur de score), puis les apps à supprimer, inutilisées ou intrusives.
 */
@Composable
internal fun ReduceTab(revocations: List<RevokeSuggestion>, removals: List<RemovalSuggestion>, usage: UsageState) {
    var allRevocations by rememberSaveable { mutableStateOf(false) }
    var allRemovals by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader(pluralStringResource(R.plurals.reduce_revoke_title, revocations.size, revocations.size), stringResource(R.string.reduce_revoke_hint)) }
        if (revocations.isEmpty()) item { Empty(stringResource(R.string.reduce_revoke_empty)) }
        items(if (allRevocations) revocations else revocations.take(PreviewCount), key = { "revoke-${it.app.app.packageName}-${it.finding.permission}" }) {
            RevokeRow(it)
        }
        if (revocations.size > PreviewCount) item { ShowAll(allRevocations, revocations.size) { allRevocations = !allRevocations } }

        item {
            SectionHeader(
                pluralStringResource(R.plurals.reduce_remove_title, removals.size, removals.size),
                stringResource(if (usage.hasAccess) R.string.reduce_remove_hint else R.string.reduce_remove_hint_no_usage),
                Modifier.padding(top = 12.dp),
            )
        }
        if (!usage.hasAccess) item { UsageAccessCard() }
        if (removals.isEmpty()) item { Empty(stringResource(R.string.reduce_remove_empty)) }
        items(if (allRemovals) removals else removals.take(PreviewCount), key = { "remove-${it.app.app.packageName}" }) { RemovalRow(it, usage.hasAccess) }
        if (removals.size > PreviewCount) item { ShowAll(allRemovals, removals.size) { allRemovals = !allRemovals } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevokeRow(suggestion: RevokeSuggestion) {
    val context = LocalContext.current
    val app = suggestion.app.app
    val permission = PermissionGlossary.titleOf(suggestion.finding.permission)?.let { stringResource(it) } ?: suggestion.finding.permission
    VyliaCard(modifier = Modifier.fillMaxWidth().clickable { openAppSettings(context, app.packageName) }) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.icon, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.reduce_revoke_line, permission), style = MaterialTheme.typography.bodySmall)
                }
                Gain(stringResource(R.string.reduce_gain_points, suggestion.gain))
            }
            Text(
                stringResource(R.string.reduce_score_after, suggestion.app.score.value, suggestion.newScore),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (suggestion.badgesGained.isNotEmpty() || suggestion.badgesLost.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestion.badgesGained.forEach { BadgeChip(it) }
                    suggestion.badgesLost.forEach { Text(stringResource(R.string.reduce_badge_lost), style = MaterialTheme.typography.labelSmall); BadgeChip(it) }
                }
            }
        }
    }
}

@Composable
private fun RemovalRow(suggestion: RemovalSuggestion, usageKnown: Boolean) {
    val context = LocalContext.current
    val app = suggestion.app.app
    VyliaCard(modifier = Modifier.fillMaxWidth().clickable { openAppSettings(context, app.packageName) }) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(app.icon, 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lastUsedText(suggestion.lastUsedAt, usageKnown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (suggestion.points > 0) Gain(stringResource(R.string.reduce_reward_points, suggestion.points))
            ScoreBadge(suggestion.app.score)
        }
    }
}

/**
 * « Ouverte il y a 3 jours », « Pas ouverte depuis 4 mois » ; avec l'accès, une app absente des statistiques n'a pas
 * été ouverte depuis plus d'un an.
 */
@Composable
private fun lastUsedText(lastUsedAt: Long?, usageKnown: Boolean): String {
    if (lastUsedAt == null) {
        return stringResource(if (usageKnown) R.string.reduce_unused_year else R.string.reduce_last_used_unknown)
    }
    val days = ((System.currentTimeMillis() - lastUsedAt) / DAY_MS).toInt()
    return when {
        days < 1 -> stringResource(R.string.reduce_last_used_today)
        days < 60 -> pluralStringResource(R.plurals.reduce_last_used_days, days, days)
        else -> pluralStringResource(R.plurals.reduce_unused_months, days / 30, days / 30)
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Explique l'accès aux données d'utilisation et ouvre l'écran Android où l'activer. */
@Composable
private fun UsageAccessCard() {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reduce_usage_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.reduce_usage_text), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { openUsageAccessSettings(context) }) { Text(stringResource(R.string.reduce_usage_button)) }
        }
    }
}

private fun openUsageAccessSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    } catch (_: android.content.ActivityNotFoundException) {
        // Écran absent sur certains appareils : l'explication reste affichée.
    }
}

@Composable
internal fun SectionHeader(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Gain(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ShowAll(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(if (expanded) stringResource(R.string.list_collapse) else stringResource(R.string.list_show_all, count))
    }
}
