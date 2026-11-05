package com.vylia.privacy.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.scan.components.AppCard
import java.text.Normalizer

/** Vrai si le nom ou le paquet de l'app contient [query], sans tenir compte de la casse ni des accents. */
fun AuditedApp.matchesQuery(query: String): Boolean {
    val needle = query.trim().normalizedForSearch()
    return needle.isEmpty() || needle in app.label.normalizedForSearch() || needle in app.packageName.lowercase()
}

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DiacriticalMarks, "").lowercase()

private val DiacriticalMarks = Regex("\\p{Mn}+")

/** Recherche globale, ouverte par la loupe de n'importe quel écran : un appui sur une app ouvre sa fiche. */
@Composable
fun GlobalSearchScreen(apps: List<AuditedApp>, query: String, onAppClick: (AuditedApp) -> Unit, modifier: Modifier = Modifier) {
    val results = remember(apps, query) { if (query.isBlank()) emptyList() else apps.filter { it.matchesQuery(query) } }
    when {
        query.isBlank() -> Message(stringResource(R.string.search_prompt), modifier)
        results.isEmpty() -> Message(stringResource(R.string.home_search_empty, query.trim()), modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(results, key = { it.app.packageName }) { AppCard(it, onClick = { onAppClick(it) }) }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
