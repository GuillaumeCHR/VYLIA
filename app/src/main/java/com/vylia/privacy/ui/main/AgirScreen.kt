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
import androidx.compose.ui.graphics.Color
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.ScoreBadge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.domain.model.AuditedApp
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
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
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
        
        // Section 2: Assainir
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Assainir (Worst of Fame)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Les applications les plus intrusives de votre appareil. Envisagez des alternatives.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (worstOfFame.isEmpty()) {
                    Text("Aucune application critique détectée !", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    worstOfFame.take(5).forEach { app ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app.app.label, modifier = Modifier.weight(1f))
                            Text("${app.score.value}/100", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        // Section 3: Valoriser
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)) // Light green
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🏆 Valoriser (Hall of Fame)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                            ScoreBadge(score = app.score.value, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

suspend fun generateAndSaveScoreBitmap(context: Context, score: Int): Uri? = withContext(Dispatchers.IO) {
    try {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        canvas.drawColor(android.graphics.Color.parseColor("#121212")) // Dark mode bg
        
        // Draw texts
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 80f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawText("Mon Bilan VYLIA", width / 2f, 400f, paint)
        
        paint.textSize = 250f
        paint.color = when {
            score >= 80 -> android.graphics.Color.parseColor("#4CAF50")
            score >= 50 -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#F44336")
        }
        
        canvas.drawText("$score/100", width / 2f, 1000f, paint)
        
        paint.textSize = 50f
        paint.color = android.graphics.Color.LTGRAY
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
