package com.vylia.privacy.ui.act

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.vylia.privacy.ui.theme.Emerald
import com.vylia.privacy.ui.theme.Slate300
import com.vylia.privacy.ui.theme.Slate400
import com.vylia.privacy.ui.theme.Slate900
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.ScoreBadge
import com.vylia.privacy.ui.components.openAppSettings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.rewards.Badge
import com.vylia.privacy.domain.rewards.RemovedApp
import com.vylia.privacy.domain.rewards.RewardsState
import com.vylia.privacy.ui.components.ScoreAnimationMillis
import com.vylia.privacy.ui.theme.CyanAccent
import java.text.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.currentLocale

/**
 * Récompenses et partage : points gagnés en supprimant des apps intrusives, trophées, historique des suppressions,
 * et bilan à partager (image générée sur le téléphone).
 */
@Composable
internal fun RewardsTab(
    averageScore: Int?,
    rewards: RewardsState,
    recentRewards: List<RemovedApp>,
    onDismissRecent: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RewardsSection(rewards, recentRewards, onDismissRecent, Modifier.fillMaxWidth())
        ShareCard(averageScore, rewards)
    }
}

/** Partager son bilan : score moyen, points et trophées, en image (100 % hors connexion). */
@Composable
private fun ShareCard(averageScore: Int?, rewards: RewardsState) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.act_share_chooser)
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.act_share_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.act_share_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                coroutineScope.launch {
                    val uri = generateAndSaveScoreBitmap(context, averageScore ?: 100, rewards.points, rewards.unlockedBadges.size)
                    uri?.let { shareImage(context, it, chooserTitle) }
                }
            }) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.act_share_button))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RewardsSection(
    rewards: RewardsState,
    recentRewards: List<RemovedApp>,
    onDismissRecent: () -> Unit,
    modifier: Modifier = Modifier
) {
    VyliaCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.act_rewards_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.act_rewards_text), style = MaterialTheme.typography.bodyMedium)

            AnimatedVisibility(
                visible = recentRewards.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.act_rewards_congrats, recentRewards.size,
                                recentRewards.joinToString { it.label }, recentRewards.sumOf { it.points },
                            ),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onDismissRecent) { Text(stringResource(R.string.ok)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            AnimatedPoints(rewards.points)

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.act_badges, rewards.unlockedBadges.size, Badge.entries.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Badge.entries.forEach { badge -> BadgeChip(badge, unlocked = badge in rewards.unlockedBadges) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.act_removed_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (rewards.removedApps.isEmpty()) {
                Text(
                    stringResource(R.string.act_removed_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val locale = currentLocale()
                val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.SHORT, locale) }
                rewards.removedApps.take(10).forEach { removed ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScoreBadge(PrivacyScore(removed.score))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(removed.label, fontWeight = FontWeight.Bold)
                            Text(
                                pluralStringResource(R.plurals.act_removed_line, removed.trackerCount, removed.trackerCount, dateFormat.format(removed.removedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("+${removed.points}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Compteur de points qui défile jusqu'à sa valeur à l'affichage, puis à chaque nouveau gain. */
@Composable
private fun AnimatedPoints(points: Int) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(points) {
        animated.animateTo(points.toFloat(), tween(ScoreAnimationMillis * 2))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = animated.value.toInt().toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = CyanAccent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.act_points),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun BadgeChip(badge: Badge, unlocked: Boolean) {
    Surface(
        modifier = Modifier.width(104.dp).alpha(if (unlocked) 1f else 0.4f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (unlocked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (unlocked) badge.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(badge.title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                stringResource(badge.description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

suspend fun generateAndSaveScoreBitmap(context: Context, score: Int, points: Int, trophies: Int): Uri? = withContext(Dispatchers.IO) {
    try {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        canvas.drawColor(Slate900.toArgb())
        
        // Draw texts
        val paint = Paint().apply {
            color = Slate300.toArgb()
            textSize = 80f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawText(context.getString(R.string.act_story_title), width / 2f, 400f, paint)
        
        paint.textSize = 250f
        paint.color = when {
            score >= 80 -> Emerald.toArgb()
            score >= 50 -> android.graphics.Color.parseColor("#F59E0B")
            else -> android.graphics.Color.parseColor("#EF4444")
        }
        
        canvas.drawText("$score/100", width / 2f, 1000f, paint)
        
        // Les accomplissements : points de confidentialité et trophées.
        paint.textSize = 60f
        paint.color = Slate300.toArgb()
        canvas.drawText(context.getString(R.string.act_story_points, points), width / 2f, 1250f, paint)
        canvas.drawText(
            context.resources.getQuantityString(R.plurals.act_story_trophies, trophies, trophies),
            width / 2f,
            1350f,
            paint,
        )

        paint.textSize = 50f
        paint.color = Slate400.toArgb()
        canvas.drawText(context.getString(R.string.act_story_footer), width / 2f, 1800f, paint)

        // Save to cache
        val imagePath = File(context.cacheDir, "images")
        imagePath.mkdirs()
        val file = File(imagePath, "vylia_score.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        null
    }
}

fun shareImage(context: Context, uri: Uri, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
