package com.vylia.privacy.ui.splash

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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.vylia.privacy.ui.components.drawYogiStudioMark
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private val Background = Color(0xFF0F172A)
private val TileIdle = Color(0xFF1E293B)
private val GlyphIdle = Color(0xFF475569)
private val Cyan = Color(0xFF38BDF8)
/** Petits espions bienveillants : tuile violette, chapeau, lunettes noires et sourire (ni rouge, ni menace). */
private val SpyTile = Color(0xFF7C3AED)
private val SpyInk = Color(0xFF1E1B4B)
private val SpyFace = Color(0xFFFFD7B5)
private val SpyBand = Color(0xFFF59E0B)

/** Applis saines : tons du haut de l'échelle du score (vert émeraude à orange), comme de vraies icônes. */
private val CleanColors = arrayOf(
    Color(0xFF10B981), Color(0xFF84CC16), Color(0xFFF59E0B), Color(0xFF10B981),
    Color(0xFFF97316), Color(0xFF84CC16), Color(0xFF10B981), Color(0xFFF59E0B),
)

/**
 * Géométrie de l'intro, calculée une fois pour une taille d'écran : grille de 12 applis (3 × 4), trajet de la
 * loupe, instant où elle révèle chaque appli, et trajectoires d'éjection des applis intrusives (déterministes).
 */
internal class ScanLayout(val width: Float, val height: Float) {
    val unit = minOf(width, height) / 360f
    val iconSize = 64f * unit
    private val gap = 30f * unit
    val centerY = height * 0.44f

    val iconX = FloatArray(COUNT)
    val iconY = FloatArray(COUNT)
    val row = IntArray(COUNT)
    val revealTime = LongArray(COUNT)
    val intrusive = BooleanArray(COUNT) { it in INTRUSIVE }
    val ejectVx = FloatArray(COUNT)
    val ejectVy = FloatArray(COUNT)
    val ejectSpin = FloatArray(COUNT)

    private val rowY = FloatArray(ROWS)
    private val scanLeft: Float
    private val scanRight: Float

    /** Sourire d'un espion, centré sur l'origine de sa tuile (construit une fois). */
    val smile = Path().apply {
        val w = iconSize * 0.08f
        val y = iconSize * 0.16f
        moveTo(-w, y)
        quadraticTo(0f, y + iconSize * 0.07f, w, y)
    }

    /** Triangle « lecture » d'une icône, centré sur l'origine (construit une fois). */
    val playGlyph = Path().apply {
        val s = iconSize * 0.2f
        moveTo(-s * 0.7f, -s)
        lineTo(s, 0f)
        lineTo(-s * 0.7f, s)
        close()
    }

    init {
        val gridWidth = COLS * iconSize + (COLS - 1) * gap
        val gridHeight = ROWS * iconSize + (ROWS - 1) * gap
        val left = (width - gridWidth) / 2 + iconSize / 2
        val top = centerY - gridHeight / 2 + iconSize / 2
        for (r in 0 until ROWS) rowY[r] = top + r * (iconSize + gap)
        scanLeft = left - iconSize
        scanRight = left + (COLS - 1) * (iconSize + gap) + iconSize
        val scanDuration = SplashTimeline.SCAN_END - SplashTimeline.SETUP_END
        for (i in 0 until COUNT) {
            val c = i % COLS
            val r = i / COLS
            row[i] = r
            iconX[i] = left + c * (iconSize + gap)
            iconY[i] = rowY[r]
            // La loupe parcourt chaque rangée en serpentin : de gauche à droite, puis de droite à gauche.
            val along = (iconX[i] - scanLeft) / (scanRight - scanLeft)
            val f = if (r % 2 == 0) along else 1f - along
            revealTime[i] = SplashTimeline.SETUP_END + ((r + f * ROW_TRAVEL) / ROWS * scanDuration).toLong()
            // Éjection : vers l'extérieur de la grille, avec une pointe d'aléatoire fixe.
            val dirX = if (c == 1) (if (r % 2 == 0) 1f else -1f) else (c - 1).toFloat()
            // Sortie en douceur : l'espion s'éclipse vers le haut en flottant, sans tourbillon.
            ejectVx[i] = dirX * width * (0.18f + 0.04f * ((i * 7) % 3))
            ejectVy[i] = -height * (0.45f + 0.05f * r)
            ejectSpin[i] = if (i % 2 == 0) 14f else -14f
        }
    }

    /** Position de la loupe à l'instant [time] (écrite dans [out] : x, y), sur son trajet en serpentin. */
    fun loupe(time: Long, out: FloatArray) {
        val p = progress(time, SplashTimeline.SETUP_END, SplashTimeline.SCAN_END) * ROWS
        val r = p.toInt().coerceAtMost(ROWS - 1)
        val f = (p - r).coerceIn(0f, 1f)
        // Chaque rangée : un aller sur ROW_TRAVEL du temps, puis la descente vers la rangée suivante.
        val travel = (f / ROW_TRAVEL).coerceAtMost(1f)
        val eased = easeInOutCubic(travel)
        out[0] = if (r % 2 == 0) scanLeft + (scanRight - scanLeft) * eased else scanRight - (scanRight - scanLeft) * eased
        val down = if (r < ROWS - 1) easeInOutCubic(((f - ROW_TRAVEL) / (1f - ROW_TRAVEL)).coerceIn(0f, 1f)) else 0f
        out[1] = rowY[r] + (if (r < ROWS - 1) rowY[r + 1] - rowY[r] else 0f) * down
    }

    companion object {
        const val COLS = 3
        const val ROWS = 4
        const val COUNT = COLS * ROWS
        private val INTRUSIVE = setOf(1, 5, 6, 10)
        private const val ROW_TRAVEL = 0.8f
    }
}

/**
 * Dessine l'intro « scan » à un instant donné, en aplats (ni ombre, ni dégradé) : grille d'applis, loupe qui la
 * traverse, applis saines révélées en vert à orange, applis intrusives démasquées en petits espions bienveillants qui
 * s'éclipsent,
 * et signature Yogi Studio. Aucune allocation par image.
 */
internal class ScanPainter(private val layout: ScanLayout) {
    private val loupe = FloatArray(2)

    fun draw(scope: DrawScope, time: Long, presents: TextLayoutResult) = with(scope) {
        drawRect(Background)
        if (time < SplashTimeline.EJECT_END + ICON_FADE) drawIcons(time)
        if (time in SplashTimeline.SETUP_END..SplashTimeline.SCAN_END + LOUPE_FADE) drawLoupe(time)
        if (time >= SplashTimeline.EJECT_END) drawSignature(time, presents)
    }

    private fun DrawScope.drawIcons(time: Long) {
        val fade = 1f - progress(time, SplashTimeline.EJECT_END, SplashTimeline.EJECT_END + ICON_FADE)
        for (i in 0 until ScanLayout.COUNT) {
            val delay = layout.row[i] * ROW_DELAY
            val pop = springCurve(progress(time, delay, delay + POP_MS))
            if (pop <= 0f) continue
            val revealed = time >= layout.revealTime[i]
            if (layout.intrusive[i] && time >= SplashTimeline.EJECT) {
                drawEjected(i, time)
            } else if (layout.intrusive[i] && revealed) {
                drawSpy(i, time, layout.iconX[i], layout.iconY[i], 0f, pop, fade)
            } else if (revealed) {
                // Appli saine : sa couleur apparaît avec un petit rebond, puis un anneau « validé » à l'expulsion.
                val bounce = 1f + 0.12f * sin(PI.toFloat() * progress(time, layout.revealTime[i], layout.revealTime[i] + REVEAL_MS))
                drawAppIcon(i, layout.iconX[i], layout.iconY[i], pop * bounce, CleanColors[i % CleanColors.size], Color.White, fade)
                drawApprovedRing(i, time, fade)
            } else {
                drawAppIcon(i, layout.iconX[i], layout.iconY[i], pop, TileIdle, GlyphIdle, fade)
            }
        }
    }

    /** Icône d'appli classique : tuile arrondie et pictogramme simple (objectif, liste, lecture, repère…). */
    private fun DrawScope.drawAppIcon(i: Int, x: Float, y: Float, scale: Float, tile: Color, glyph: Color, alpha: Float) {
        val s = layout.iconSize
        translate(x, y) {
            scale(scale, pivot = Offset.Zero) {
                drawRoundRect(tile, Offset(-s / 2, -s / 2), Size(s, s), CornerRadius(s * 0.26f), alpha = alpha)
                val g = s * 0.2f
                val stroke = s * 0.07f
                when (i % 6) {
                    0 -> {
                        drawCircle(glyph, g, Offset.Zero, alpha = alpha, style = Stroke(stroke))
                        drawCircle(glyph, g * 0.35f, Offset.Zero, alpha = alpha)
                    }
                    1 -> for (k in -1..1) {
                        drawLine(glyph, Offset(-g, k * g * 0.8f), Offset(g, k * g * 0.8f), stroke, StrokeCap.Round, alpha = alpha)
                    }
                    2 -> drawPath(layout.playGlyph, glyph, alpha = alpha)
                    3 -> {
                        drawCircle(glyph, g * 0.8f, Offset(0f, -g * 0.3f), alpha = alpha)
                        drawLine(glyph, Offset(0f, g * 0.2f), Offset(0f, g * 1.1f), stroke * 1.4f, StrokeCap.Round, alpha = alpha)
                    }
                    4 -> {
                        drawRect(glyph, Offset(-g, -g * 0.7f), Size(2 * g, 1.4f * g), alpha = alpha, style = Stroke(stroke))
                        drawLine(glyph, Offset(-g, -g * 0.7f), Offset(0f, g * 0.1f), stroke, StrokeCap.Round, alpha = alpha)
                        drawLine(glyph, Offset(g, -g * 0.7f), Offset(0f, g * 0.1f), stroke, StrokeCap.Round, alpha = alpha)
                    }
                    else -> for (k in 0 until 4) {
                        val cx = if (k % 2 == 0) -g * 0.55f else g * 0.55f
                        val cy = if (k < 2) -g * 0.55f else g * 0.55f
                        drawRoundRect(glyph, Offset(cx - g * 0.4f, cy - g * 0.4f), Size(g * 0.8f, g * 0.8f), CornerRadius(g * 0.15f), alpha = alpha)
                    }
                }
            }
        }
    }

    /**
     * Appli intrusive démasquée : un petit espion bienveillant (chapeau, lunettes noires, sourire). Démasqué, il
     * sursaute avec un rebond, puis se balance doucement jusqu'à ce qu'il s'éclipse.
     */
    private fun DrawScope.drawSpy(i: Int, time: Long, x: Float, y: Float, angle: Float, scale: Float, alpha: Float) {
        val s = layout.iconSize
        val since = time - layout.revealTime[i]
        val bounce = if (since in 0 until REVEAL_POP_MS) 1f + 0.18f * sin(PI.toFloat() * since / REVEAL_POP_MS) else 1f
        val sway = 4f * sin(time / 180f + i)
        translate(x, y) {
            rotate(angle + sway, pivot = Offset.Zero) {
                scale(scale * bounce, pivot = Offset.Zero) {
                    drawRoundRect(SpyTile, Offset(-s / 2, -s / 2), Size(s, s), CornerRadius(s * 0.26f), alpha = alpha)
                    drawCircle(SpyFace, radius = s * 0.24f, center = Offset(0f, s * 0.08f), alpha = alpha)
                    // Chapeau : bord, calotte et ruban safran.
                    drawRoundRect(SpyInk, Offset(-s * 0.2f, -s * 0.34f), Size(s * 0.4f, s * 0.22f), CornerRadius(s * 0.06f), alpha = alpha)
                    drawRect(SpyBand, Offset(-s * 0.2f, -s * 0.18f), Size(s * 0.4f, s * 0.04f), alpha = alpha)
                    drawRoundRect(SpyInk, Offset(-s * 0.34f, -s * 0.14f), Size(s * 0.68f, s * 0.07f), CornerRadius(s * 0.035f), alpha = alpha)
                    // Lunettes noires et sourire.
                    listOf(-1f, 1f).forEach { side ->
                        drawRoundRect(
                            SpyInk,
                            Offset(side * s * 0.1f - s * 0.08f, s * 0.0f),
                            Size(s * 0.16f, s * 0.09f),
                            CornerRadius(s * 0.04f),
                            alpha = alpha,
                        )
                    }
                    drawLine(SpyInk, Offset(-s * 0.03f, s * 0.03f), Offset(s * 0.03f, s * 0.03f), s * 0.025f, alpha = alpha)
                    drawPath(layout.smile, SpyInk, alpha = alpha, style = Stroke(s * 0.035f, cap = StrokeCap.Round))
                }
            }
        }
    }

    /** L'espion s'éclipse : il s'envole doucement vers le haut en s'estompant, en soulevant un peu son chapeau. */
    private fun DrawScope.drawEjected(i: Int, time: Long) {
        val p = progress(time, SplashTimeline.EJECT, SplashTimeline.EJECT_END)
        val e = easeInOutCubic(p)
        val x = layout.iconX[i] + layout.ejectVx[i] * e
        val y = layout.iconY[i] + layout.ejectVy[i] * e
        drawSpy(i, Long.MAX_VALUE / 2, x, y, layout.ejectSpin[i] * p, 1f - 0.2f * p, 1f - p)
    }

    /** Anneau vert qui s'élargit autour des applis saines au moment de l'expulsion. */
    private fun DrawScope.drawApprovedRing(i: Int, time: Long, fade: Float) {
        val p = progress(time, SplashTimeline.EJECT, SplashTimeline.EJECT + RING_MS)
        if (p <= 0f || p >= 1f) return
        drawCircle(
            CleanColors[i % CleanColors.size],
            radius = layout.iconSize * (0.6f + 0.35f * easeOutCubic(p)),
            center = Offset(layout.iconX[i], layout.iconY[i]),
            alpha = (1f - p) * fade,
            style = Stroke(3f * layout.unit),
        )
    }

    /** La loupe cyan et son faisceau de scan sur la rangée en cours. */
    private fun DrawScope.drawLoupe(time: Long) {
        layout.loupe(time, loupe)
        val alpha = progress(time, SplashTimeline.SETUP_END, SplashTimeline.SETUP_END + LOUPE_FADE) *
            (1f - progress(time, SplashTimeline.SCAN_END, SplashTimeline.SCAN_END + LOUPE_FADE))
        if (alpha <= 0f) return
        val r = layout.iconSize * 0.62f
        val x = loupe[0]
        val y = loupe[1]
        drawLine(Cyan, Offset(0f, y), Offset(size.width, y), 2f * layout.unit, alpha = 0.25f * alpha)
        drawCircle(Cyan, r, Offset(x, y), alpha = 0.12f * alpha)
        drawCircle(Cyan, r, Offset(x, y), alpha = alpha, style = Stroke(6f * layout.unit))
        val handle = r * 0.72f
        drawLine(
            Cyan,
            Offset(x + handle, y + handle),
            Offset(x + handle * 1.9f, y + handle * 1.9f),
            9f * layout.unit,
            StrokeCap.Round,
            alpha = alpha,
        )
    }

    /** La signature : le cercle safran se trace, le personnage apparaît avec un rebond, puis « Yogi Studio présente ». */
    private fun DrawScope.drawSignature(time: Long, presents: TextLayoutResult) {
        val start = SplashTimeline.EJECT_END
        val fadeOut = 1f - progress(time, SplashTimeline.SIGNATURE_END, SplashTimeline.SIGNATURE_END + SIGNATURE_FADE)
        if (fadeOut <= 0f) return
        val side = minOf(size.width, size.height) * 0.42f
        val topLeft = Offset((size.width - side) / 2, size.height * 0.42f - side / 2)
        drawYogiStudioMark(
            ringProgress = easeOutCubic(progress(time, start, start + RING_DRAW_MS)),
            characterScale = springCurve(progress(time, start + CHARACTER_DELAY, start + CHARACTER_DELAY + CHARACTER_MS)),
            alpha = fadeOut,
            side = side,
            topLeft = topLeft,
        )
        val textAlpha = progress(time, start + PRESENTS_DELAY, start + PRESENTS_DELAY + PRESENTS_MS) * fadeOut
        if (textAlpha > 0f) {
            drawText(
                presents,
                topLeft = Offset((size.width - presents.size.width) / 2, topLeft.y + side + 16f * layout.unit),
                alpha = textAlpha,
            )
        }
    }

    private companion object {
        const val ROW_DELAY = 80L
        const val POP_MS = 420L
        const val REVEAL_MS = 260L
        const val REVEAL_POP_MS = 320L
        const val LOUPE_FADE = 150L
        const val ICON_FADE = 200L
        const val RING_MS = 450L
        const val RING_DRAW_MS = 450L
        const val CHARACTER_DELAY = 300L
        const val CHARACTER_MS = 450L
        const val PRESENTS_DELAY = 450L
        const val PRESENTS_MS = 300L
        const val SIGNATURE_FADE = 200L
    }
}
