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
private val BugRed = Color(0xFFEF4444)
private val BugDark = Color(0xFF7F1D1D)
private val Glitch = Color(0xFF22D3EE)

/** Applis saines : tons du haut de l'échelle du score (vert émeraude à orange), comme de vraies icônes. */
private val CleanColors = arrayOf(
    Color(0xFF10B981), Color(0xFF84CC16), Color(0xFFF59E0B), Color(0xFF10B981),
    Color(0xFFF97316), Color(0xFF84CC16), Color(0xFF10B981), Color(0xFFF59E0B),
)

/** Bug « digitalisé » en pixel art (9 × 8) : antennes, yeux, pattes. */
private val BugPixels = arrayOf(
    "..X...X..",
    "...X.X...",
    "..XXXXX..",
    ".XX.X.XX.",
    "XXXXXXXXX",
    "X.XXXXX.X",
    "X.X...X.X",
    "...X.X...",
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
            ejectVx[i] = dirX * width * (1.5f + 0.25f * ((i * 7) % 3))
            ejectVy[i] = (r - 1.5f) * height * 0.35f - height * 0.25f
            ejectSpin[i] = if (i % 2 == 0) 540f else -420f
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
 * traverse, applis saines révélées en vert à orange, applis intrusives qui « buguent » en rouge puis sont expulsées,
 * et signature Yogi Studio. Aucune allocation par image.
 */
internal class ScanPainter(private val layout: ScanLayout) {
    private val loupe = FloatArray(2)

    fun draw(scope: DrawScope, time: Long, presents: TextLayoutResult) = with(scope) {
        drawRect(Background)
        val shake = shakeOffset(time)
        translate(left = shake) {
            if (time < SplashTimeline.EJECT_END + ICON_FADE) drawIcons(time)
            if (time in SplashTimeline.SETUP_END..SplashTimeline.SCAN_END + LOUPE_FADE) drawLoupe(time)
        }
        if (time >= SplashTimeline.EJECT_END) drawSignature(time, presents)
    }

    /** Petit tremblement au moment de l'expulsion, amorti en 0,35 s. */
    private fun shakeOffset(time: Long): Float {
        val dt = (time - SplashTimeline.EJECT) / 1_000f
        if (dt < 0f || dt > 0.35f) return 0f
        return 10f * layout.unit * sin(2f * PI.toFloat() * 20f * dt) * exp(-dt / 0.1f)
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
                drawBug(i, time, layout.iconX[i], layout.iconY[i], 0f, pop, fade)
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
     * Appli intrusive démasquée : tuile rouge sombre et bug en pixels rouges ; juste après la révélation,
     * un « glitch » (décalages cyan / rouge) la fait trembler, puis elle frémit jusqu'à l'expulsion.
     */
    private fun DrawScope.drawBug(i: Int, time: Long, x: Float, y: Float, angle: Float, scale: Float, alpha: Float) {
        val s = layout.iconSize
        val since = time - layout.revealTime[i]
        val glitching = since < GLITCH_MS
        val jitter = if (glitching) ((since / 40) % 3 - 1) * 4f * layout.unit else sin(time / 30f + i) * layout.unit
        translate(x + jitter, y) {
            rotate(angle, pivot = Offset.Zero) {
                scale(scale, pivot = Offset.Zero) {
                    drawRoundRect(BugDark, Offset(-s / 2, -s / 2), Size(s, s), CornerRadius(s * 0.26f), alpha = alpha)
                    if (glitching) {
                        drawPixels(-3f * layout.unit, Glitch, alpha * 0.8f)
                        drawPixels(3f * layout.unit, BugRed, alpha * 0.6f)
                    }
                    drawPixels(0f, BugRed, alpha)
                }
            }
        }
    }

    private fun DrawScope.drawPixels(shiftX: Float, color: Color, alpha: Float) {
        val px = layout.iconSize * 0.075f
        val left = -px * 4.5f + shiftX
        val top = -px * 4f
        for (r in BugPixels.indices) {
            val line = BugPixels[r]
            for (c in line.indices) {
                if (line[c] == 'X') drawRect(color, Offset(left + c * px, top + r * px), Size(px * 0.9f, px * 0.9f), alpha = alpha)
            }
        }
    }

    /** Expulsion : le bug part hors champ en tournoyant, avec une traînée de fantômes. */
    private fun DrawScope.drawEjected(i: Int, time: Long) {
        for (ghost in TRAIL downTo 0) {
            val t = time - ghost * TRAIL_STEP_MS
            if (t < SplashTimeline.EJECT) continue
            val p = progress(t, SplashTimeline.EJECT, SplashTimeline.EJECT_END)
            val e = p * p
            val x = layout.iconX[i] + layout.ejectVx[i] * e
            val y = layout.iconY[i] + layout.ejectVy[i] * e
            val alpha = if (ghost == 0) 1f else TRAIL_ALPHA / ghost
            drawBug(i, Long.MAX_VALUE / 2, x, y, layout.ejectSpin[i] * p, 1f + 0.3f * p, alpha)
        }
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
        const val GLITCH_MS = 320L
        const val LOUPE_FADE = 150L
        const val ICON_FADE = 200L
        const val RING_MS = 450L
        const val TRAIL = 3
        const val TRAIL_STEP_MS = 28L
        const val TRAIL_ALPHA = 0.35f
        const val RING_DRAW_MS = 450L
        const val CHARACTER_DELAY = 300L
        const val CHARACTER_MS = 450L
        const val PRESENTS_DELAY = 450L
        const val PRESENTS_MS = 300L
        const val SIGNATURE_FADE = 200L
    }
}
