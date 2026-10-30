package com.vylia.privacy.ui.home.components

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.domain.learn.CategoryRationale
import com.vylia.privacy.domain.learn.GradeBand
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.learn.gradeRiskLabel
import com.vylia.privacy.domain.scoring.Category
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.scoreGradientColor

/** "Dernier scan : aujourd'hui à 22:41", ou l'état du scan en cours. */
@Composable
fun LastScanLine(lastScanAt: Long?, scanning: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val last = lastScanAt?.let {
        DateUtils.formatDateTime(
            context, it,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = when {
                scanning && last != null -> "Scan en cours · dernier scan complet : $last"
                scanning -> "Premier scan en cours…"
                last != null -> "Dernier scan : $last"
                else -> "Aucun scan terminé"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Filtre par note (A à E), chaque note dans la couleur de l'échelle. `null` : toutes. */
@Composable
fun GradeFilterRow(
    bands: List<GradeBand>,
    scoreBase: Int,
    counts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Toutes") })
        bands.forEach { band ->
            val count = counts[band.grade] ?: 0
            FilterChip(
                selected = selected == band.grade,
                onClick = { onSelect(if (selected == band.grade) null else band.grade) },
                enabled = count > 0 || selected == band.grade,
                label = {
                    Text(band.grade, fontWeight = FontWeight.Bold, color = scoreGradientColor((band.min + band.max) / 2, scoreBase))
                    Text(" · $count")
                },
            )
        }
    }
}

/** Menu des catégories présentes dans le scan, avec leur nombre d'apps. `null` : toutes. */
@Composable
fun CategoryFilter(
    options: List<Triple<String, String, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text("Catégorie : " + (options.find { it.first == selected }?.second ?: "toutes")) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Toutes les catégories") }, onClick = { onSelect(null); expanded = false })
            options.forEach { (id, name, count) ->
                DropdownMenuItem(text = { Text("$name ($count)") }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

/** En-tête d'une catégorie : permissions justifiées et tolérées, et pourquoi. */
@Composable
fun CategoryExpectationsCard(categoryId: String, category: Category, modifier: Modifier = Modifier) {
    fun titles(permissions: Set<String>) = permissions.joinToString { PermissionGlossary.find(it)?.title ?: it }
    VyliaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(category.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            CategoryRationale.of(categoryId, category.description)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            ExpectationLine("Justifiées", if (category.allowed.isEmpty()) "aucune" else titles(category.allowed), MaterialTheme.colorScheme.primary)
            if (category.tolerated.isNotEmpty()) {
                ExpectationLine("Tolérées", titles(category.tolerated), MaterialTheme.colorScheme.secondary)
            }
            Text(
                "Toute autre permission sensible est pénalisée comme inattendue ou interdite.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ExpectationLine(label: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label : ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** En-tête d'une note sélectionnée : niveau de risque. */
@Composable
fun GradeFilterSummary(band: GradeBand, scoreBase: Int, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "Note ${band.grade} · ${gradeRiskLabel(band.grade)} (${band.min} à ${band.max}) · $count app(s)",
        style = MaterialTheme.typography.labelLarge,
        color = scoreGradientColor((band.min + band.max) / 2, scoreBase),
        modifier = modifier,
    )
}
