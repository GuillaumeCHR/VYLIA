package com.vylia.privacy.ui.splash

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.HeaderLogotypeSize
import com.vylia.privacy.ui.components.VyliaLogotype
import com.vylia.privacy.ui.components.drawYogiStudioMark
import com.vylia.privacy.ui.theme.CyanAccent
import com.vylia.privacy.ui.theme.Emerald
import com.vylia.privacy.ui.theme.Slate300
import com.vylia.privacy.ui.theme.Slate900

/**
 * Intro « scan » (≈ 4,6 s), sur le schéma de celle de Molkky Board : une grille d'applis apparaît, une loupe la
 * traverse et démasque les applis intrusives (bugs rouges pixelisés) qui sont expulsées, tandis que les applis saines
 * (icônes vertes à orange) restent ; signature Yogi Studio ; puis le logo VYLIA claque (la chaîne se brise) et le
 * logotype glisse en haut à gauche, à la place exacte du titre de l'écran Scan. Un tap passe au logo. Avec
 * « Réduire les animations », simple fondu du logo.
 *
 * @param frameTimeMs fige l'intro à cet instant (aperçus et captures) au lieu de la jouer.
 */
@Composable
fun VyliaSplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier, frameTimeMs: Long? = null) {
    val reduced = rememberReducedMotion()
    val end = if (reduced) SplashTimeline.REDUCED_END else SplashTimeline.END
    val elapsed = remember { mutableLongStateOf(frameTimeMs ?: 0L) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    if (frameTimeMs == null) SplashClock(elapsed, reduced, end, currentOnFinished)
    val skipLabel = stringResource(R.string.splash_skip_hint)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
            .pointerInput(reduced) {
                detectTapGestures {
                    // Passer : directement au logo VYLIA.
                    val target = if (reduced) end else SplashTimeline.LOGO_SHOWN
                    elapsed.longValue = maxOf(elapsed.longValue, target)
                }
            }
            .semantics { contentDescription = skipLabel },
    ) {
        if (reduced) {
            ReducedSplash(elapsed)
        } else {
            ScanSceneCanvas(elapsed)
            LogoReveal(elapsed)
        }
    }
}

/** Fait avancer le temps de l'intro image par image ; vibration à l'expulsion des applis intrusives. */
@Composable
private fun SplashClock(elapsed: MutableLongState, reduced: Boolean, end: Long, onFinished: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(reduced) {
        var last = withFrameMillis { it }
        while (elapsed.longValue < end) {
            withFrameMillis { now ->
                val before = elapsed.longValue
                val next = before + (now - last)
                last = now
                if (!reduced && before < SplashTimeline.EJECT && next >= SplashTimeline.EJECT) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                elapsed.longValue = next
            }
        }
        onFinished()
    }
}

@Composable
private fun ScanSceneCanvas(elapsed: MutableLongState) {
    val measurer = rememberTextMeasurer()
    val presents = stringResource(R.string.splash_presents)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val layout = remember(width, height) { ScanLayout(width, height) }
        val painter = remember(layout) { ScanPainter(layout) }
        val presentsLayout = remember(presents, layout) {
            measurer.measure(
                presents,
                TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = with(density) { (14f * layout.unit).toSp() },
                    color = Slate300,
                ),
            )
        }
        Canvas(Modifier.fillMaxSize()) { painter.draw(this, elapsed.longValue, presentsLayout) }
    }
}

/**
 * Le logo VYLIA claque au centre (ressort) et la chaîne se brise ; puis le logo et la baseline s'effacent pendant
 * que le logotype glisse et se réduit jusqu'au titre de l'écran Scan (en haut à gauche, sous la barre d'état).
 */
@Composable
private fun LogoReveal(elapsed: MutableLongState) {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    var titleWidth by remember { mutableIntStateOf(0) }
    var titleHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val logoSize = with(density) { 112.dp.toPx() }
        val centerY = height * 0.42f
        fun appear(): Float = progress(elapsed.longValue, SplashTimeline.LOGO_IN, SplashTimeline.LOGO_SHOWN)
        fun move(): Float = easeInOutCubic(progress(elapsed.longValue, SplashTimeline.MOVE_START, SplashTimeline.END))
        // Logo et baseline : entrée en claquant, sortie en fondu au début du glissement.
        val fadeOut = { 1f - progress(elapsed.longValue, SplashTimeline.MOVE_START, SplashTimeline.MOVE_START + 150) }
        VyliaLogo(
            breakProgress = { appear() },
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    val p = appear()
                    alpha = (p * 3f).coerceAtMost(1f) * fadeOut()
                    val slam = START_SCALE + (1f - START_SCALE) * springCurve(p)
                    scaleX = slam
                    scaleY = slam
                    translationX = (width - logoSize) / 2
                    translationY = centerY - logoSize * 1.25f
                },
        )
        Text(
            text = stringResource(R.string.app_tagline),
            color = CyanAccent,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = progress(elapsed.longValue, SplashTimeline.LOGO_SHOWN - 100, SplashTimeline.LOGO_SHOWN + 150) * fadeOut()
                    translationY = centerY + with(density) { 40.dp.toPx() }
                },
        )
        VyliaLogotype(
            fontSize = SPLASH_LOGOTYPE_SIZE,
            modifier = Modifier
                .onSizeChanged { titleWidth = it.width; titleHeight = it.height }
                .graphicsLayer {
                    val p = appear()
                    val m = move()
                    alpha = (p * 3f).coerceAtMost(1f)
                    // Pivot à gauche, à mi-hauteur : le bord gauche et l'axe du texte vont droit à leur place.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    val slam = START_SCALE + (1f - START_SCALE) * springCurve(p)
                    val target = HeaderLogotypeSize.value / SPLASH_LOGOTYPE_SIZE.value
                    val scale = slam + (target - slam) * m
                    scaleX = scale
                    scaleY = scale
                    val startX = (width - titleWidth * slam) / 2
                    val startY = centerY - titleHeight / 2f
                    val endX = with(density) { 16.dp.toPx() }
                    val endY = statusBar + with(density) { 32.dp.toPx() } - titleHeight / 2f
                    translationX = startX + (endX - startX) * m
                    translationY = startY + (endY - startY) * m
                },
        )
    }
}

@Composable
private fun ReducedSplash(elapsed: MutableLongState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            val t = elapsed.longValue
            drawYogiStudioMark(alpha = progress(t, 0, 300) * (1f - progress(t, 700, SplashTimeline.REDUCED_END)))
        }
    }
}

/** « Supprimer les animations » d'Android (échelle de durée des animations à 0). */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private val SPLASH_LOGOTYPE_SIZE = 44.sp
private const val START_SCALE = 1.6f

private val sparks = listOf(
    Offset(0f, -14f) to Offset(0f, -20f),
    Offset(-4f, -13f) to Offset(-7f, -18f),
    Offset(4f, -13f) to Offset(7f, -18f),
    Offset(0f, 14f) to Offset(0f, 20f),
    Offset(-4f, 13f) to Offset(-7f, 18f),
    Offset(4f, 13f) to Offset(7f, 18f),
)

/**
 * Logo de la chaîne brisée, même géométrie que `ic_launcher_foreground.xml` (unités du viewport,
 * relatives au centre). [breakProgress] : 0 = maillons emboîtés, 1 = chaîne brisée.
 */
@Composable
fun VyliaLogo(modifier: Modifier = Modifier, breakProgress: () -> Float = { 1f }) {
    Canvas(modifier) {
        val unit = size.minDimension / 72f
        val progress = breakProgress()
        val shift = (1f - progress) * 8f
        rotate(-45f) {
            drawLink(left = -30f + shift, unit = unit, color = Emerald)
            drawLink(left = 6f - shift, unit = unit, color = CyanAccent)
            val sparkAlpha = progress.coerceIn(0f, 1f)
            if (sparkAlpha > 0f) {
                sparks.forEach { (start, end) ->
                    drawLine(
                        color = Slate300.copy(alpha = sparkAlpha),
                        start = center + start * unit,
                        end = center + end * unit,
                        strokeWidth = 2.5f * unit,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLink(left: Float, unit: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = center + Offset(left, -8f) * unit,
        size = Size(24f, 16f) * unit,
        cornerRadius = CornerRadius(8f * unit),
        style = Stroke(width = 6f * unit),
    )
}
