package com.vylia.privacy.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vylia.privacy.R
import com.vylia.privacy.ui.theme.CyanAccent
import com.vylia.privacy.ui.theme.Emerald
import com.vylia.privacy.ui.theme.Slate300
import com.vylia.privacy.ui.theme.Slate900
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Écran de démarrage : les deux maillons de la chaîne, d'abord emboîtés, se brisent,
 * puis le nom et la baseline apparaissent. Fond #0F172A identique à la fenêtre et au splash système.
 */
@Composable
fun VyliaSplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val logoScale = remember { Animatable(0.8f) }
    val breakProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        delay(350)
        breakProgress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        textAlpha.animateTo(1f, tween(400))
        delay(450)
        currentOnFinished()
    }

    Box(modifier = modifier.fillMaxSize().background(Slate900), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VyliaLogo(
                breakProgress = { breakProgress.value },
                modifier = Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                color = Slate300,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = CyanAccent,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
        }
    }
}

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
