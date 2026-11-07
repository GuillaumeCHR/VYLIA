package com.vylia.privacy.ui.scan.components

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import com.vylia.privacy.domain.scoring.Rules
import com.vylia.privacy.ui.components.categoryName

/** "Dernier scan : aujourd'hui à 22:41", ou l'état du scan en cours. */
@Composable
fun LastScanLine(
    lastScanAt: Long?,
    scanning: Boolean,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    refreshEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val last = lastScanAt?.let {
        DateUtils.formatDateTime(
            context, it,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    // Le bouton « Relancer le scan » termine la ligne (cible de 48 dp).
    Row(modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = when {
                scanning && last != null -> stringResource(R.string.scan_running_last, last)
                scanning -> stringResource(R.string.scan_first_running)
                last != null -> stringResource(R.string.scan_last, last)
                else -> stringResource(R.string.scan_none)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.home_refresh))
            }
        }
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
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.filter_all_grades)) })
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

/** En-tête d'une catégorie : permissions justifiées et tolérées, et pourquoi. */
@Composable
fun CategoryExpectationsCard(categoryId: String, category: Category, rules: Rules?, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    fun titles(permissions: Set<String>) =
        permissions.joinToString { PermissionGlossary.find(it)?.title?.let(resources::getString) ?: it }
    VyliaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(resources.categoryName(categoryId, rules), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            (CategoryRationale.of(categoryId)?.let { stringResource(it) } ?: category.description)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            ExpectationLine(
                stringResource(R.string.expectations_allowed),
                if (category.allowed.isEmpty()) stringResource(R.string.expectations_none) else titles(category.allowed),
                MaterialTheme.colorScheme.primary,
            )
            if (category.tolerated.isNotEmpty()) {
                ExpectationLine(stringResource(R.string.expectations_tolerated), titles(category.tolerated), MaterialTheme.colorScheme.secondary)
            }
            Text(
                stringResource(R.string.expectations_other),
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
        Text(stringResource(R.string.label_colon, label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** En-tête d'une note sélectionnée : niveau de risque. */
@Composable
fun GradeFilterSummary(band: GradeBand, scoreBase: Int, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.grade_filter_summary, band.grade, stringResource(gradeRiskLabel(band.grade)), band.min, band.max) +
            " · " + pluralStringResource(R.plurals.app_count, count, count),
        style = MaterialTheme.typography.labelLarge,
        color = scoreGradientColor((band.min + band.max) / 2, scoreBase),
        modifier = modifier,
    )
}
