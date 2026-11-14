package com.vylia.privacy.ui.act

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import com.vylia.privacy.ui.scan.components.LocalCardStyle
import com.vylia.privacy.data.preferences.CardStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.domain.act.ShieldLeak
import com.vylia.privacy.domain.act.ShieldLeaks
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.shield.TrackerCoverage
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted
import com.vylia.privacy.ui.shield.LocalOpenProtect
import com.vylia.privacy.domain.act.RemovalSuggestion
import com.vylia.privacy.domain.act.AppRevocations
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.ui.components.AppIcon
import com.vylia.privacy.ui.components.BadgeChip
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.rememberManageApp

/** Lignes affichées avant « Tout voir » dans chaque liste. */
private const val PreviewCount = 8

/**
 * Réduire : les actions à fort impact. D'abord les autorisations injustifiées à retirer (gain exact recalculé par le
 * moteur de score), puis les apps à supprimer, inutilisées ou intrusives.
 */
/**
 * Sous-onglets de Gérer : retirer des autorisations, supprimer des apps, traqueurs qui passent malgré le bouclier
 * (seulement avec le bouclier), remplacer une app. Mémorisé pour la session.
 */
enum class ManageSection(@param:StringRes val title: Int) {
    PERMISSIONS(R.string.reduce_tab_permissions),
    REMOVALS(R.string.reduce_tab_removals),
    TRACKERS(R.string.reduce_tab_trackers),
    ALTERNATIVES(R.string.manage_tab_alternatives),
    PREINSTALLED(R.string.manage_tab_preinstalled);

    companion object {
        /** Traqueurs : seulement avec le bouclier ; Préinstallées : seulement en mode Avancé. */
        fun visible(shieldEnabled: Boolean, advanced: Boolean = false): List<ManageSection> =
            entries.filter { (it != TRACKERS || shieldEnabled) && (it != PREINSTALLED || advanced) }
    }
}

@Composable
internal fun ReduceTab(
    revocations: List<AppRevocations>,
    removals: List<RemovalSuggestion>,
    usage: UsageState,
    section: ManageSection,
    onSection: (ManageSection) -> Unit,
    alternatives: @Composable () -> Unit,
    trackers: @Composable () -> Unit = {},
    advanced: Boolean = false,
    preinstalled: @Composable () -> Unit = {},
) {
    val sections = ManageSection.visible(BuildConfig.SHIELD_ENABLED, advanced)
    val current = section.takeIf { it in sections } ?: ManageSection.PERMISSIONS
    Column {
        SecondaryTabRow(selectedTabIndex = sections.indexOf(current)) {
            sections.forEach { entry ->
                Tab(
                    selected = current == entry,
                    onClick = { onSection(entry) },
                    text = {
                        Text(
                            stringResource(entry.title),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                        )
                    },
                )
            }
        }
        when (current) {
            ManageSection.PERMISSIONS -> RevocationsList(revocations)
            ManageSection.REMOVALS -> RemovalsList(removals, usage)
            ManageSection.TRACKERS -> trackers()
            ManageSection.ALTERNATIVES -> alternatives()
            ManageSection.PREINSTALLED -> preinstalled()
        }
    }
}

@Composable
private fun RevocationsList(revocations: List<AppRevocations>) {
    var allRevocations by rememberSaveable { mutableStateOf(false) }
    val permissions = revocations.sumOf { it.suggestions.size }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionHeader(
                pluralStringResource(R.plurals.reduce_revoke_title, permissions, permissions) + " · " +
                    pluralStringResource(R.plurals.reduce_revoke_apps, revocations.size, revocations.size),
                stringResource(R.string.reduce_revoke_hint),
            )
        }
        if (revocations.isEmpty()) item { Empty(stringResource(R.string.reduce_revoke_empty)) }
        items(if (allRevocations) revocations else revocations.take(PreviewCount), key = { "revoke-${it.app.app.packageName}" }) {
            RevokeRow(it)
        }
        if (revocations.size > PreviewCount) item { ShowAll(allRevocations, revocations.size) { allRevocations = !allRevocations } }
    }
}

@Composable
private fun RemovalsList(removals: List<RemovalSuggestion>, usage: UsageState) {
    var allRemovals by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionHeader(
                pluralStringResource(R.plurals.reduce_remove_title, removals.size, removals.size),
                stringResource(if (usage.hasAccess) R.string.reduce_remove_hint else R.string.reduce_remove_hint_no_usage),
            )
        }
        if (!usage.hasAccess) item { UsageAccessCard() }
        if (removals.isEmpty()) item { Empty(stringResource(R.string.reduce_remove_empty)) }
        items(if (allRemovals) removals else removals.take(PreviewCount), key = { "remove-${it.app.app.packageName}" }) { RemovalRow(it, usage.hasAccess) }
        if (removals.size > PreviewCount) item { ShowAll(allRemovals, removals.size) { allRemovals = !allRemovals } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * Gérer › Traqueurs : les apps dont des traqueurs passent malgré le bouclier, la plus exposée d'abord. [leaks] `null` :
 * la couverture des traqueurs n'est pas encore calculée. [hasAlternative] : une alternative est proposée pour l'app.
 */
@Composable
internal fun LeaksList(
    leaks: List<ShieldLeak>?,
    shieldActive: Boolean,
    analyzing: Boolean,
    hasAlternative: (AuditedApp) -> Boolean,
    onAlternatives: () -> Unit,
) {
    var all by rememberSaveable { mutableStateOf(false) }
    if (leaks == null) {
        ActPlaceholder(null)
        return
    }
    val trackers = ShieldLeaks.distinctTrackers(leaks)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionHeader(
                pluralStringResource(R.plurals.reduce_leaks_title, trackers, trackers) + " · " +
                    pluralStringResource(R.plurals.reduce_revoke_apps, leaks.size, leaks.size),
                stringResource(R.string.reduce_leaks_hint),
            )
        }
        if (!shieldActive) item { ShieldOffCard() }
        if (analyzing) item { Empty(stringResource(R.string.reduce_leaks_pending)) }
        if (leaks.isEmpty() && !analyzing) item { Empty(stringResource(R.string.reduce_leaks_empty)) }
        items(if (all) leaks else leaks.take(PreviewCount), key = { "leak-${it.app.app.packageName}" }) {
            LeakRow(it, hasAlternative(it.app), onAlternatives)
        }
        if (leaks.size > PreviewCount) item { ShowAll(all, leaks.size) { all = !all } }
    }
}

@Composable
private fun ShieldOffCard() {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reduce_leaks_shield_off), style = MaterialTheme.typography.bodySmall)
            LocalOpenProtect.current?.let { openProtect ->
                Button(onClick = openProtect) { Text(stringResource(R.string.detail_shield_turn_on)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeakRow(leak: ShieldLeak, hasAlternative: Boolean, onAlternatives: () -> Unit) {
    val manage = rememberManageApp()
    val app = leak.app.app
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.icon, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.reduce_leaks_passing, leak.penalty.formatted(currentLocale(), decimals = 1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ScoreBadge(leak.app.score)
            }
            leak.trackers.forEach { tracker ->
                Column {
                    Text(tracker.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            when (tracker.coverage) {
                                TrackerCoverage.PARTIAL -> R.string.tracker_shield_partial
                                TrackerCoverage.UNKNOWN -> R.string.tracker_shield_unknown
                                else -> R.string.tracker_shield_none
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // La fiche Android de l'app, où se trouve le bouton Désinstaller : VYLIA ne désinstalle rien lui-même.
                OutlinedButton(onClick = { manage(leak.app) }) { Text(stringResource(R.string.reduce_action_uninstall)) }
                if (hasAlternative) TextButton(onClick = onAlternatives) { Text(stringResource(R.string.reduce_action_alternatives)) }
            }
        }
    }
}

@Composable
private fun RevokeRow(entry: AppRevocations) {
    if (LocalCardStyle.current == CardStyle.COMPACT) {
        CompactRevokeRow(entry)
        return
    }
    val manage = rememberManageApp()
    val app = entry.app.app
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.icon, 32.dp)
                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Gain(stringResource(R.string.reduce_gain_points, entry.gain))
            }
            // Une ligne par autorisation, avec ce qu'elle rapporte seule.
            entry.suggestions.forEach { suggestion ->
                val permission = PermissionGlossary.titleOf(suggestion.finding.permission)?.let { stringResource(it) } ?: suggestion.finding.permission
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reduce_revoke_line, permission), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.reduce_gain_points, suggestion.gain),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.reduce_score_after, entry.app.score.value, entry.newScore),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.badgesGained.isNotEmpty() || entry.badgesLost.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.badgesGained.forEach { BadgeChip(it) }
                    entry.badgesLost.forEach { Text(stringResource(R.string.reduce_badge_lost), style = MaterialTheme.typography.labelSmall); BadgeChip(it) }
                }
            }
            OutlinedButton(onClick = { manage(entry.app) }) {
                Text(stringResource(R.string.reduce_action_permissions))
            }
        }
    }
}

/**
 * Carte compacte (réglage Cartes d'apps) : nom, permissions à retirer sur une ligne, score avant et après, gain ;
 * ni badges ni bouton, toute la carte ouvre la fiche Android de l'app.
 */
@Composable
private fun CompactRevokeRow(entry: AppRevocations) {
    val manage = rememberManageApp()
    val resources = LocalResources.current
    val app = entry.app.app
    val permissions = entry.suggestions.joinToString(" · ") { s ->
        PermissionGlossary.titleOf(s.finding.permission)?.let(resources::getString) ?: s.finding.permission
    }
    val action = stringResource(R.string.reduce_action_permissions)
    VyliaCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = action) { manage(entry.app) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(app.icon, 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(permissions, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.reduce_score_after, entry.app.score.value, entry.newScore),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Gain(stringResource(R.string.reduce_gain_points, entry.gain))
        }
    }
}

@Composable
private fun RemovalRow(suggestion: RemovalSuggestion, usageKnown: Boolean) {
    val manage = rememberManageApp()
    val app = suggestion.app.app
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.icon, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(lastUsedText(suggestion.lastUsedAt, usageKnown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (suggestion.points > 0) Gain(stringResource(R.string.reduce_reward_points, suggestion.points))
                ScoreBadge(suggestion.app.score)
            }
            // La fiche Android de l'app, où se trouve le bouton Désinstaller : VYLIA ne désinstalle rien lui-même.
            OutlinedButton(onClick = { manage(suggestion.app) }) {
                Text(stringResource(R.string.reduce_action_uninstall))
            }
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
        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
