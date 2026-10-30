package com.vylia.privacy.ui.main

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.provider.Settings
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
import com.vylia.privacy.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun AgirScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    val recentRewards by viewModel.recentRewards.collectAsStateWithLifecycle()
    val apps = state.apps
    
    val hallOfFame = apps.filter { it.score.value >= 80 }
    val worstOfFame = apps.filter { it.score.value < 50 }.sortedBy { it.score.value }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Agir", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Sensibiliser
        VyliaCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sensibiliser", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Générez une image de votre bilan global de confidentialité (100% hors-ligne) pour la partager avec vos proches.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    coroutineScope.launch {
                        val uri = generateAndSaveScoreBitmap(context, state.averageScore?.value ?: 100)
                        uri?.let { shareImage(context, it) }
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Partager")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Partager mon bilan (Story)")
                }
            }
        }
        
        // Section 2: Hall of Fame / Récompenses
        RewardsSection(
            rewards = rewards,
            recentRewards = recentRewards,
            onDismissRecent = viewModel::dismissRecentRewards,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        // Section 3: Assainir
        VyliaCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            containerColor = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Assainir (Worst of Fame)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Les applications les plus intrusives de votre appareil. Désinstallez-les pour gagner des points : plus une app est intrusive, plus sa suppression rapporte.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (worstOfFame.isEmpty()) {
                    Text("Aucune application critique détectée !", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    worstOfFame.take(5).forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.app.label, fontWeight = FontWeight.Bold)
                                Text(
                                    "${app.score.value}/100 · +${PrivacyScore.MAX - app.score.value} pts si supprimée",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { openAppDetails(context, app.app.packageName) }) {
                                Text("Gérer")
                            }
                        }
                    }
                }
            }
        }
        
        // Section 4: Valoriser
        VyliaCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🌿 Valoriser (apps exemplaires)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Les bonnes élèves (Score >= 80). Zéro traqueur et respect de la vie privée.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (hallOfFame.isEmpty()) {
                    Text("Aucune application ne remplit les critères pour le moment.")
                } else {
                    hallOfFame.take(5).forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.app.label, modifier = Modifier.weight(1f))
                            ScoreBadge(app.score)
                        }
                    }
                }
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
            Text("🏆 Hall of Fame — Récompenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Chaque application intrusive (score < 50) que vous supprimez rapporte 100 − son score en points.", style = MaterialTheme.typography.bodyMedium)

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
                            text = "🎉 Bravo ! ${recentRewards.joinToString { it.label }} supprimée(s) : +${recentRewards.sumOf { it.points }} pts",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onDismissRecent) { Text("OK") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            AnimatedPoints(rewards.points)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Badges (${rewards.unlockedBadges.size}/${Badge.entries.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Badge.entries.forEach { badge -> BadgeChip(badge, unlocked = badge in rewards.unlockedBadges) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Applications intrusives supprimées", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (rewards.removedApps.isEmpty()) {
                Text(
                    "Aucune pour l'instant. Rendez-vous dans « Assainir » ci-dessous pour commencer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
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
                                "${removed.trackerCount} traqueur(s) éliminé(s) · ${dateFormat.format(removed.removedAt)}",
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
            "points de confidentialité",
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
            Text(badge.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                badge.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Fiche système de l'app (bouton Désinstaller) ; ne nécessite aucune permission, contrairement à ACTION_DELETE. */
private fun openAppDetails(context: Context, packageName: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    )
}

suspend fun generateAndSaveScoreBitmap(context: Context, score: Int): Uri? = withContext(Dispatchers.IO) {
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
        
        canvas.drawText("Mon Bilan VYLIA", width / 2f, 400f, paint)
        
        paint.textSize = 250f
        paint.color = when {
            score >= 80 -> Emerald.toArgb()
            score >= 50 -> android.graphics.Color.parseColor("#F59E0B")
            else -> android.graphics.Color.parseColor("#EF4444")
        }
        
        canvas.drawText("$score/100", width / 2f, 1000f, paint)
        
        paint.textSize = 50f
        paint.color = Slate400.toArgb()
        canvas.drawText("Analyse 100% locale, sans serveur", width / 2f, 1800f, paint)

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

fun shareImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Partager mon bilan"))
}
