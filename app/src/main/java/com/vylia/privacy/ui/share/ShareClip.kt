package com.vylia.privacy.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.vylia.privacy.R
import com.vylia.privacy.ui.splash.ScanLayout
import com.vylia.privacy.ui.splash.ScanPainter
import com.vylia.privacy.ui.splash.SplashTimeline
import com.vylia.privacy.ui.splash.easeOutCubic
import com.vylia.privacy.ui.splash.progress
import com.vylia.privacy.ui.splash.springCurve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Textes du clip, traduits par l'appelant. */
data class ClipLabels(
    val scoreLabel: String,
    val rating: String,
    val trackers: String,
    val second: String,
    val cta: String,
    val hashtag: String,
)

/**
 * Dessine une image du clip à l'instant [timeMs] dans un [Canvas] Android, sans Compose pour le texte : le même
 * code sert au GIF et aux tests. Pinceaux et textes sont préparés une fois.
 *
 * Déroulé (8 s) : 1. la scène de scan de l'intro (la loupe démasque les espions) ; 2. l'anneau du score se remplit
 * et la note défile ; 3. les compteurs (traqueurs, pistages bloqués ou trophées) arrivent l'un après l'autre ;
 * 4. carton final : logo VYLIA, « Et vous, quel est votre score ? ».
 */
class ShareClipRenderer(
    private val content: ShareContent,
    private val labels: ClipLabels,
    val width: Int,
    val height: Int,
    private val logo: Drawable?,
    logotype: Typeface?,
    locale: Locale,
) {
    private val unit = width / 360f
    private val scanLayout = ScanLayout(width.toFloat(), height.toFloat())
    private val scanPainter = ScanPainter(scanLayout)
    private val drawScope = CanvasDrawScope()
    private val composeDensity = Density(unit)
    private val numbers = NumberFormat.getIntegerInstance(locale)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = logotype ?: Typeface.DEFAULT_BOLD
        color = CardText.toArgb()
        textSize = 46f * unit
    }
    private val secondValue = if (content.shieldBlocked > 0) content.shieldBlocked else content.unlockedBadges.size.toLong()
    private val secondColor = if (content.shieldBlocked > 0) CardCyan.toArgb() else CardAmber.toArgb()
    private val scoreColor = scoreColor(content.score).toArgb()

    val durationMs: Long get() = END

    fun render(bitmap: Bitmap, timeMs: Long) = draw(Canvas(bitmap), timeMs)

    fun draw(canvas: Canvas, timeMs: Long) {
        canvas.drawColor(CardBackground.toArgb())
        when {
            timeMs < SCAN_END -> drawScope.draw(composeDensity, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
                scanPainter.draw(this, timeMs, null)
            }
            timeMs < COUNTERS_START -> drawScore(canvas, timeMs)
            timeMs < OUTRO_START -> drawCounters(canvas, timeMs)
            else -> drawOutro(canvas, timeMs)
        }
    }

    private fun drawScore(canvas: Canvas, time: Long) {
        val p = easeOutCubic(progress(time, SCORE_START, SCORE_START + SCORE_FILL))
        val appear = progress(time, SCAN_END, SCORE_START)
        val cx = width / 2f
        val cy = height * 0.45f
        val radius = 110f * unit * (0.7f + 0.3f * springCurve(appear))
        text.alpha(appear)
        text.color(CardMuted.toArgb(), appear)
        text.textSize = 20f * unit
        canvas.drawText(labels.scoreLabel, cx, cy - radius - 40f * unit, text)
        stroke.strokeWidth = radius * 0.18f
        stroke.color = CardPanel.toArgb()
        stroke.alpha = (255 * appear).toInt()
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 0f, 360f, false, stroke)
        stroke.color = scoreColor
        stroke.alpha = (255 * appear).toInt()
        canvas.drawArc(rect, -90f, 360f * content.score / 100f * p, false, stroke)
        text.color(CardText.toArgb(), appear)
        text.textSize = radius * 0.7f
        canvas.drawText("${(content.score * p).toInt()}", cx, cy + radius * 0.22f, text)
        text.textSize = radius * 0.18f
        text.color(CardMuted.toArgb(), appear)
        canvas.drawText("/100", cx, cy + radius * 0.5f, text)
        val rating = progress(time, SCORE_START + SCORE_FILL, SCORE_START + SCORE_FILL + 250)
        text.color(scoreColor, rating)
        text.textSize = 26f * unit
        canvas.drawText(labels.rating, cx, cy + radius + 56f * unit, text)
    }

    private fun drawCounters(canvas: Canvas, time: Long) {
        drawCounter(canvas, time, COUNTERS_START, height * 0.33f, content.trackers.toLong(), labels.trackers, CardSpy.toArgb())
        drawCounter(canvas, time, COUNTERS_START + COUNTER_GAP, height * 0.62f, secondValue, labels.second, secondColor)
    }

    /** Une pastille qui glisse depuis le bas, et le nombre qui défile jusqu'à sa valeur. */
    private fun drawCounter(canvas: Canvas, time: Long, start: Long, y: Float, value: Long, label: String, color: Int) {
        val slide = springCurve(progress(time, start, start + 500))
        if (slide <= 0f) return
        val count = easeOutCubic(progress(time, start + 150, start + 1_100))
        val offset = (1f - slide) * 120f * unit
        val cx = width / 2f
        fill.color = color
        fill.alpha = (60 * slide.coerceAtMost(1f)).toInt()
        rect.set(36f * unit, y - 95f * unit + offset, width - 36f * unit, y + 75f * unit + offset)
        canvas.drawRoundRect(rect, 28f * unit, 28f * unit, fill)
        text.color(color, slide.coerceAtMost(1f))
        text.textSize = 76f * unit
        canvas.drawText(numbers.format((value * count).toLong()), cx, y + offset, text)
        text.color(CardText.toArgb(), slide.coerceAtMost(1f))
        text.textSize = 22f * unit
        canvas.drawText(label, cx, y + 42f * unit + offset, text)
    }

    private fun drawOutro(canvas: Canvas, time: Long) {
        val p = progress(time, OUTRO_START, OUTRO_START + 400)
        val slam = 1.4f - 0.4f * springCurve(p)
        val cx = width / 2f
        val cy = height * 0.38f
        logo?.let {
            // L'icône de l'app : sa zone sûre (66 sur 108) remplit le carré du logo.
            val side = 120f * unit * slam / (66f / 108f)
            it.setBounds((cx - side / 2).toInt(), (cy - side / 2).toInt(), (cx + side / 2).toInt(), (cy + side / 2).toInt())
            it.alpha = (255 * p.coerceAtMost(1f)).toInt()
            it.draw(canvas)
        }
        brand.alpha = (255 * p.coerceAtMost(1f)).toInt()
        canvas.drawText("VYLIA", cx, cy + 120f * unit, brand)
        val cta = progress(time, OUTRO_START + 300, OUTRO_START + 700)
        text.color(CardText.toArgb(), cta)
        text.textSize = 24f * unit
        canvas.drawText(labels.cta, cx, cy + 190f * unit, text)
        text.color(CardCyan.toArgb(), cta)
        text.textSize = 18f * unit
        canvas.drawText(labels.hashtag, cx, cy + 228f * unit, text)
    }

    private fun Paint.color(argb: Int, alpha: Float) {
        color = argb
        this.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
    }

    private fun Paint.alpha(value: Float) {
        alpha = (255 * value.coerceIn(0f, 1f)).toInt()
    }

    companion object {
        /** La scène de scan de l'intro, jusqu'à l'éjection des espions. */
        val SCAN_END = SplashTimeline.EJECT_END
        val SCORE_START = SCAN_END + 300
        const val SCORE_FILL = 1_200L
        val COUNTERS_START = SCORE_START + SCORE_FILL + 900
        const val COUNTER_GAP = 600L
        val OUTRO_START = COUNTERS_START + COUNTER_GAP + 1_500
        val END = OUTRO_START + 1_600
    }
}

/** Le GIF : 540 × 960, 12,5 images par seconde, encodé sur le téléphone dans `cache/share`. */
object ShareClip {
    private const val WIDTH = 540
    private const val HEIGHT = 960
    private const val FRAME_MS = 80L

    fun renderer(context: Context, content: ShareContent, labels: ClipLabels, locale: Locale) = ShareClipRenderer(
        content, labels, WIDTH, HEIGHT,
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground),
        runCatching { ResourcesCompat.getFont(context, R.font.unbounded) }.getOrNull(),
        locale,
    )

    /** Encode le clip et renvoie le fichier ; [onProgress] reçoit 0 à 100. Annulable entre deux images. */
    suspend fun encode(context: Context, renderer: ShareClipRenderer, onProgress: (Int) -> Unit): File = withContext(Dispatchers.Default) {
        val file = ShareFiles.newFile(context, "gif")
        val bitmap = Bitmap.createBitmap(renderer.width, renderer.height, Bitmap.Config.ARGB_8888)
        val frames = (renderer.durationMs / FRAME_MS).toInt()
        try {
            file.outputStream().buffered().use { out ->
                val gif = GifEncoder(out, renderer.width, renderer.height, (FRAME_MS / 10).toInt())
                for (i in 0 until frames) {
                    coroutineContext.ensureActive()
                    renderer.render(bitmap, i * FRAME_MS)
                    gif.addFrame(bitmap)
                    onProgress((i + 1) * 100 / frames)
                }
                gif.finish()
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        file
    }
}
