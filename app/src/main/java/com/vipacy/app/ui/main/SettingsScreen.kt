package com.vipacy.app.ui.main

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
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.net.Uri

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val csvData = viewModel.generateExportData()
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvData.toByteArray())
                    }
                    Toast.makeText(context, "Export réussi", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur lors de l'export", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(text = "Paramètres", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Gestion des données", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { exportLauncher.launch("vipacy_audit_report.csv") },
            enabled = !state.isExporting,
            modifier = Modifier.align(Alignment.Start)
        ) {
            if (state.isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Génération en cours...")
            } else {
                Text("Exporter le rapport d'audit (CSV)")
            }
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

        Text(text = "Algorithme VIPACY (Open Source)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
