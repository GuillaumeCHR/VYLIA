package com.vylia.privacy.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.rewards.Badge
import com.vylia.privacy.ui.components.VyliaLogotype
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.splash.VyliaLogo
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

// Couleurs fixes des images (identité VYLIA, quel que soit le thème du téléphone).
internal val CardBackground = Color(0xFF0F172A)
internal val CardPanel = Color(0xFF1E293B)
internal val CardLine = Color(0xFF334155)
internal val CardText = Color(0xFFCBD5E1)
internal val CardMuted = Color(0xFF94A3B8)
internal val CardEmerald = Color(0xFF10B981)
internal val CardCyan = Color(0xFF38BDF8)
internal val CardAmber = Color(0xFFF59E0B)
internal val CardRed = Color(0xFFEF4444)
internal val CardSpy = Color(0xFF7C3AED)

/** Couleur d'un score, comme l'échelle de l'app : rouge sous 50, ambre sous 80, émeraude au-delà. */
internal fun scoreColor(score: Int): Color = when (PrivacyScore(score.coerceIn(0, 100)).rating) {
    ScoreRating.GOOD -> CardEmerald
    ScoreRating.FAIR -> CardAmber
    ScoreRating.POOR -> CardRed
}

internal fun ratingLabel(score: Int): Int = when (PrivacyScore(score.coerceIn(0, 100)).rating) {
    ScoreRating.GOOD -> R.string.risk_low
    ScoreRating.FAIR -> R.string.risk_medium
    ScoreRating.POOR -> R.string.risk_high
}

/** Largeur de référence : 360 dp à 3 px/dp = 1 080 px, quel que soit le téléphone. */
private const val CARD_WIDTH_DP = 360
private const val CARD_DENSITY = 3f

/**
 * Une image prête à capturer, à sa taille réelle en pixels ([ShareFormat]). Densité fixe et taille de police
 * système ignorée : l'image produite est la même sur tous les téléphones.
 */
@Composable
fun ShareCard(content: ShareContent, template: ShareTemplate, format: ShareFormat, modifier: Modifier = Modifier) {
    val heightDp = CARD_WIDTH_DP * format.height / format.width
    val compact = format == ShareFormat.SQUARE
    CompositionLocalProvider(LocalDensity provides Density(CARD_DENSITY, 1f)) {
        Box(
            modifier
                .requiredSize(CARD_WIDTH_DP.dp, heightDp.dp)
                .clipToBounds()
                .background(Brush.verticalGradient(listOf(CardBackground, Color(0xFF111C33), CardBackground))),
        ) {
            Glow(template)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = if (compact) 18.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (template) {
                    ShareTemplate.REPORT -> ReportCard(content, compact)
                    ShareTemplate.TRACKERS -> TrackersCard(content, compact)
                    ShareTemplate.SHIELD -> ShieldCardImage(content, compact)
                    ShareTemplate.PROGRESS -> ProgressCard(content, compact)
                    ShareTemplate.TROPHIES -> TrophiesCard(content, compact)
                }
                Footer(compact)
            }
        }
    }
}

/** Halo coloré en haut de l'image, couleur du modèle. */
@Composable
private fun Glow(template: ShareTemplate) {
    val color = when (template) {
        ShareTemplate.REPORT, ShareTemplate.PROGRESS -> CardEmerald
        ShareTemplate.TRACKERS -> CardSpy
        ShareTemplate.SHIELD -> CardCyan
        ShareTemplate.TROPHIES -> CardAmber
    }
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.95f),
            radius = size.width * 0.95f,
            center = Offset(size.width * 0.5f, 0f),
        )
    }
}

@Composable
private fun ColumnScope.ReportCard(content: ShareContent, compact: Boolean) {
    Title(stringResource(R.string.card_report_title), compact)
    Spacer(Modifier.weight(1f))
    ScoreRing(content.score, if (compact) 132.dp else 190.dp)
    Spacer(Modifier.height(10.dp))
    Text(stringResource(ratingLabel(content.score)), color = scoreColor(content.score), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(content.trackers.toLong(), pluralStringResource(R.plurals.card_stat_trackers, content.trackers), CardSpy, Modifier.weight(1f))
        if (content.shieldBlocked > 0) {
            Stat(content.shieldBlocked, pluralStringResource(R.plurals.card_stat_blocked, content.shieldBlocked.toQuantity()), CardCyan, Modifier.weight(1f))
        } else {
            Stat(content.appCount.toLong(), pluralStringResource(R.plurals.card_stat_apps, content.appCount), CardCyan, Modifier.weight(1f))
        }
        Stat(content.unlockedBadges.size.toLong(), pluralStringResource(R.plurals.card_stat_trophies, content.unlockedBadges.size), CardAmber, Modifier.weight(1f))
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.TrackersCard(content: ShareContent, compact: Boolean) {
    Spacer(Modifier.weight(0.6f))
    SpyBadge(if (compact) 54.dp else 76.dp)
    Spacer(Modifier.height(8.dp))
    BigNumber(content.trackers.toLong(), CardText, if (compact) 64.sp else 92.sp)
    Text(
        pluralStringResource(R.plurals.card_trackers_label, content.trackers),
        color = CardText, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center,
    )
    Text(
        stringResource(R.string.card_trackers_sub, content.appsWithTrackers, content.analyzedApps),
        color = CardMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(0.6f))
    val categories = if (compact) content.categories.take(2) else content.categories
    if (categories.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardPanel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.card_trackers_top), color = CardMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val max = categories.first().second.coerceAtLeast(1)
            categories.forEach { (name, count) -> Bar(name, count, count / max.toFloat()) }
        }
    }
    Spacer(Modifier.weight(0.6f))
}

@Composable
private fun ColumnScope.ShieldCardImage(content: ShareContent, compact: Boolean) {
    Title(stringResource(R.string.card_shield_title), compact)
    Spacer(Modifier.weight(1f))
    Box(
        Modifier.size(if (compact) 84.dp else 120.dp).clip(CircleShape).background(CardCyan.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Text("🧿", fontSize = if (compact) 46.sp else 66.sp) }
    Spacer(Modifier.height(14.dp))
    BigNumber(content.shieldBlocked, CardCyan, if (compact) 54.sp else 76.sp)
    Text(
        pluralStringResource(R.plurals.card_shield_label, content.shieldBlocked.toQuantity()),
        color = CardText, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.card_shield_sub), color = CardMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.ProgressCard(content: ShareContent, compact: Boolean) {
    Title(stringResource(R.string.card_progress_title), compact)
    Spacer(Modifier.weight(0.7f))
    val sign = if (content.progress > 0) "+" else "−"
    Text(
        stringResource(R.string.card_progress_points, sign + kotlin.math.abs(content.progress)),
        color = if (content.progress > 0) CardEmerald else CardAmber,
        fontWeight = FontWeight.Black, fontSize = if (compact) 56.sp else 80.sp,
    )
    val since = content.firstAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, currentLocale()).format(Date(it)) }.orEmpty()
    Text(
        stringResource(R.string.card_progress_sub, content.firstScore ?: content.score, content.score, since),
        color = CardMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(0.5f))
    ScoreCurve(content.curve, Modifier.fillMaxWidth().height(if (compact) 90.dp else 170.dp))
    Spacer(Modifier.weight(0.7f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TrophiesCard(content: ShareContent, compact: Boolean) {
    Title(stringResource(R.string.card_trophies_title), compact)
    Spacer(Modifier.weight(1f))
    val tile = if (compact) 50.dp else 76.dp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = if (compact) 5 else 3,
    ) {
        Badge.entries.forEach { badge ->
            val unlocked = badge in content.unlockedBadges
            Box(
                Modifier.size(tile).clip(RoundedCornerShape(18.dp)).background(if (unlocked) CardAmber.copy(alpha = 0.18f) else CardPanel),
                contentAlignment = Alignment.Center,
            ) {
                if (unlocked) Text(badge.emoji, fontSize = if (compact) 24.sp else 36.sp) else Text("?", color = CardLine, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.card_trophies_sub, content.unlockedBadges.size, Badge.entries.size, content.points),
        color = CardText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.weight(1f))
}

// --- Briques ---------------------------------------------------------------------------------

@Composable
private fun Title(text: String, compact: Boolean) {
    Text(
        text, color = CardText, fontWeight = FontWeight.Black, fontSize = if (compact) 22.sp else 28.sp,
        textAlign = TextAlign.Center, lineHeight = if (compact) 26.sp else 33.sp,
    )
}

@Composable
private fun BigNumber(value: Long, color: Color, size: TextUnit) {
    Text(NumberFormat.getIntegerInstance(currentLocale()).format(value), color = color, fontWeight = FontWeight.Black, fontSize = size)
}

/** Anneau du score, coloré selon l'échelle, avec la note au centre. */
@Composable
internal fun ScoreRing(score: Int, size: Dp, sweep: Float = 1f, shown: Int = score) {
    val color = scoreColor(score)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(CardPanel, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * score / 100f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shown", color = CardText, fontWeight = FontWeight.Black, fontSize = (size.value * 0.34f).sp)
            Text("/100", color = CardMuted, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.09f).sp)
        }
    }
}

@Composable
private fun Stat(value: Long, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CardPanel).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(NumberFormat.getIntegerInstance(currentLocale()).format(value), color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = CardMuted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

@Composable
private fun Bar(label: String, count: Int, fraction: Float) {
    Column {
        Row {
            Text(label, color = CardText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.card_apps_count, count, count), color = CardText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(CardLine)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f)).height(8.dp).clip(CircleShape).background(CardSpy))
        }
    }
}

/** Le petit espion de l'intro, en pastille : chapeau, lunettes noires et sourire. */
@Composable
internal fun SpyBadge(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(CardSpy, cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.26f))
        val face = Color(0xFFFFD7B5)
        val ink = Color(0xFF1E1B4B)
        drawCircle(face, s * 0.24f, Offset(s * 0.5f, s * 0.58f))
        drawRect(ink, Offset(s * 0.26f, s * 0.32f), Size(s * 0.48f, s * 0.05f))
        drawRect(ink, Offset(s * 0.34f, s * 0.14f), Size(s * 0.32f, s * 0.19f))
        drawRect(CardAmber, Offset(s * 0.34f, s * 0.27f), Size(s * 0.32f, s * 0.04f))
        drawRoundRect(ink, Offset(s * 0.33f, s * 0.49f), Size(s * 0.15f, s * 0.08f), androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
        drawRoundRect(ink, Offset(s * 0.52f, s * 0.49f), Size(s * 0.15f, s * 0.08f), androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
        val smile = Path().apply {
            moveTo(s * 0.42f, s * 0.67f)
            quadraticTo(s * 0.5f, s * 0.73f, s * 0.58f, s * 0.67f)
        }
        drawPath(smile, ink, style = Stroke(s * 0.03f, cap = StrokeCap.Round))
    }
}

/** Courbe du score moyen dans le temps, avec un dégradé dessous et un point sur la dernière valeur. */
@Composable
private fun ScoreCurve(points: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = (points.min() - 5f).coerceAtLeast(0f)
        val max = (points.max() + 5f).coerceAtMost(100f)
        val span = (max - min).coerceAtLeast(1f)
        fun at(i: Int) = Offset(size.width * i / (points.size - 1), size.height * (1f - (points[i] - min) / span))
        val line = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(CardEmerald.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, CardEmerald, style = Stroke(size.height * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = at(points.size - 1)
        drawCircle(CardBackground, size.height * 0.06f, last)
        drawCircle(CardEmerald, size.height * 0.04f, last)
    }
}

/** Bas de l'image : la question qui invite à essayer, et la signature VYLIA. */
@Composable
private fun Footer(compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardPanel).padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
    ) {
        Text(stringResource(R.string.card_footer_cta), color = CardText, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 16.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VyliaLogo(Modifier.size(if (compact) 22.dp else 28.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_footer_app), color = CardMuted, fontSize = 10.sp)
                VyliaLogotype(fontSize = if (compact) 16.sp else 18.sp)
            }
            Text(stringResource(R.string.card_hashtag), color = CardCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Quantité pour choisir le pluriel d'un compteur qui peut dépasser un Int. */
internal fun Long.toQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
