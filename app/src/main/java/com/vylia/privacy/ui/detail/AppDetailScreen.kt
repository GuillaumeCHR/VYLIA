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
import androidx.compose.material3.HorizontalDivider
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
import com.vylia.privacy.domain.learn.GradeBand
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.learn.gradeRiskLabel
import com.vylia.privacy.ui.components.GradeScale
import com.vylia.privacy.ui.components.scoreGradientColor
import com.vylia.privacy.ui.understand.PermissionExplanation
import com.vylia.privacy.domain.scoring.TrackerFinding
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.BadgeChip
import com.vylia.privacy.ui.components.badgeDescription
import com.vylia.privacy.ui.components.securityDescription
import com.vylia.privacy.ui.components.securityLabel
import com.vylia.privacy.domain.scoring.ScoreResult
import androidx.compose.ui.platform.LocalResources
import com.vylia.privacy.ui.components.TransparencyBadge
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.theme.color
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted

/** Détail du score v2 : catégorie, confiance, constats permissions et traqueurs. */
@Composable
fun AppDetailScreen(
    auditedApp: AuditedApp,
    categoryName: String,
    gradeBands: List<GradeBand>,
    scoreBase: Int,
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
                    TransparencyBadge(text = stringResource(R.string.detail_category, categoryName))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreBadge(auditedApp.score)
                    Text(
                        text = stringResource(R.string.grade_label, result.grade),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = auditedApp.score.rating.color.accent,
                    )
                }
            }
        }

        VyliaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${stringResource(gradeRiskLabel(result.grade))} · ${result.score}/$scoreBase",
                    fontWeight = FontWeight.Bold,
                    color = scoreGradientColor(result.score, scoreBase),
                )
                GradeScale(gradeBands, base = scoreBase, marker = result.score)
            }
        }

        if (result.securityFindings.isNotEmpty()) SecurityCard(result)
        if (result.badges.isNotEmpty()) BadgesCard(result)

        VyliaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (result.confidence == Confidence.FULL) {
                    Text(stringResource(R.string.detail_confidence_full), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.detail_confidence_full_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.detail_confidence_partial), fontWeight = FontWeight.Bold)
                    Text(
                        text = if (auditedApp.trackerAnalysis == TrackerAnalysis.Failed) {
                            stringResource(R.string.detail_apk_failed, result.trackerPenalty)
                        } else {
                            stringResource(R.string.detail_trackers_pending, result.trackerPenalty)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                PenaltyRow(stringResource(R.string.detail_permissions), result.permissionPenalty)
                PenaltyRow(stringResource(R.string.detail_trackers), result.trackerPenalty.toDouble())
            }
        }

        AllPermissionsCard(app, result.permissionFindings)

        SectionCard(title = stringResource(R.string.detail_trackers_section, result.trackerFindings.size)) {
            when {
                auditedApp.trackerAnalysis !is TrackerAnalysis.Analyzed -> EmptyLine(stringResource(R.string.detail_trackers_not_analyzed))
                result.trackerFindings.isEmpty() -> EmptyLine(stringResource(R.string.detail_no_tracker))
                else -> result.trackerFindings.forEachIndexed { index, finding ->
                    if (index > 0) HorizontalDivider()
                    TrackerLine(finding)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.detail_trackers_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tous les badges de l'app, bonnes pratiques d'abord, chacun avec son explication. */
@Composable
private fun BadgesCard(result: ScoreResult) {
    val resources = LocalResources.current
    SectionCard(title = stringResource(R.string.badges_title)) {
        Text(
            stringResource(R.string.badges_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            "positive" to R.string.badges_positive,
            "negative" to R.string.badges_negative,
            "neutral" to R.string.badges_neutral,
        ).forEach { (polarity, title) ->
            val badges = result.badges.filter { it.polarity == polarity }
            if (badges.isEmpty()) return@forEach
            Spacer(Modifier.height(10.dp))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            badges.forEach { badge ->
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    BadgeChip(badge)
                    resources.badgeDescription(badge)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Patterns de capacités typiques des logiciels espions, et le plafond qu'ils imposent au score. */
@Composable
private fun SecurityCard(result: ScoreResult) {
    val resources = LocalResources.current
    VyliaCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            result.securityFindings.forEach { finding ->
                Text("⚠️ " + resources.securityLabel(finding), fontWeight = FontWeight.Bold)
                resources.securityDescription(finding)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            result.securityScoreCap?.let {
                Text(stringResource(R.string.security_cap, it), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.security_hint), style = MaterialTheme.typography.bodySmall)
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
internal fun PenaltyText(penalty: Double) {
    Text(
        text = if (penalty == 0.0) "0" else "−" + penalty.formatted(currentLocale(), decimals = 1),
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

@get:StringRes
internal val Context.label: Int
    get() = when (this) {
        Context.ALLOWED -> R.string.detail_context_allowed
        Context.TOLERATED -> R.string.detail_context_tolerated
        Context.UNEXPECTED -> R.string.detail_context_unexpected
        Context.FORBIDDEN -> R.string.detail_context_forbidden
    }

internal val Context.tint: Color
    @Composable get() = when (this) {
        Context.ALLOWED, Context.TOLERATED -> MaterialTheme.colorScheme.primary
        Context.UNEXPECTED -> Color(0xFFF59E0B)
        Context.FORBIDDEN -> MaterialTheme.colorScheme.error
    }
