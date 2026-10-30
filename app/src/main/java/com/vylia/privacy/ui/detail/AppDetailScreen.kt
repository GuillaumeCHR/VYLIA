package com.vylia.privacy.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.domain.scoring.PermissionFinding
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.ui.main.PermissionExplanation
import com.vylia.privacy.domain.scoring.TrackerFinding
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.TransparencyBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.theme.color
import java.util.Locale

/** Détail du score v2 : catégorie, confiance, constats permissions et traqueurs. */
@Composable
fun AppDetailScreen(
    auditedApp: AuditedApp,
    categoryName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val result = auditedApp.result
    val app = auditedApp.app

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text("Détail du score", style = MaterialTheme.typography.titleLarge)
        }

        VyliaCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    TransparencyBadge(text = "Catégorie : $categoryName")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreBadge(auditedApp.score)
                    Text(
                        text = "Note ${result.grade}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = auditedApp.score.rating.color.accent,
                    )
                }
            }
        }

        VyliaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (result.confidence == Confidence.FULL) {
                    Text("Confiance : complète", fontWeight = FontWeight.Bold)
                    Text(
                        "Permissions et traqueurs ont été analysés.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Confiance : partielle (note suivie de « ? »)", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (auditedApp.trackerAnalysis == TrackerAnalysis.Failed) {
                            "L'APK n'a pas pu être analysé : une pénalité forfaitaire de ${result.trackerPenalty} pts remplace les traqueurs."
                        } else {
                            "Analyse des traqueurs pas encore faite : une pénalité forfaitaire de ${result.trackerPenalty} pts est appliquée en attendant."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                PenaltyRow("Permissions", result.permissionPenalty)
                PenaltyRow("Traqueurs", result.trackerPenalty.toDouble())
            }
        }

        SectionCard(title = "Permissions (${result.permissionFindings.size}) · touchez pour comprendre") {
            if (result.permissionFindings.isEmpty()) {
                EmptyLine("Aucune permission sensible.")
            } else {
                result.permissionFindings.forEachIndexed { index, finding ->
                    if (index > 0) HorizontalDivider()
                    PermissionLine(finding)
                }
            }
        }

        SectionCard(title = "Traqueurs (${result.trackerFindings.size})") {
            when {
                auditedApp.trackerAnalysis !is TrackerAnalysis.Analyzed -> EmptyLine("Non analysés pour l'instant.")
                result.trackerFindings.isEmpty() -> EmptyLine("Aucun traqueur connu détecté.")
                else -> result.trackerFindings.forEachIndexed { index, finding ->
                    if (index > 0) HorizontalDivider()
                    TrackerLine(finding)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Détection locale par signatures de code (base Exodus Privacy) : un SDK obfusqué ou renommé " +
                    "peut passer inaperçu. Une absence de traqueur n'est donc pas une garantie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Touchez une permission pour lire son explication (lexique de l'onglet Apprendre). */
@Composable
private fun PermissionLine(finding: PermissionFinding) {
    val info = PermissionGlossary.find(finding.permission)
    var expanded by rememberSaveable(finding.permission) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = info != null) { expanded = !expanded }
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (info?.title ?: finding.permission) + if (info == null) "" else if (expanded) "  ▲" else "  ▼",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${finding.permission} · ${if (finding.granted) "accordée" else "déclarée seulement"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = finding.context.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = finding.context.tint,
                )
            }
            PenaltyText(finding.penalty)
        }
        if (expanded && info != null) {
            Spacer(Modifier.height(8.dp))
            PermissionExplanation(info)
        }
    }
}

@Composable
private fun TrackerLine(finding: TrackerFinding) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(finding.name, modifier = Modifier.weight(1f))
        PenaltyText(finding.penalty.toDouble())
    }
}

@Composable
private fun PenaltyRow(label: String, penalty: Double) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        PenaltyText(penalty)
    }
}

@Composable
private fun PenaltyText(penalty: Double) {
    Text(
        text = if (penalty == 0.0) "0" else "−" + String.format(Locale.FRANCE, "%.1f", penalty).removeSuffix(",0"),
        fontWeight = FontWeight.Bold,
        color = if (penalty == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppIcon(drawable: android.graphics.drawable.Drawable) {
    val sizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bitmap = remember(drawable, sizePx) { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(48.dp))
}

private val Context.label: String
    get() = when (this) {
        Context.ALLOWED -> "Justifiée pour cette catégorie"
        Context.TOLERATED -> "Tolérée pour cette catégorie"
        Context.UNEXPECTED -> "Inattendue pour cette catégorie"
        Context.FORBIDDEN -> "Interdite pour cette catégorie"
    }

private val Context.tint: Color
    @Composable get() = when (this) {
        Context.ALLOWED, Context.TOLERATED -> MaterialTheme.colorScheme.primary
        Context.UNEXPECTED -> Color(0xFFF59E0B)
        Context.FORBIDDEN -> MaterialTheme.colorScheme.error
    }
