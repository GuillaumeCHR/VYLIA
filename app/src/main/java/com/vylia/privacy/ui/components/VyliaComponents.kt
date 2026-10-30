package com.vylia.privacy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.ui.home.components.labelRes
import com.vylia.privacy.ui.theme.Slate800
import com.vylia.privacy.ui.theme.WhiteTranslucent
import com.vylia.privacy.ui.theme.color

/** Durée commune des animations de score (pastilles, jauge, points). */
const val ScoreAnimationMillis = 700

@Composable
fun VyliaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Slate800,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/**
 * Pastille colorée (vert / orange / rouge) affichant le score d'une application.
 * La valeur et la couleur s'animent quand le score est recalculé (ex. après l'analyse des traqueurs).
 */
@Composable
fun ScoreBadge(score: PrivacyScore, modifier: Modifier = Modifier) {
    val riskColor = score.rating.color
    val animatedValue by animateIntAsState(score.value, tween(ScoreAnimationMillis), label = "scoreValue")
    val container by animateColorAsState(riskColor.container, tween(ScoreAnimationMillis), label = "scoreContainer")
    val content by animateColorAsState(riskColor.onContainer, tween(ScoreAnimationMillis), label = "scoreContent")
    val description = "${score.value}/100, ${stringResource(score.rating.labelRes)}"
    Surface(
        modifier = modifier
            .size(52.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = animatedValue.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun TransparencyBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color = WhiteTranslucent, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
