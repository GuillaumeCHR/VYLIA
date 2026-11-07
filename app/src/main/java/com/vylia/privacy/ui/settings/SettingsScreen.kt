package com.vylia.privacy.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.vylia.privacy.data.preferences.BackgroundScan
import com.vylia.privacy.data.preferences.CardStyle
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.scan.ScanViewModel
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
/** Même nom que le fichier intégré : l'export se réimporte tel quel. */
private const val RulesExportFileName = "vylia_rules_v3.json"

private val RuleFileMimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Sous-menus des Paramètres : une page par grande entrée du menu principal. */
private enum class SettingsPage { MAIN, RULES, DATABASE, AUTOMATION, DATA, ABOUT }

/**
 * Paramètres : réglages généraux (langue, mode d'analyse) en tête, puis une entrée par sous-menu
 * (règles de score, base de traqueurs, données, à propos). Le bouton Retour ramène au menu principal.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    /** Ouvrir directement Automatisations (lien « Modifier » de l'alerte de seuil, dans le Suivi). */
    openAutomation: Boolean = false,
    /** Ouvre l'Aide sur « Comment fonctionne VYLIA ». */
    onOpenHowItWorks: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    homeViewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory)
) {
    var page by rememberSaveable { mutableStateOf(if (openAutomation) SettingsPage.AUTOMATION else SettingsPage.MAIN) }
    val back = { page = SettingsPage.MAIN }
    BackHandler(enabled = page != SettingsPage.MAIN) { back() }
    when (page) {
        SettingsPage.MAIN -> SettingsHome(modifier, viewModel, onOpen = { page = it }, onOpenHowItWorks = onOpenHowItWorks)
        SettingsPage.RULES -> RulesPage(modifier, viewModel, back)
        SettingsPage.DATABASE -> DatabasePage(modifier, viewModel, back)
        SettingsPage.AUTOMATION -> AutomationPage(modifier, viewModel, back)
        SettingsPage.DATA -> DataPage(modifier, viewModel, homeViewModel, back)
        SettingsPage.ABOUT -> AboutScreen(modifier, back)
    }
}

// --- Menu principal ---------------------------------------------------------------------------

@Composable
private fun SettingsHome(modifier: Modifier, viewModel: SettingsViewModel, onOpen: (SettingsPage) -> Unit, onOpenHowItWorks: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val automation by viewModel.automation.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Titre et retour : barre d'en-tête de l'écran principal.
        SectionTitle(R.string.settings_general)
        LanguageCard()
        SettingsSwitch(
            title = stringResource(R.string.settings_advanced_mode),
            hint = stringResource(if (state.advancedMode) R.string.settings_advanced_hint else R.string.settings_basic_hint),
            checked = state.advancedMode,
            onChange = viewModel::setAdvancedMode,
        )
        CardStyleCard(viewModel)

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

        SettingsLink(
            title = stringResource(R.string.settings_automation),
            hint = stringResource(
                when {
                    automation.installAlerts && automation.backgroundScan != BackgroundScan.OFF -> R.string.settings_automation_both
                    automation.installAlerts -> R.string.settings_automation_install
                    automation.backgroundScan != BackgroundScan.OFF -> R.string.settings_automation_scan
                    else -> R.string.settings_automation_off
                }
            ),
            onClick = { onOpen(SettingsPage.AUTOMATION) },
        )

        SectionTitle(R.string.settings_data)
        SettingsLink(
            title = stringResource(R.string.settings_data_entry),
            hint = stringResource(R.string.settings_data_hint),
            onClick = { onOpen(SettingsPage.DATA) },
        )

        SectionTitle(R.string.settings_about_section)
        SettingsLink(
            title = stringResource(R.string.howto_title),
            hint = stringResource(R.string.settings_howto_hint),
            onClick = onOpenHowItWorks,
        )
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
    RadioCard(
        title = stringResource(R.string.settings_language),
        options = AppLanguage.entries,
        selected = current,
        label = { "${it.flag}  ${stringResource(it.label)}" },
        onSelect = { current = it; it.select() },
    )
}

/** Présentation des cartes d'apps dans les listes Scan et la recherche. */
@Composable
private fun CardStyleCard(viewModel: SettingsViewModel) {
    val current by viewModel.cardStyle.collectAsStateWithLifecycle()
    RadioCard(
        title = stringResource(R.string.settings_cards),
        options = CardStyle.entries,
        selected = current,
        label = {
            stringResource(if (it == CardStyle.COMPACT) R.string.settings_cards_compact else R.string.settings_cards_detailed)
        },
        hint = {
            stringResource(if (it == CardStyle.COMPACT) R.string.settings_cards_compact_hint else R.string.settings_cards_detailed_hint)
        },
        onSelect = viewModel::setCardStyle,
    )
}

@Composable
private fun <T> RadioCard(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    hint: (@Composable (T) -> String)? = null,
) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp).selectableGroup()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                        hint?.let { Hint(it(option)) }
                    }
                }
            }
        }
    }
}

// --- Règles de score --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesPage(modifier: Modifier, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rulesLauncher = rememberLauncherForActivityResult(OpenFromDownloads()) { uri: Uri? ->
        uri?.let(viewModel::importRules)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let(viewModel::exportRules)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { rulesLauncher.launch(RuleFileMimeTypes) }) {
                    Text(stringResource(R.string.rules_import))
                }
                OutlinedButton(onClick = { exportLauncher.launch(RulesExportFileName) }) {
                    Text(stringResource(R.string.rules_export))
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
                    color = if (message is RulesMessage.Rejected || message is RulesMessage.ExportFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
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
    RulesMessage.Exported -> stringResource(R.string.rules_exported_message)
    is RulesMessage.ExportFailed -> stringResource(
        R.string.rules_export_failed_message,
        message.detail ?: stringResource(R.string.rules_invalid_file),
    )
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
        StalkerwareUpdateCard(viewModel)
        EchapCreditsCard()
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

/** Base des stalkerwares connus : version en vigueur et mise à jour, comme pour la base Exodus. */
@Composable
private fun StalkerwareUpdateCard(viewModel: SettingsViewModel) {
    val state by viewModel.stalkerware.collectAsStateWithLifecycle()
    val updating by viewModel.stalkerwareUpdating.collectAsStateWithLifecycle()
    val message by viewModel.stalkerwareMessage.collectAsStateWithLifecycle()
    InfoCard(R.string.stalkerware_status) {
        Text(
            state.downloadedAt?.let { stringResource(R.string.stalkerware_downloaded, state.database.apps, formatDate(it)) }
                ?: stringResource(R.string.stalkerware_embedded, state.database.apps, state.database.commit.take(7)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Hint(stringResource(R.string.stalkerware_update_hint))
        Button(onClick = viewModel::updateStalkerware, enabled = !updating) {
            if (updating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.database_updating))
            } else {
                Text(stringResource(R.string.stalkerware_update))
            }
        }
        when (val m = message) {
            is StalkerwareMessage.Updated -> Text(
                stringResource(R.string.stalkerware_update_success, m.count),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
            is StalkerwareMessage.Failed -> Text(
                stringResource(R.string.database_update_error, m.detail ?: stringResource(R.string.rules_invalid_file)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            null -> Unit
        }
    }
}

/** Echap et sa base de stalkerwares, sur le modèle de la carte Exodus : qui, quelle licence, quelles limites. */
@Composable
fun EchapCreditsCard() {
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as com.vylia.privacy.VyliaApplication).container.stalkerwareRepository
    }
    val state by repository.current.collectAsStateWithLifecycle()
    val stalkerware = state.database
    val version = state.downloadedAt?.let { formatDate(it) } ?: stalkerware.commit.take(7).ifEmpty { "?" }
    InfoCard(R.string.echap_title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Pas de logo : celui d'Echap lui appartient. Le badge « Avis de recherche » dit de quoi il s'agit.
            Text("🕵️", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clearAndSetSemantics {})
            Text(stringResource(R.string.echap_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.echap_license, stalkerware.apps, version),
            style = MaterialTheme.typography.bodyMedium,
        )
        Hint(stringResource(R.string.echap_limit))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openUrl(context, ECHAP_URL) }) { Text(stringResource(R.string.echap_website)) }
            OutlinedButton(onClick = { openUrl(context, CC_BY_URL) }) { Text(stringResource(R.string.echap_cc_by)) }
        }
    }
}

private const val ECHAP_URL = "https://echap.eu.org/"
private const val CC_BY_URL = "https://creativecommons.org/licenses/by/4.0/"
private const val EXODUS_URL = "https://exodus-privacy.eu.org/"
private const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"

// --- Briques communes -------------------------------------------------------------------------

/**
 * Sous-menu : titre, puis contenu défilant. Le retour passe par la flèche de la barre d'en-tête et le geste retour
 * d'Android, tous deux reçus par le [BackHandler] de [SettingsScreen] ; [onBack] reste pour les appelants.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SubPage(modifier: Modifier, @StringRes title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
