package com.vylia.privacy.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import com.vylia.privacy.data.export.CsvExporter
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Sélecteur de fichiers ouvert d'emblée sur le dossier Téléchargements (ignoré si le fournisseur ne le gère pas). */
private class OpenFromDownloads : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri("com.android.providers.downloads.documents", "downloads")
        )
}

// Les fichiers .json de Téléchargements sont souvent typés application/octet-stream.
private val RuleFileMimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Sous-menus des Paramètres : une page par grande entrée du menu principal. */
private enum class SettingsPage { MAIN, RULES, GRID, DATABASE, DATA, ABOUT }

/**
 * Paramètres : réglages généraux (langue, mode d'analyse) en tête, puis une entrée par sous-menu
 * (règles de score, base de traqueurs, données, à propos). Le bouton Retour ramène au menu principal.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenLearn: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    // La grille est un sous-menu des règles de score : Retour y ramène.
    val back = { page = if (page == SettingsPage.GRID) SettingsPage.RULES else SettingsPage.MAIN }
    BackHandler(enabled = page != SettingsPage.MAIN) { back() }
    when (page) {
        SettingsPage.MAIN -> SettingsHome(modifier, viewModel, onOpen = { page = it })
        SettingsPage.RULES -> RulesPage(modifier, viewModel, homeViewModel, onOpenLearn, { page = SettingsPage.GRID }, back)
        SettingsPage.GRID -> {
            val loadedRules by homeViewModel.rules.collectAsStateWithLifecycle()
            ScoringGridScreen(modifier, loadedRules.rules, back)
        }
        SettingsPage.DATABASE -> DatabasePage(modifier, viewModel, back)
        SettingsPage.DATA -> DataPage(modifier, homeViewModel, back)
        SettingsPage.ABOUT -> AboutScreen(modifier, back)
    }
}

// --- Menu principal ---------------------------------------------------------------------------

@Composable
private fun SettingsHome(modifier: Modifier, viewModel: SettingsViewModel, onOpen: (SettingsPage) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SectionTitle(R.string.settings_general)
        LanguageCard()
        SettingsSwitch(
            title = stringResource(R.string.settings_advanced_mode),
            hint = stringResource(if (state.advancedMode) R.string.settings_advanced_hint else R.string.settings_basic_hint),
            checked = state.advancedMode,
            onChange = viewModel::setAdvancedMode,
        )

        SectionTitle(R.string.settings_analysis)
        SettingsLink(
            title = stringResource(R.string.settings_rules),
            hint = stringResource(
                if (state.rulesImported) R.string.settings_rules_imported else R.string.settings_rules_bundled,
                state.ruleCategoryCount,
            ),
            onClick = { onOpen(SettingsPage.RULES) },
        )
        SettingsLink(
            title = stringResource(R.string.settings_database),
            hint = if (state.isObsolete) stringResource(R.string.settings_database_obsolete_short)
            else stringResource(R.string.settings_database_hint, formatDate(state.lastUpdateTimestamp)),
            hintColor = if (state.isObsolete) MaterialTheme.colorScheme.error else Color.Unspecified,
            onClick = { onOpen(SettingsPage.DATABASE) },
        )

        SectionTitle(R.string.settings_data)
        SettingsLink(
            title = stringResource(R.string.settings_data_entry),
            hint = stringResource(R.string.settings_data_hint),
            onClick = { onOpen(SettingsPage.DATA) },
        )

        SectionTitle(R.string.settings_about_section)
        SettingsLink(
            title = stringResource(R.string.settings_about),
            hint = stringResource(R.string.settings_about_hint, BuildConfig.VERSION_NAME),
            onClick = { onOpen(SettingsPage.ABOUT) },
        )
    }
}

/** Langue de l'application (Android 13+ : la même que dans les réglages système de l'app). */
@Composable
private fun LanguageCard() {
    var current by remember { mutableStateOf(AppLanguage.current()) }
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppLanguage.entries.forEach { language ->
                val selected = current == language
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            current = language
                            language.select()
                        }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(
                        "${language.flag}  ${stringResource(language.label)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

// --- Règles de score --------------------------------------------------------------------------

@Composable
private fun RulesPage(
    modifier: Modifier,
    viewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    onOpenLearn: () -> Unit,
    onOpenGrid: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedRules by homeViewModel.rules.collectAsStateWithLifecycle()
    val scoring = loadedRules.rules.scoring
    val rulesLauncher = rememberLauncherForActivityResult(OpenFromDownloads()) { uri: Uri? ->
        uri?.let(viewModel::importRules)
    }
    SubPage(modifier, R.string.settings_rules, onBack) {
        InfoCard(R.string.rules_current) {
            Text(
                stringResource(
                    if (state.rulesImported) R.string.settings_rules_imported else R.string.settings_rules_bundled,
                    state.ruleCategoryCount,
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Hint(stringResource(R.string.rules_import_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { rulesLauncher.launch(RuleFileMimeTypes) }) {
                    Text(stringResource(R.string.rules_import))
                }
                if (state.rulesImported) {
                    OutlinedButton(onClick = viewModel::resetRules) {
                        Text(stringResource(R.string.rules_reset))
                    }
                }
            }
            state.rulesMessage?.let { message ->
                Text(
                    text = rulesMessage(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message is RulesMessage.Rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        InfoCard(R.string.rules_algorithm) {
            Text(stringResource(R.string.rules_algorithm_text, scoring.base), style = MaterialTheme.typography.bodyMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.learn_overview_formula, scoring.base, scoring.permissionCap, scoring.trackerCap),
                    modifier = Modifier.padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenGrid) { Text(stringResource(R.string.rules_open_grid)) }
                OutlinedButton(onClick = onOpenLearn) { Text(stringResource(R.string.rules_open_learn)) }
            }
        }
    }
}

@Composable
private fun rulesMessage(message: RulesMessage): String = when (message) {
    is RulesMessage.Imported -> stringResource(R.string.rules_imported_message, message.categoryCount)
    is RulesMessage.Rejected -> stringResource(
        R.string.rules_rejected_message,
        message.detail ?: stringResource(R.string.rules_invalid_file),
    )
    RulesMessage.Reset -> stringResource(R.string.rules_reset_message)
}

// --- Base de traqueurs ------------------------------------------------------------------------

@Composable
private fun DatabasePage(modifier: Modifier, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SubPage(modifier, R.string.settings_database, onBack) {
        InfoCard(R.string.database_status) {
            Text(
                stringResource(R.string.database_last_update, formatDate(state.lastUpdateTimestamp)),
                style = MaterialTheme.typography.bodyLarge
            )
            if (state.isObsolete) {
                Text(
                    stringResource(R.string.database_obsolete),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Hint(stringResource(R.string.database_update_hint))
            Button(onClick = viewModel::updateDatabase, enabled = !state.isUpdating) {
                if (state.isUpdating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.database_updating))
                } else {
                    Text(stringResource(R.string.database_update))
                }
            }
            state.updateError?.let {
                Text(
                    text = stringResource(R.string.database_update_error, it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.updateSuccess) {
                Text(
                    text = stringResource(R.string.database_update_success),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                LaunchedEffect(Unit) {
                    // reset success state after showing
                    viewModel.resetSuccessState()
                }
            }
        }
        ExodusCreditsCard()
    }
}

/** Source des signatures de traqueurs : Exodus Privacy, son logo, sa licence et la limite de la détection. */
@Composable
fun ExodusCreditsCard() {
    val context = LocalContext.current
    InfoCard(R.string.exodus_title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_exodus_logo),
                contentDescription = stringResource(R.string.exodus_logo_description),
                modifier = Modifier.size(48.dp)
            )
            Text(stringResource(R.string.exodus_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Text(stringResource(R.string.exodus_license), style = MaterialTheme.typography.bodyMedium)
        Hint(stringResource(R.string.exodus_limit))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openUrl(context, EXODUS_URL) }) { Text(stringResource(R.string.exodus_website)) }
            OutlinedButton(onClick = { openUrl(context, ODBL_URL) }) { Text(stringResource(R.string.exodus_odbl)) }
        }
    }
}

private const val EXODUS_URL = "https://exodus-privacy.eu.org/"
private const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"

// --- Données ----------------------------------------------------------------------------------

@Composable
private fun DataPage(modifier: Modifier, homeViewModel: HomeViewModel, onBack: () -> Unit) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val csvData = homeViewModel.exportCsv(csvLabels(resources))
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(csvData.toByteArray())
                        }
                    }
                    Toast.makeText(context, resources.getString(R.string.data_export_done), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, resources.getString(R.string.data_export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    SubPage(modifier, R.string.settings_data_entry, onBack) {
        InfoCard(R.string.data_export_title) {
            Text(stringResource(R.string.data_export_text), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { exportLauncher.launch(resources.getString(R.string.data_export_file_name)) },
                enabled = !homeState.isLoading,
            ) {
                Text(stringResource(R.string.data_export_button))
            }
            if (homeState.isAnalyzingTrackers) Hint(stringResource(R.string.data_export_partial))
        }
        InfoCard(R.string.data_local_title) {
            Text(stringResource(R.string.data_local_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** En-têtes et valeurs du rapport CSV, dans la langue de l'app. */
private fun csvLabels(resources: android.content.res.Resources) = CsvExporter.Labels(
    header = listOf(
        R.string.csv_app_name, R.string.csv_package, R.string.csv_type, R.string.csv_score, R.string.csv_grade,
        R.string.csv_confidence, R.string.csv_tracker_count, R.string.csv_trackers, R.string.csv_permissions,
    ).map(resources::getString),
    userApp = resources.getString(R.string.csv_user_app),
    systemApp = resources.getString(R.string.csv_system_app),
    notAnalyzed = resources.getString(R.string.csv_not_analyzed),
    analysisFailed = resources.getString(R.string.csv_analysis_failed),
    permissionTitle = resources::getString,
)

// --- Briques communes -------------------------------------------------------------------------

/** Sous-menu : flèche de retour, titre, puis contenu défilant. */
@Composable
internal fun SubPage(modifier: Modifier, @StringRes title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

/** Carte titrée d'un sous-menu. */
@Composable
internal fun InfoCard(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title).uppercase(currentLocale()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun SettingsSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLabel(title, hint, Color.Unspecified, Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SettingsLink(title: String, hint: String, onClick: () -> Unit, hintColor: Color = Color.Unspecified) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLabel(title, hint, hintColor, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsLabel(title: String, hint: String, hintColor: Color, modifier: Modifier) {
    Column(modifier.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = if (hintColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else hintColor
        )
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, currentLocale()).format(Date(timestamp))

/** Ouvre le navigateur du téléphone ; sans navigateur, rien ne se passe. */
internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: android.content.ActivityNotFoundException) {
        // Aucun navigateur installé.
    }
}
