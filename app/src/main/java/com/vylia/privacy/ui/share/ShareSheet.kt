package com.vylia.privacy.ui.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.currentLocale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.NumberFormat

/**
 * Écran de partage, sur le modèle de Molkky Board : un modèle d'image par page (glisser pour changer), trois
 * formats, un texte d'accompagnement modifiable, partage direct, WhatsApp ou galerie ; et un clip animé (GIF) créé
 * sur le téléphone. Rien ne part vers un serveur ; aucun nom d'app ni métadonnée dans les fichiers.
 */
@Composable
fun ShareSheet(content: ShareContent, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ShareSheetContent(content, onDismiss)
        }
    }
}

@Composable
private fun ShareSheetContent(content: ShareContent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val templates = remember(content) { content.templates }
    var templateIndex by rememberSaveable { mutableIntStateOf(0) }
    var format by rememberSaveable { mutableStateOf(ShareFormat.STORY) }
    val template = templates[templateIndex.coerceIn(templates.indices)]
    val defaultText = defaultText(context, content, template)
    var editedText by rememberSaveable(template) { mutableStateOf<String?>(null) }
    val text = editedText ?: defaultText
    val layer = rememberGraphicsLayer()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.share_back)) }
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChipRow {
                templates.forEachIndexed { index, t ->
                    FilterChip(index == templateIndex, { templateIndex = index }, label = { Text(stringResource(t.label)) })
                }
            }
            TemplatePager(content, templates, templateIndex, format, layer) { templateIndex = it }
            ChipRow {
                ShareFormat.entries.forEach { f ->
                    FilterChip(format == f, { format = f }, label = { Text(stringResource(f.label)) })
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { editedText = it },
                label = { Text(stringResource(R.string.share_text_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.share_privacy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ImageButtons(context, layer, text)
            ClipSection(content, text)
        }
    }
}

/** Aperçu : un modèle par page ; la page visible est enregistrée dans [layer] pour le partage. */
@Composable
private fun TemplatePager(
    content: ShareContent,
    templates: List<ShareTemplate>,
    selected: Int,
    format: ShareFormat,
    layer: GraphicsLayer,
    onSelect: (Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = selected) { templates.size }
    LaunchedEffect(selected) { if (pager.currentPage != selected) pager.animateScrollToPage(selected) }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage != selected) onSelect(pager.currentPage) }
    HorizontalPager(pager, Modifier.fillMaxWidth().height(PREVIEW_HEIGHT.dp)) { page ->
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val scale = minOf(with(density) { maxWidth.toPx() } / format.width, with(density) { maxHeight.toPx() } / format.height)
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                ShareCard(
                    content, templates[page], format,
                    modifier = if (page == pager.currentPage) {
                        Modifier.drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
    Text(stringResource(R.string.share_template_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ImageButtons(context: Context, layer: GraphicsLayer, text: String) {
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.share_chooser)
    val whatsapp = remember { ShareFiles.isInstalled(context, ShareFiles.WHATSAPP) }
    suspend fun png(): File {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        return withContext(Dispatchers.IO) { ShareFiles.writePng(context, bitmap) }
    }
    fun share(target: String?) = scope.launch {
        val file = png()
        context.startSafely(ShareFiles.shareIntent(ShareFiles.uriFor(context, file), "image/png", text, title, target))
    }
    Button(onClick = { share(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_button)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (whatsapp) TextButton(onClick = { share(ShareFiles.WHATSAPP) }) { Text(stringResource(R.string.share_whatsapp)) }
        if (ShareFiles.canSaveToGallery) {
            TextButton(onClick = {
                scope.launch {
                    val file = png()
                    val ok = withContext(Dispatchers.IO) { ShareFiles.saveToGallery(context, file, "image/png") }
                    Toast.makeText(context, if (ok) R.string.share_saved else R.string.share_save_failed, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.share_save)) }
        }
    }
}

/** Le clip animé : créé à la demande (quelques secondes), puis partagé ou enregistré comme une image. */
@Composable
private fun ClipSection(content: ShareContent, text: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var clip by remember(content) { mutableStateOf<File?>(null) }
    val title = stringResource(R.string.share_chooser)
    val labels = ClipLabels(
        scoreLabel = stringResource(R.string.card_score_label),
        rating = stringResource(ratingLabel(content.score)),
        trackers = pluralStringResource(R.plurals.card_trackers_label, content.trackers),
        second = if (content.shieldBlocked > 0) {
            pluralStringResource(R.plurals.card_stat_blocked, content.shieldBlocked.toQuantity())
        } else {
            pluralStringResource(R.plurals.card_stat_trophies, content.unlockedBadges.size)
        },
        cta = stringResource(R.string.card_footer_cta),
        hashtag = stringResource(R.string.card_hashtag),
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.share_clip_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.share_clip_text), style = MaterialTheme.typography.bodyMedium)
        val running = job?.isActive == true
        when {
            running -> {
                Text(stringResource(R.string.share_clip_progress, progress), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            clip != null -> {
                val file = clip!!
                Button(onClick = {
                    context.startSafely(ShareFiles.shareIntent(ShareFiles.uriFor(context, file), "image/gif", text, title))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_clip_share)) }
                if (ShareFiles.canSaveToGallery) {
                    TextButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { ShareFiles.saveToGallery(context, file, "image/gif") }
                            Toast.makeText(context, if (ok) R.string.share_saved else R.string.share_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.share_save)) }
                }
            }
            else -> OutlinedButton(onClick = {
                progress = 0
                job = scope.launch {
                    clip = runCatching {
                        ShareClip.encode(context, ShareClip.renderer(context, content, labels, locale)) { progress = it }
                    }.getOrNull()
                    if (clip == null) Toast.makeText(context, R.string.share_clip_failed, Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_clip_button)) }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun Context.startSafely(intent: android.content.Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Aucune app pour recevoir le partage : rien à faire.
    }
}

/** Texte d'accompagnement proposé pour un modèle, modifiable. */
internal fun defaultText(context: Context, content: ShareContent, template: ShareTemplate): String = when (template) {
    ShareTemplate.REPORT -> context.getString(R.string.share_text_report, content.score)
    ShareTemplate.TRACKERS -> context.getString(R.string.share_text_trackers, content.trackers, content.appsWithTrackers)
    ShareTemplate.SHIELD -> context.getString(
        R.string.share_text_shield,
        NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(content.shieldBlocked),
    )
    ShareTemplate.PROGRESS -> context.getString(R.string.share_text_progress, content.firstScore ?: content.score, content.score)
    ShareTemplate.TROPHIES -> context.getString(R.string.share_text_trophies, content.unlockedBadges.size)
}

private val ShareTemplate.label: Int
    get() = when (this) {
        ShareTemplate.REPORT -> R.string.share_template_report
        ShareTemplate.TRACKERS -> R.string.share_template_trackers
        ShareTemplate.SHIELD -> R.string.share_template_shield
        ShareTemplate.PROGRESS -> R.string.share_template_progress
        ShareTemplate.TROPHIES -> R.string.share_template_trophies
    }

private val ShareFormat.label: Int
    get() = when (this) {
        ShareFormat.STORY -> R.string.share_format_story
        ShareFormat.PORTRAIT -> R.string.share_format_portrait
        ShareFormat.SQUARE -> R.string.share_format_square
    }

private const val PREVIEW_HEIGHT = 440
