package com.vylia.privacy.ui.understand

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.scoring.BadgeDef
import com.vylia.privacy.domain.scoring.BadgeRule
import com.vylia.privacy.domain.scoring.Rules
import com.vylia.privacy.ui.components.BadgeChip
import com.vylia.privacy.ui.components.TransparencyBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.badgeColor
import com.vylia.privacy.ui.components.badgeDescription
import com.vylia.privacy.ui.components.badgeLabel
import com.vylia.privacy.ui.components.trackerCategoryName
import com.vylia.privacy.ui.components.securityLabel
import com.vylia.privacy.domain.scoring.SecurityFinding

/** Axes des badges, dans l'ordre d'affichage des règles (sécurité d'abord), puis les combinaisons. */
private val AxisTitles = listOf(
    "permissions" to R.string.badge_axis_permissions,
    "security" to R.string.badge_axis_security,
    "trackers" to R.string.badge_axis_trackers,
    "combo" to R.string.badge_axis_combo,
)

/**
 * Tous les badges des règles en vigueur, par axe : pictogramme, signification, règle en clair (déduite des paramètres
 * de la règle, donc juste même pour des règles importées), badges remplacés, et nombre de vos apps qui l'ont.
 */
@Composable
internal fun BadgesTab(rules: Rules, scannedApps: List<AuditedApp>, query: String) {
    val resources = LocalResources.current
    val counts = remember(scannedApps) {
        scannedApps.filter { AppFilter.USER.matches(it.app) }
            .flatMap { app -> app.result.badges.map { it.id } }
            .groupingBy { it }.eachCount()
    }
    // Recherche dans le nom, la signification et la règle en clair de chaque badge.
    val byAxis = remember(rules, query, resources) {
        rules.badges.definitions
            .filter { badge ->
                query.matchesAny(listOfNotNull(resources.badgeLabel(badge), resources.badgeDescription(badge), resources.ruleText(badge.rule, rules)))
            }
            .groupBy { it.axis }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(R.string.understand_badges_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (byAxis.isEmpty()) {
            item { Text(stringResource(R.string.learn_search_empty_badges, query.trim()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        AxisTitles.forEach { (axis, title) ->
            val badges = byAxis[axis] ?: return@forEach
            item(key = "axis-$axis") {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(badges, key = { it.id }) { badge -> BadgeCard(badge, rules, counts[badge.id] ?: 0) }
        }
    }
}

@Composable
private fun BadgeCard(badge: BadgeDef, rules: Rules, onDevice: Int) {
    val resources = LocalResources.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeChip(badge)
                Text(
                    stringResource(
                        when (badge.polarity) {
                            "positive" -> R.string.badge_pill_positive
                            "negative" -> R.string.badge_pill_negative
                            else -> R.string.badge_pill_neutral
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor(badge.severity),
                )
            }
            resources.badgeDescription(badge)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                stringResource(R.string.badge_rule_label, resources.ruleText(badge.rule, rules)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (badge.supersedes.isNotEmpty()) {
                val replaced = rules.badges.definitions.filter { it.id in badge.supersedes }.joinToString { resources.badgeLabel(it) }
                Text(
                    stringResource(R.string.badge_supersedes, replaced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TransparencyBadge(
                if (onDevice == 0) stringResource(R.string.badge_on_device_none)
                else pluralStringResource(R.plurals.badge_on_device, onDevice, onDevice)
            )
        }
    }
}

/** La règle d'un badge en une phrase, à partir de ses paramètres. */
private fun Resources.ruleText(rule: BadgeRule, rules: Rules): String {
    fun permissions(keys: Collection<String>) = keys.joinToString { key ->
        PermissionGlossary.titleOf(key)?.let(::getString) ?: key.substringAfterLast('.')
    }
    fun categories(keys: Collection<String>) = keys.joinToString { trackerCategoryName(it) }
    fun context(name: String?) = getString(
        when (name) {
            "ALLOWED" -> R.string.badge_ctx_allowed
            "TOLERATED" -> R.string.badge_ctx_tolerated
            "FORBIDDEN" -> R.string.badge_ctx_forbidden
            else -> R.string.badge_ctx_unexpected
        }
    )
    fun granted(value: Boolean?) = when (value) {
        true -> getString(R.string.badge_granted_suffix)
        false -> getString(R.string.badge_refused_suffix)
        null -> ""
    }
    val min = rule.min ?: 1
    return when (rule.type) {
        "permissions_none" -> getString(R.string.badge_rule_permissions_none)
        "permissions_in_purpose" -> getString(R.string.badge_rule_permissions_in_purpose)
        "permission_context" -> getQuantityString(R.plurals.badge_rule_permission_context, min, min, context(rule.context), granted(rule.granted))
        "permission_penalty_at_cap" -> getString(R.string.badge_rule_penalty_cap, rules.scoring.permissionCap)
        "security_pattern" -> getString(
            R.string.badge_rule_security,
            rule.patterns.joinToString { id ->
                rules.security.patterns[id]?.let { securityLabel(SecurityFinding(id, it.label, it.severity, it.description)) } ?: id
            },
        )
        "trackers_none" -> getString(R.string.badge_rule_trackers_none)
        "trackers_technical_only" -> getString(
            R.string.badge_rule_trackers_technical,
            (rules.badges.technicalTrackers.categories.map { trackerCategoryName(it) } + rules.badges.technicalTrackers.names).joinToString(),
        )
        "trackers_in_categories" -> getQuantityString(R.plurals.badge_rule_trackers_in_categories, min, min, categories(rule.categories))
        "tracker_category_coverage" -> getString(R.string.badge_rule_tracker_coverage, min, categories(rule.categories))
        "trackers_count" -> getString(R.string.badge_rule_trackers_count, min)
        "trackers_unanalyzed" -> getString(R.string.badge_rule_trackers_unanalyzed)
        "axes_all_positive" -> getString(R.string.badge_rule_axes_all_positive)
        "axes_all_severity" -> getString(R.string.badge_rule_axes_all_severity)
        "permission_set_context" -> getString(
            R.string.badge_rule_set_context,
            min,
            rule.contexts.joinToString(" ${getString(R.string.badge_or)} ") { context(it) },
            granted(rule.granted),
            permissions(rule.permissions),
        )
        "permissions_absent" -> getString(R.string.badge_rule_absent, permissions(rule.permissions))
        else -> getString(R.string.badge_rule_unknown)
    }
}
