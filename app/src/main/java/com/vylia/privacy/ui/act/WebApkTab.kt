package com.vylia.privacy.ui.act

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.web.WebService
import com.vylia.privacy.domain.web.WebServices
import com.vylia.privacy.ui.components.AppIcon
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.openAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Une app native qui a une version web connue, et ce que la remplacer retirerait. */
private class Candidate(val app: AuditedApp, val service: WebService) {
    val trackers = (app.trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.size ?: 0
    val sensitive = app.result.permissionFindings.count { it.granted && it.penalty > 0.0 }
}

/**
 * Gérer › WebAPK : remplacer une app par la version web du même service, installée par le navigateur. VYLIA ne crée
 * pas le WebAPK (c'est le navigateur) et ne mesure pas les traqueurs d'un site : pas de score pour la version web.
 * Au retour dans VYLIA, la liste des WebAPK installés est relue : si la version web d'une app est là, la carte
 * propose de désinstaller l'app native (par sa fiche Android, comme partout dans VYLIA).
 */
@Composable
internal fun WebApkTab(apps: List<AuditedApp>, webServices: WebServices) {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val installedSites by produceState(emptySet<String>(), resumes) { value = installedWebApkServices(context, webServices) }
    val browser = remember(resumes) { defaultBrowser(context) }
    val candidates = remember(apps, webServices) {
        apps.mapNotNull { app -> webServices.forPackage(app.app.packageName)?.let { Candidate(app, it) } }
    }
    val pending = candidates.filter { it.service.id in installedSites }
    val toInstall = candidates.filter { it.service.id !in installedSites }
    val examples = webServices.services.filter { s -> s.example && candidates.none { it.service.id == s.id } && s.id !in installedSites }
    var introOpen by rememberSaveable { mutableStateOf(true) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Intro(introOpen) { introOpen = !introOpen } }
        if (pending.isNotEmpty()) {
            items(pending, key = { "installed-${it.app.app.packageName}" }) { InstalledCard(it) }
        }
        item { SectionHeader(stringResource(R.string.webapk_candidates_title), "") }
        if (toInstall.isEmpty()) {
            item { Text(stringResource(R.string.webapk_candidates_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(toInstall, key = { "candidate-${it.app.app.packageName}" }) { CandidateCard(it, shortcutOnly = !browser.makesWebApk) }
        item { HowTo(browser) }
        if (examples.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.webapk_examples_title), "") }
            items(examples, key = { "example-${it.id}" }) { ExampleRow(it) }
        }
    }
}

@Composable
private fun Intro(open: Boolean, onToggle: () -> Unit) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.webapk_intro_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (open) {
                Text(stringResource(R.string.webapk_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.webapk_gain), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.webapk_loss), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.webapk_no_score), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Repliable une fois lue, pour ne pas encombrer l'écran à chaque visite.
            TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (open) R.string.webapk_hide_intro else R.string.webapk_show_intro))
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: Candidate, shortcutOnly: Boolean) {
    val context = LocalContext.current
    val app = candidate.app.app
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.icon, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.webapk_candidate_native, candidate.app.score.value), style = MaterialTheme.typography.bodySmall)
                }
            }
            // Ce qui disparaîtrait avec l'app ; pas de score pour la version web, VYLIA ne peut pas le mesurer.
            Text(
                if (candidate.trackers == 0 && candidate.sensitive == 0) stringResource(R.string.webapk_candidate_nothing)
                else pluralStringResource(
                    R.plurals.webapk_candidate_gain,
                    candidate.trackers,
                    candidate.trackers,
                    pluralStringResource(R.plurals.webapk_sensitive, candidate.sensitive, candidate.sensitive),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(candidate.service.url, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { openSite(context, candidate.service.url) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.webapk_install))
            }
            if (shortcutOnly && ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                OutlinedButton(onClick = { pinShortcut(context, candidate) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.webapk_shortcut))
                }
            }
        }
    }
}

@Composable
private fun InstalledCard(candidate: Candidate) {
    val context = LocalContext.current
    val app = candidate.app.app
    VyliaCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.icon, 32.dp)
                Text(stringResource(R.string.webapk_installed_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.webapk_installed_text, candidate.service.name), style = MaterialTheme.typography.bodyMedium)
            // La fiche Android de l'app, où se trouve le bouton Désinstaller : VYLIA ne désinstalle rien elle-même.
            Button(onClick = { openAppSettings(context, app.packageName) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.webapk_uninstall))
            }
        }
    }
}

@Composable
private fun HowTo(browser: Browser) {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.webapk_howto_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            listOf(R.string.webapk_howto_1, R.string.webapk_howto_2, R.string.webapk_howto_3).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
            }
            browser.label?.let { name ->
                Text(
                    stringResource(if (browser.makesWebApk) R.string.webapk_browser_ok else R.string.webapk_browser_shortcut, name),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (browser.makesWebApk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ExampleRow(service: WebService) {
    val context = LocalContext.current
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(service.name, fontWeight = FontWeight.Bold)
                Text(WebServices.hostOf(service.url).orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { openSite(context, service.url) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.webapk_open_site))
            }
        }
    }
}

/** Le navigateur par défaut, et s'il produit de vrais WebAPK (vérifié pour Chrome ; Samsung Internet et Edge aussi). */
private class Browser(val label: String?, val makesWebApk: Boolean)

private val WEBAPK_BROWSERS = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
    "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta", "com.microsoft.emmx",
)

private fun defaultBrowser(context: Context): Browser {
    val pm = context.packageManager
    val info = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")), PackageManager.MATCH_DEFAULT_ONLY)
    val pkg = info?.activityInfo?.packageName
    // « android » : aucun navigateur par défaut, Android demandera lequel utiliser.
    if (pkg == null || pkg == "android") return Browser(null, makesWebApk = true)
    return Browser(info.loadLabel(pm).toString(), pkg in WEBAPK_BROWSERS)
}

/** Services dont un WebAPK est installé, d'après l'adresse de départ de chaque WebAPK. */
private suspend fun installedWebApkServices(context: Context, webServices: WebServices): Set<String> = withContext(Dispatchers.IO) {
    context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNullTo(HashSet()) { info ->
        webServices.forUrl(info.metaData?.getString(WebServices.WEBAPK_START_URL))?.id
    }
}

private fun openSite(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: android.content.ActivityNotFoundException) {
        // Aucun navigateur.
    }
}

/** Raccourci épinglé vers le site, avec l'icône de l'app native, quand le navigateur ne produit pas de WebAPK. */
private fun pinShortcut(context: Context, candidate: Candidate) {
    val sizePx = (48 * context.resources.displayMetrics.density).toInt()
    val shortcut = ShortcutInfoCompat.Builder(context, "web-${candidate.service.id}")
        .setShortLabel(candidate.service.name)
        .setIcon(IconCompat.createWithBitmap(candidate.app.app.icon.toBitmap(sizePx, sizePx)))
        .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(candidate.service.url)))
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
