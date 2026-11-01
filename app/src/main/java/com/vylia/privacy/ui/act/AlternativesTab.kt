package com.vylia.privacy.ui.act

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.act.Alternative
import com.vylia.privacy.domain.act.AlternativeCatalogue
import com.vylia.privacy.domain.act.AlternativeGroup
import com.vylia.privacy.domain.act.localized
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.components.AppIcon
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale

/** Score sous lequel on propose de remplacer une app (zone orange et rouge). */
private const val REPLACE_BELOW = 70

/**
 * Alternatives : d'abord le Worst of Fame (vos apps les moins respectueuses, avec de quoi les remplacer), puis le
 * Hall of Fame des alternatives éthiques ou libres, par famille. Les alternatives déjà installées sont repérées.
 */
@Composable
internal fun AlternativesTab(userApps: List<AuditedApp>, allApps: List<AuditedApp>, catalogue: AlternativeCatalogue) {
    val language = currentLocale().language
    val installed = remember(allApps) { allApps.map { it.app.packageName }.toSet() }
    val worst = remember(userApps, catalogue) {
        userApps.filter { it.score.value < REPLACE_BELOW }
            .mapNotNull { app -> catalogue.groupFor(app)?.let { app to it } }
            .filter { (app, group) -> group.alternatives.any { it.packageName != app.app.packageName } }
            .sortedBy { it.first.score.value }
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader(stringResource(R.string.alt_worst_title), stringResource(R.string.alt_worst_hint)) }
        if (worst.isEmpty()) {
            item { Text(stringResource(R.string.alt_worst_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(worst, key = { "worst-${it.first.app.packageName}" }) { (app, group) -> ReplaceCard(app, group, installed, language) }

        item {
            SectionHeader(stringResource(R.string.alt_hall_title), stringResource(R.string.alt_hall_hint), Modifier.padding(top = 12.dp))
        }
        items(catalogue.groups, key = { "group-${it.id}" }) { group ->
            GroupCard(group, installed, language, expanded = open == group.id) { open = if (open == group.id) null else group.id }
        }
    }
}

/** Une app à remplacer, avec les alternatives de sa famille. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplaceCard(app: AuditedApp, group: AlternativeGroup, installed: Set<String>, language: String) {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.app.icon, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(group.title.localized(language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScoreBadge(app.score)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.alternatives.filter { it.packageName != app.app.packageName }.forEach { alt ->
                    AlternativeChip(alt, alt.packageName in installed) { openStore(context, alt) }
                }
            }
        }
    }
}

/** Une famille du catalogue : repliée, elle montre ses alternatives ; ouverte, leur description et leurs magasins. */
@Composable
private fun GroupCard(group: AlternativeGroup, installed: Set<String>, language: String, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.title.localized(language), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!expanded) {
                Text(
                    group.alternatives.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                group.alternatives.forEach { alt -> AlternativeDetail(alt, alt.packageName in installed) { openStore(context, alt) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternativeDetail(alt: Alternative, isInstalled: Boolean, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(alt.name + if (isInstalled) "  ✓" else "", fontWeight = FontWeight.SemiBold)
        Text(alt.pitch.localized(currentLocale().language), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isInstalled) Tag(stringResource(R.string.alt_installed), Color(0xFF10B981))
            if (alt.openSource) Tag(stringResource(R.string.alt_open_source), Color(0xFF38BDF8))
            if (alt.fdroid != null) Tag("F-Droid", Color(0xFF94A3B8))
            if (alt.play != null) Tag("Google Play", Color(0xFF94A3B8))
        }
        if (!isInstalled) OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.alt_get)) }
    }
}

@Composable
private fun AlternativeChip(alt: Alternative, isInstalled: Boolean, onClick: () -> Unit) {
    val color = if (isInstalled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            alt.name + if (isInstalled) "  ✓" else "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/**
 * Ouvre la page de l'alternative : F-Droid d'abord (applications libres vérifiées), sinon Google Play, sinon son site.
 * Le navigateur ou le magasin s'en chargent : VYLIA ne se connecte à rien.
 */
private fun openStore(context: Context, alt: Alternative) {
    val uri = when {
        alt.fdroid != null -> "https://f-droid.org/packages/${alt.fdroid}/"
        alt.play != null -> "https://play.google.com/store/apps/details?id=${alt.play}"
        else -> alt.web ?: return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (_: android.content.ActivityNotFoundException) {
        // Ni navigateur ni magasin : rien à ouvrir.
    }
}
