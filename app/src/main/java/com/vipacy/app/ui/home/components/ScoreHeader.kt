package com.vipacy.app.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vipacy.app.R
import com.vipacy.app.domain.model.AppFilter
import com.vipacy.app.domain.model.PrivacyScore
import com.vipacy.app.ui.theme.color

/** En-tête : jauge circulaire du score moyen de l'onglet sélectionné. */
@Composable
fun ScoreHeader(
    averageScore: PrivacyScore?,
    appCount: Int,
    filter: AppFilter,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScoreGauge(averageScore)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.header_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (averageScore != null) {
                    Text(
                        text = stringResource(averageScore.rating.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = averageScore.rating.color.accent,
                    )
                }
                Text(
                    text = stringResource(
                        if (filter == AppFilter.SYSTEM) R.string.header_subtitle_system else R.string.header_subtitle_user,
                        appCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreGauge(score: PrivacyScore?) {
    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        if (score == null) {
            CircularProgressIndicator(modifier = Modifier.size(88.dp), strokeWidth = 8.dp)
        } else {
            CircularProgressIndicator(
                progress = { score.value / PrivacyScore.MAX.toFloat() },
                modifier = Modifier.size(88.dp),
                color = score.rating.color.accent,
                trackColor = score.rating.color.container,
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = score.value.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.score_out_of),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}
