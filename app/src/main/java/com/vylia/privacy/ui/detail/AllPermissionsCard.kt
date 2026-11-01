package com.vylia.privacy.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.data.AndroidPermission
import com.vylia.privacy.data.PermissionCatalog
import com.vylia.privacy.data.Protection
import com.vylia.privacy.domain.learn.PermissionClassifier
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.learn.PermissionTheme
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.scoring.PermissionFinding
import androidx.compose.foundation.layout.width
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.main.PermissionExplanation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Une permission déclarée par l'app, avec ce qu'Android en dit. */
private class DeclaredPermission(val name: String, val android: AndroidPermission) {
    val shortName = name.removePrefix("android.permission.")
    val sensitive get() = android.protection == Protection.DANGEROUS || android.protection == Protection.SPECIAL
}

/**
 * Les permissions de l'app, toutes, comme sur Exodus Privacy : regroupées par thème, avec leur niveau de protection
 * Android et l'état accordée / refusée des permissions sensibles. Celles qui comptent dans le score affichent leur
 * contexte (justifiée, tolérée…) et leur pénalité, totalisée par thème. Au toucher : la fiche VYLIA, ou la
 * description officielle d'Android. Les permissions de badges des lanceurs tiennent en une ligne.
 */
@Composable
fun AllPermissionsCard(app: InstalledApp, findings: List<PermissionFinding>) {
    val byPermission = remember(findings) { findings.associateBy { it.permission } }
    val context = LocalContext.current
    val catalog = remember(context) { PermissionCatalog(context) }
    val declared by produceState<List<DeclaredPermission>?>(null, app.packageName, catalog) {
        value = withContext(Dispatchers.IO) {
            app.requestedPermissions.map { DeclaredPermission(it, catalog.describe(it)) }
        }
    }
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.perms_all_title, app.requestedPermissions.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.perms_all_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val list = declared
            if (list == null) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                return@Column
            }
            val (launcher, others) = list.partition { PermissionClassifier.isLauncherBadge(it.name) }
            val groups = others.groupBy { PermissionClassifier.theme(it.name) }
            var expanded by rememberSaveable(app.packageName) { mutableStateOf<String?>(null) }
            PermissionTheme.entries.forEach { theme ->
                val items = groups[theme] ?: return@forEach
                val penalty = items.sumOf { byPermission[it.shortName]?.counted ?: 0.0 }
                ThemeHeader(theme, items, penalty, expanded == theme.name) {
                    expanded = if (expanded == theme.name) null else theme.name
                }
                if (expanded == theme.name) {
                    items.sortedWith(
                        compareByDescending<DeclaredPermission> { byPermission[it.shortName]?.penalty ?: -1.0 }
                            .thenBy { !it.sensitive }
                            .thenBy { it.shortName }
                    ).forEach { PermissionRow(it, app.grantedPermissions, byPermission[it.shortName]) }
                }
                HorizontalDivider()
            }
            if (launcher.isNotEmpty()) LauncherBadgesRow(launcher)
        }
    }
}

@Composable
private fun ThemeHeader(theme: PermissionTheme, items: List<DeclaredPermission>, penalty: Double, expanded: Boolean, onToggle: () -> Unit) {
    val sensitive = items.count { it.sensitive }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(theme.title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (sensitive > 0) {
            Text(
                pluralStringResource(R.plurals.perms_sensitive_count, sensitive, sensitive),
                style = MaterialTheme.typography.labelMedium,
                color = protectionColor(Protection.DANGEROUS),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (penalty > 0.0) {
            PenaltyText(penalty)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "${items.size}" + if (expanded) "  ▲" else "  ▼",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionRow(permission: DeclaredPermission, granted: Set<String>, finding: PermissionFinding?) {
    val info = PermissionGlossary.find(permission.name)
    var open by rememberSaveable(permission.name) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                info?.title?.let { stringResource(it) } ?: permission.android.label?.replaceFirstChar { it.uppercase() } ?: permission.shortName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (finding != null) PenaltyText(finding.counted)
        }
        Text(
            permission.shortName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Chip(stringResource(protectionLabel(permission.android.protection)), protectionColor(permission.android.protection))
            if (permission.sensitive) {
                val isGranted = permission.shortName in granted
                Chip(
                    stringResource(if (isGranted) R.string.perm_status_granted else R.string.perm_status_refused),
                    if (isGranted) Color(0xFFF59E0B) else Color(0xFF10B981),
                )
            }
            if (finding != null) Chip(stringResource(finding.context.label), finding.context.tint)
            // Une permission plus lourde du même groupe compte déjà (ex. position précise et approximative).
            if (finding != null && finding.counted < finding.penalty) Chip(stringResource(R.string.perm_grouped), Color(0xFF94A3B8))
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            when {
                info != null -> PermissionExplanation(info)
                permission.android.protection == Protection.UNDEFINED -> Hint(stringResource(R.string.perm_undefined_description))
                else -> Hint(permission.android.description ?: stringResource(R.string.perm_no_description))
            }
        }
    }
}

/** Badges et raccourcis des lanceurs d'autres marques : une seule ligne, détail au toucher. */
@Composable
private fun LauncherBadgesRow(items: List<DeclaredPermission>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp)) {
        Text(
            stringResource(R.string.launcher_group_title, items.size) + if (open) "  ▲" else "  ▼",
            fontWeight = FontWeight.Bold,
        )
        Hint(stringResource(R.string.launcher_group_description))
        if (open) {
            items.forEach {
                Text(it.name, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun protectionLabel(protection: Protection): Int = when (protection) {
    Protection.DANGEROUS -> R.string.protection_dangerous
    Protection.SPECIAL -> R.string.protection_special
    Protection.SYSTEM -> R.string.protection_system
    Protection.NORMAL -> R.string.protection_normal
    Protection.UNDEFINED -> R.string.protection_undefined
}

/** Rouge pour « dangereuse » comme sur Exodus, ambre pour les accès spéciaux, neutre pour le reste. */
private fun protectionColor(protection: Protection): Color = when (protection) {
    Protection.DANGEROUS -> Color(0xFFEF4444)
    Protection.SPECIAL -> Color(0xFFF59E0B)
    Protection.SYSTEM -> Color(0xFF64748B)
    Protection.NORMAL, Protection.UNDEFINED -> Color(0xFF94A3B8)
}
