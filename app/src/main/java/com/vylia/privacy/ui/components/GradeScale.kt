package com.vylia.privacy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vylia.privacy.domain.learn.GradeBand

/** Dégradé du score, de 100 (vert émeraude) à 0 (rouge cramoisi), à intervalles réguliers. */
val ScoreGradient = listOf(
    Color(0xFF10B981), // émeraude
    Color(0xFF84CC16), // vert-jaune
    Color(0xFFF59E0B), // ambre
    Color(0xFFF97316), // orange
    Color(0xFFEF4444), // rouge
    Color(0xFFB91C3C), // cramoisi
)

/** Couleur du dégradé pour un score de 0 à [max]. */
fun scoreGradientColor(score: Int, max: Int = 100): Color {
    val position = ((max - score.coerceIn(0, max)).toFloat() / max) * (ScoreGradient.size - 1)
    val index = position.toInt().coerceAtMost(ScoreGradient.size - 2)
    return lerp(ScoreGradient[index], ScoreGradient[index + 1], position - index)
}

/**
 * Échelle des notes : barre dégradée vert → cramoisi, découpée selon les seuils des règles,
 * avec un curseur facultatif sur le score [marker].
 */
@Composable
fun GradeScale(bands: List<GradeBand>, base: Int, modifier: Modifier = Modifier, marker: Int? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Lettres, chacune centrée sur sa tranche (largeur proportionnelle à l'étendue de la tranche).
        Row(modifier = Modifier.fillMaxWidth()) {
            bands.forEach { band ->
                Text(
                    text = band.grade,
                    modifier = Modifier.weight((band.max - band.min + 1).toFloat()),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = scoreGradientColor((band.min + band.max) / 2, base),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(if (marker != null) 26.dp else 14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Brush.horizontalGradient(ScoreGradient), RoundedCornerShape(7.dp))
            )
            // Séparations entre tranches.
            Row(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                bands.forEachIndexed { index, band ->
                    Box(modifier = Modifier.weight((band.max - band.min + 1).toFloat()).height(14.dp)) {
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(width = 2.dp, height = 14.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }
            if (marker != null) {
                // Chaque point de score occupe 1/(base+1) de la largeur, comme les tranches pondérées.
                val x = maxWidth * ((base - marker.coerceIn(0, base) + 0.5f) / (base + 1))
                val markerColor = MaterialTheme.colorScheme.onSurface
                Canvas(modifier = Modifier.offset(x = x - 6.dp, y = 14.dp).size(12.dp)) {
                    val triangle = Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(triangle, markerColor)
                }
            }
        }
        // Bornes de chaque tranche.
        Row(modifier = Modifier.fillMaxWidth()) {
            bands.forEach { band ->
                Text(
                    text = if (band.min == band.max) "${band.min}" else "${band.max}–${band.min}",
                    modifier = Modifier.weight((band.max - band.min + 1).toFloat()),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
