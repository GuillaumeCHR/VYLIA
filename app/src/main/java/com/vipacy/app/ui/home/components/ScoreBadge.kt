package com.vipacy.app.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vipacy.app.domain.model.PrivacyScore
import com.vipacy.app.ui.theme.color

/** Pastille colorée (vert / orange / rouge) affichant le score d'une application. */
@Composable
fun ScoreBadge(score: PrivacyScore, modifier: Modifier = Modifier) {
    val riskColor = score.rating.color
    val description = "${score.value}/100, ${stringResource(score.rating.labelRes)}"
    Surface(
        modifier = modifier
            .size(52.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = riskColor.container,
        contentColor = riskColor.onContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = score.value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
