package com.vylia.privacy.ui.act

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.act.Alternative
import com.vylia.privacy.domain.act.AlternativeCatalogue
import com.vylia.privacy.domain.act.AlternativeGroup
import com.vylia.privacy.domain.act.localized
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import java.text.NumberFormat

/**
 * Alternatives : une introduction (remplacer n'est pas toujours possible, réduire le risque vient d'abord, pas de
 * téléphone « dégooglisé »), un menu des catégories du Play Store, deux ou trois apps (avec leur logo) pour la catégorie
 * choisie, et en deux phrases la façon dont la liste est construite. Les alternatives déjà installées sont repérées.
 */
@Composable
internal fun AlternativesTab(allApps: List<AuditedApp>, catalogue: AlternativeCatalogue) {
    val language = currentLocale().language
    val installed = remember(allApps) { allApps.map { it.app.packageName }.toSet() }
    var selectedId by rememberSaveable { mutableStateOf(catalogue.groups.firstOrNull()?.id) }
    val selected = catalogue.groups.firstOrNull { it.id == selectedId } ?: catalogue.groups.firstOrNull()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Intro() }
        item { CategoryMenu(catalogue.groups, selected, language) { selectedId = it.id } }
        if (selected != null) {
            items(selected.alternatives, key = { "${selected.id}-${it.packageName}" }) { alt ->
                AlternativeCard(alt, alt.packageName in installed, language)
            }
        }
        item { MethodNote() }
    }
}

@Composable
private fun Intro() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.alt_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.alt_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.alt_reduce_first), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.alt_degoogle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.alt_trophy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Menu déroulant des catégories du Play Store. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryMenu(groups: List<AlternativeGroup>, selected: AlternativeGroup?, language: String, onSelect: (AlternativeGroup) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(
            value = selected?.title?.localized(language).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.alt_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text("${group.title.localized(language)}  ·  ${group.alternatives.size}") },
                    onClick = {
                        onSelect(group)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternativeCard(alt: Alternative, isInstalled: Boolean, language: String) {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AltIcon(alt.packageName)
                Text(alt.name + if (isInstalled) "  ✓" else "", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(alt.pitch.localized(language), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isInstalled) Tag(stringResource(R.string.alt_installed), Color(0xFF10B981))
                if (alt.cost == "Free") Tag(stringResource(R.string.alt_free), Color(0xFF10B981))
                if (alt.openSource) Tag(stringResource(R.string.alt_open_source), Color(0xFF38BDF8))
                if (alt.play != null) Tag("Google Play", Color(0xFF94A3B8))
                if (alt.fdroid != null) Tag("F-Droid", Color(0xFF94A3B8))
            }
            alt.likes?.takeIf { it > 0 }?.let {
                Text(
                    stringResource(R.string.alt_likes, NumberFormat.getIntegerInstance(currentLocale()).format(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isInstalled) {
                val label = when {
                    alt.play != null -> R.string.alt_get_play
                    alt.fdroid != null -> R.string.alt_get_fdroid
                    else -> R.string.alt_get_web
                }
                OutlinedButton(onClick = { openStore(context, alt) }) { Text(stringResource(label)) }
            }
        }
    }
}

/** Comment la liste est construite, en quelques mots. */
@Composable
private fun MethodNote() {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.alt_method_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.alt_method_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Icône de l'alternative, embarquée dans l'app (assets/alt_icons, voir tools/alternatives/fetch_icons.py). */
@Composable
private fun AltIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.assets.open("alt_icons/$packageName.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/**
 * Ouvre la page de l'alternative : le Play Store d'abord (l'app Play Store si elle est là, sinon sa page web), sinon
 * F-Droid, sinon son site. Le magasin ou le navigateur s'en chargent : VYLIA ne se connecte à rien.
 */
private fun openStore(context: Context, alt: Alternative) {
    val uris = when {
        alt.play != null -> listOf("market://details?id=${alt.play}", "https://play.google.com/store/apps/details?id=${alt.play}")
        alt.fdroid != null -> listOf("https://f-droid.org/packages/${alt.fdroid}/")
        else -> listOfNotNull(alt.web)
    }
    for (uri in uris) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // Pas d'app pour ce lien : on essaie le suivant.
        }
    }
}
