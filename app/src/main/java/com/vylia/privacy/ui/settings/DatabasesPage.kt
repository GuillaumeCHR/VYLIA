package com.vylia.privacy.ui.settings

import android.content.res.Resources
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vylia.privacy.R
import com.vylia.privacy.domain.databases.DatabaseAggregate
import com.vylia.privacy.domain.databases.DatabaseGroup
import com.vylia.privacy.domain.databases.DatabaseRow
import com.vylia.privacy.domain.databases.DatabaseRows
import com.vylia.privacy.domain.databases.DatabaseStatus
import com.vylia.privacy.domain.databases.UpdateSummary
import com.vylia.privacy.ui.components.Accordion
import com.vylia.privacy.ui.components.VyliaCard
import java.text.DateFormat
import java.util.Date

/** Résumé d'état de toutes les bases, sous-titre du lien des Paramètres ; `true` : un échec à signaler. */
@Composable
internal fun databasesStatus(viewModel: SettingsViewModel): Pair<String, Boolean> {
    val state by viewModel.databases.collectAsStateWithLifecycle()
    val aggregate = DatabaseRows.aggregate(state.rows)
    return LocalResources.current.aggregateText(aggregate, state.progress) to (aggregate.failed > 0 || aggregate.repeatedFailure)
}

/**
 * Paramètres › Bases de données. En tête, l'état de toutes les bases et « Tout mettre à jour » ; puis une carte en
 * accordéon par groupe (Score, Bouclier, Alternatives, Enrichissement), repliée d'office, qui montre son état agrégé
 * repliée, et une ligne par base dépliée. Rien n'est téléchargé à l'ouverture : « mise à jour disponible » ne vient que
 * d'un contrôle déjà fait.
 */
@Composable
internal fun DatabasesPage(modifier: Modifier, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.databases.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val aggregate = DatabaseRows.aggregate(state.rows)
    val busy = state.progress != null
    SubPage(modifier, R.string.settings_database, onBack) {
        VyliaCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLine(resources.aggregateText(aggregate, state.progress), aggregate.failed > 0 || aggregate.repeatedFailure)
                Button(onClick = viewModel::updateAllDatabases, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.db_progress, state.progress!!.done, state.progress!!.total))
                    } else {
                        Text(stringResource(R.string.db_update_all))
                    }
                }
                state.progress?.let { p ->
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.summary?.let { Summary(it, state.rows, resources, viewModel::dismissDatabaseSummary) }
                Hint(stringResource(R.string.db_hint))
            }
        }
        DatabaseGroup.entries.forEach { group ->
            val rows = state.rows.filter { it.group == group }
            if (rows.isNotEmpty()) GroupSection(group, rows, busy, viewModel)
        }
    }
}

@Composable
private fun GroupSection(group: DatabaseGroup, rows: List<DatabaseRow>, busy: Boolean, viewModel: SettingsViewModel) {
    val resources = LocalResources.current
    val aggregate = DatabaseRows.aggregate(rows)
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Accordion(
            key = "databases_${group.name}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            header = {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(group.title()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    StatusLine(resources.aggregateText(aggregate, null), aggregate.failed > 0 || aggregate.repeatedFailure)
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                rows.forEach { row ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(row, busy, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Row(row: DatabaseRow, busy: Boolean, viewModel: SettingsViewModel) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val name = resources.databaseName(row)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Hint(row.attribution)
        if (row.proposed && !row.enabled) {
            Hint(stringResource(R.string.db_proposed_cname_hint))
            Text(
                stringResource(
                    R.string.db_proposed_size,
                    Formatter.formatShortFileSize(context, row.downloadBytes),
                    Formatter.formatShortFileSize(context, row.rawBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { viewModel.setProposedDatabase(row.id, true) },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = resources.getString(R.string.db_add_named, name) },
            ) { Text(stringResource(R.string.db_add)) }
            return@Column
        }
        Hint(
            listOfNotNull(
                row.updatedAt?.let { stringResource(R.string.db_row_updated, resources.formatDay(it)) } ?: stringResource(R.string.db_row_embedded),
                row.entries?.let { resources.getQuantityString(R.plurals.db_row_entries, it, it) },
            ).joinToString(" · "),
        )
        StatusLine(resources.statusText(row), row.status == DatabaseStatus.FAILED)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { viewModel.updateDatabase(row.id) },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = resources.getString(R.string.db_update_named, name) },
            ) { Text(stringResource(R.string.db_update_one)) }
            if (row.proposed) {
                TextButton(
                    onClick = { viewModel.setProposedDatabase(row.id, false) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = resources.getString(R.string.db_remove_named, name) },
                ) { Text(stringResource(R.string.db_remove)) }
            }
        }
    }
}

/** Ligne d'état ; un échec porte une icône d'alerte, pas seulement une couleur. */
@Composable
private fun StatusLine(text: String, warning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        if (warning) {
            Icon(
                painterResource(R.drawable.ic_sym_warning),
                contentDescription = stringResource(R.string.db_warning),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Summary(summary: UpdateSummary, rows: List<DatabaseRow>, resources: Resources, onDismiss: () -> Unit) {
    val names = rows.associate { it.id to resources.databaseName(it) }
    val parts = listOfNotNull(
        summary.updated.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.db_result_updated, it, it) },
        summary.unchanged.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.db_result_unchanged, it, it) },
        summary.failed.size.takeIf { it > 0 }?.let { n ->
            resources.getQuantityString(R.plurals.db_result_failed, n, n) + " (" +
                summary.failed.entries.joinToString(" ; ") { (id, reason) -> resources.getString(R.string.db_result_failure, names[id] ?: id, reason) } + ")"
        },
    )
    Column(
        Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            parts.joinToString(", ") + ".",
            style = MaterialTheme.typography.bodySmall,
            color = if (summary.failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (summary.scoreChanged) Hint(stringResource(R.string.db_result_rescored))
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.db_dismiss)) }
    }
}

private fun DatabaseGroup.title(): Int = when (this) {
    DatabaseGroup.SCORE -> R.string.db_group_score
    DatabaseGroup.SHIELD -> R.string.db_group_shield
    DatabaseGroup.ALTERNATIVES -> R.string.db_group_alternatives
    DatabaseGroup.ENRICHMENT -> R.string.db_group_enrichment
}

/** Les bases de VYLIA ont un nom traduit ; les autres gardent leur nom propre. */
private fun Resources.databaseName(row: DatabaseRow): String = when (row.id) {
    "rules" -> getString(R.string.db_name_rules)
    "exodus_categories" -> getString(R.string.db_name_exodus_categories)
    "alternatives" -> getString(R.string.db_name_alternatives)
    "web_services" -> getString(R.string.db_name_web_services)
    "private_dns" -> getString(R.string.db_name_private_dns)
    "support_resources" -> getString(R.string.db_name_support_resources)
    "uad" -> getString(R.string.db_name_uad)
    else -> row.name
}

private fun Resources.statusText(row: DatabaseRow): String = when (row.status) {
    DatabaseStatus.UP_TO_DATE -> getString(R.string.db_status_up_to_date)
    DatabaseStatus.UPDATING -> getString(R.string.db_status_updating)
    DatabaseStatus.FAILED -> getString(R.string.db_status_failed, row.lastError.orEmpty()) +
        if (row.consecutiveFailures >= DatabaseRows.REPEATED_FAILURES) " " + getQuantityString(R.plurals.db_status_repeated, row.consecutiveFailures, row.consecutiveFailures) else ""
    DatabaseStatus.UPDATE_AVAILABLE -> getString(R.string.db_status_available)
    DatabaseStatus.EMBEDDED -> getString(R.string.db_status_embedded)
    DatabaseStatus.NOT_ADDED -> ""
}

/** « 14 bases · à jour le 8 nov. », « 2 mises à jour disponibles », « 1 échec lors de la dernière mise à jour »… */
private fun Resources.aggregateText(aggregate: DatabaseAggregate, progress: com.vylia.privacy.domain.databases.DatabaseUpdater.Progress?): String {
    val count = getQuantityString(R.plurals.db_count, aggregate.total, aggregate.total)
    return when {
        progress != null -> getString(R.string.db_summary_updating, count, getString(R.string.db_progress, progress.done, progress.total))
        aggregate.failed > 0 -> getQuantityString(R.plurals.db_summary_failed, aggregate.failed, aggregate.failed) +
            if (aggregate.repeatedFailure) " · " + getString(R.string.db_summary_repeated) else ""
        aggregate.updateAvailable > 0 -> getQuantityString(R.plurals.db_summary_available, aggregate.updateAvailable, aggregate.updateAvailable)
        aggregate.oldestUpdate != null -> getString(R.string.db_summary_updated, count, formatDay(aggregate.oldestUpdate))
        else -> getString(R.string.db_summary_embedded, count)
    }
}

private fun Resources.formatDay(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, configuration.locales[0]).format(Date(timestamp))
