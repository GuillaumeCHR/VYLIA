package com.vylia.privacy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Logo de l'éditeur Yogi Studio, déclinaison « tech » pour VYLIA du logo de Molkky Board (même composition,
// repère 512 × 512) : la quille en bois devient un smartphone en méditation, aux couleurs de VYLIA (ardoise,
// cyan, émeraude). Dessiné en code pour l'animer dans l'intro : le cercle cyan se trace, puis le smartphone apparaît
// avec un rebond. Nom de marque : non traduit.

private const val VIEWBOX = 512f
private const val RING_START = -62f
private const val RING_SWEEP = 314.2f

private val Tile = Color(0xFF1E293B)
private val Ring = Color(0xFF38BDF8)
private val Chrome = Color(0xFFCBD5E1)
private val ChromeShade = Color(0xFF94A3B8)
private val Joint = Color(0xFF64748B)
private val Visor = Color(0xFF0F172A)
private val Glow = Color(0xFF10B981)

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

private object PhonePaths {
    val sparks =
        listOf(
            path("M372,104 L392,96 L398,114 L380,120 Z") to 1f,
            path("M404,146 L418,142 L420,156 Z") to 0.85f,
            path("M350,74 L362,70 L364,82 L354,84 Z") to 0.7f,
        )
    val arms = listOf(path("M212,252 Q170,292 188,334"), path("M300,252 Q342,292 324,334"))

    /** Yeux fermés et sourire, sur l'écran (même tracé que le personnage d'origine). */
    val face =
        listOf(path("M228,226 Q238,235 248,226"), path("M264,226 Q274,235 284,226"), path("M243,252 Q256,265 269,252"))
}

/**
 * Dessine le logo Yogi Studio (déclinaison VYLIA) dans la zone courante.
 *
 * @param ringProgress tracé du cercle cyan, de 0 à 1.
 * @param characterScale échelle du smartphone (0 = absent, 1 = taille normale, > 1 pendant le rebond).
 * @param side côté du logo en pixels ; [topLeft] son coin haut gauche (par défaut, centré dans la zone).
 */
fun DrawScope.drawYogiStudioMark(
    ringProgress: Float = 1f,
    characterScale: Float = 1f,
    alpha: Float = 1f,
    side: Float = minOf(size.width, size.height),
    topLeft: Offset = Offset((size.width - side) / 2, (size.height - side) / 2),
) {
    val unit = side / VIEWBOX
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        drawRoundRect(Tile.copy(alpha = alpha), size = Size(VIEWBOX, VIEWBOX), cornerRadius = CornerRadius(112f))
        if (ringProgress > 0f) {
            drawArc(
                color = Ring.copy(alpha = alpha),
                startAngle = RING_START,
                sweepAngle = RING_SWEEP * ringProgress.coerceAtMost(1f),
                useCenter = false,
                topLeft = Offset(256f - 186f, 262f - 186f),
                size = Size(372f, 372f),
                style = Stroke(width = 20f, cap = StrokeCap.Round),
            )
        }
        if (characterScale > 0f) {
            scale(characterScale, pivot = Offset(256f, 300f)) { drawPhone(alpha) }
        }
    }
}

private fun DrawScope.drawPhone(alpha: Float) {
    PhonePaths.sparks.forEach { (p, a) -> drawPath(p, Glow.copy(alpha = a * alpha)) }
    drawOval(Color.Black.copy(alpha = 0.28f * alpha), topLeft = Offset(138f, 376f), size = Size(236f, 24f))
    PhonePaths.arms.forEach { drawPath(it, Joint.copy(alpha = alpha), style = Stroke(22f, cap = StrokeCap.Round)) }
    // Le smartphone : coque, écran sombre, écouteur et caméra, visage serein en cyan.
    drawRoundRect(Chrome.copy(alpha = alpha), Offset(204f, 140f), Size(104f, 206f), CornerRadius(24f))
    drawRoundRect(Visor.copy(alpha = alpha), Offset(214f, 164f), Size(84f, 152f), CornerRadius(12f))
    drawRoundRect(ChromeShade.copy(alpha = alpha), Offset(240f, 149f), Size(28f, 6f), CornerRadius(3f))
    drawCircle(ChromeShade.copy(alpha = alpha), radius = 4f, center = Offset(280f, 152f))
    PhonePaths.face.forEach { drawPath(it, Ring.copy(alpha = alpha), style = Stroke(6f, cap = StrokeCap.Round)) }
    listOf(226f, 286f).forEach { drawCircle(Glow.copy(alpha = 0.45f * alpha), radius = 9f, center = Offset(it, 246f)) }
    // Jambes croisées : deux pilules inclinées, puis les mains posées, en cyan.
    listOf(-7f to ChromeShade, 7f to Joint).forEach { (angle, color) ->
        rotate(angle, pivot = Offset(256f, 348f)) {
            drawRoundRect(color.copy(alpha = alpha), topLeft = Offset(160f, 326f), size = Size(192f, 44f), cornerRadius = CornerRadius(22f))
        }
    }
    listOf(188f, 324f).forEach { drawCircle(Ring.copy(alpha = alpha), radius = 14f, center = Offset(it, 336f)) }
}

/** Logo carré Yogi Studio (statique). */
@Composable
fun YogiStudioMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Canvas(modifier.size(size).semantics { contentDescription = "Yogi Studio" }) { drawYogiStudioMark() }
}

/** Logo horizontal : le logo carré, « Yogi » en ardoise claire et « STUDIO » en cyan espacé. */
@Composable
fun YogiStudioLogo(modifier: Modifier = Modifier, markSize: Dp = 64.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        YogiStudioMark(size = markSize)
        Column {
            Text(
                "Yogi",
                fontWeight = FontWeight.Bold,
                fontSize = (markSize.value * 0.55f).sp,
                color = Chrome,
                letterSpacing = (-0.03).em,
            )
            Text(
                "STUDIO",
                fontWeight = FontWeight.Bold,
                fontSize = (markSize.value * 0.19f).sp,
                color = Ring,
                letterSpacing = 0.5.em,
            )
        }
    }
}
