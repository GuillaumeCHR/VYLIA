package com.vylia.privacy.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import android.net.Uri
import com.vylia.privacy.ui.home.HomeViewModel

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

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val csvData = homeViewModel.exportCsv()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(csvData.toByteArray())
                        }
                    }
                    Toast.makeText(context, "Export réussi", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur lors de l'export", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val rulesLauncher = rememberLauncherForActivityResult(OpenFromDownloads()) { uri: Uri? ->
        uri?.let(viewModel::importRules)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(text = "Paramètres", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Mode d'analyse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.advancedMode) "Mode Avancé" else "Mode Basique",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.advancedMode) {
                        "Toutes les applications sont notées, y compris les applications système."
                    } else {
                        "Seules vos applications sont notées ; les applications système sont masquées."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(checked = state.advancedMode, onCheckedChange = viewModel::setAdvancedMode)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Règles de score (v2)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = (if (state.rulesImported) "Règles importées" else "Règles intégrées à l'application") +
                " · ${state.ruleCategoryCount} catégories",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Importez votre propre vylia_rules_v2.json (dossier Téléchargements). " +
                "Le fichier est copié dans l'application ; en cas de problème, les règles intégrées sont utilisées.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { rulesLauncher.launch(RuleFileMimeTypes) }) {
                Text("Importer des règles")
            }
            if (state.rulesImported) {
                OutlinedButton(onClick = viewModel::resetRules) {
                    Text("Règles intégrées")
                }
            }
        }
        state.rulesMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Gestion des données", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { exportLauncher.launch("vylia_audit_report.csv") },
            enabled = !homeState.isLoading,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Exporter le rapport d'audit (CSV)")
        }
        if (homeState.isAnalyzingTrackers) {
            Text(
                text = "Analyse des traqueurs en cours : les applications pas encore analysées seront marquées « Non analysé ».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Base de données Exodus Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Dernière mise à jour : ${state.lastUpdateDate}", style = MaterialTheme.typography.bodyMedium)

        if (state.isObsolete) {
            Text(
                text = "Attention : La base de données a plus de 90 jours. Une mise à jour est recommandée.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = viewModel::updateDatabase,
            enabled = !state.isUpdating,
            modifier = Modifier.align(Alignment.Start)
        ) {
            if (state.isUpdating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mise à jour en cours...")
            } else {
                Text("Mettre à jour la base de signatures")
            }
        }

        if (state.updateError != null) {
            Text(
                text = "Erreur : ${state.updateError}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.updateSuccess) {
            Text(
                text = "Mise à jour réussie ! Veuillez relancer l'analyse (onglet Scan) pour actualiser les scores.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            LaunchedEffect(Unit) {
                // reset success state after showing
                viewModel.resetSuccessState()
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Algorithme VYLIA (Open Source)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Le Privacy Score est calculé de 0 à 100 de manière 100% locale, selon la formule suivante :\n\n" +
                   "Score = 100\n" +
                   "- Pénalités liées aux traqueurs (de 0 à -50 pts selon le nombre)\n" +
                   "- Pénalités liées aux permissions déclarées (ex: -30 pts pour localisation en arrière-plan)\n" +
                   "- Malus d'adéquation (jusqu'à -20 pts si une permission ne correspond pas au type de l'application)",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Crédits & Partenaires", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Un grand merci à Exodus Privacy pour la mise à disposition de leur base de signatures de traqueurs sous licence ODbL.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "https://exodus-privacy.eu.org/",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
