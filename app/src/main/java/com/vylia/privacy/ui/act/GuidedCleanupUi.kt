package com.vylia.privacy.ui.act

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.domain.act.ReductionAdvisor
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.shield.ShieldStatus
import com.vylia.privacy.ui.scan.ScanViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vylia.privacy.R
import com.vylia.privacy.domain.act.CleanupAction
import com.vylia.privacy.domain.act.CleanupStep
import com.vylia.privacy.domain.act.GuidedCleanup
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.rewards.Badge
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.ui.components.AppIcon
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.components.formatted
import com.vylia.privacy.ui.components.rememberManageApp

/**
 * Le balai de la barre du haut : [ready] une fois le scan et l'analyse des traqueurs finis, [due] quand le ménage est
 * proposé (jamais fait jusqu'au bout, ou score du téléphone passé sous celui du dernier ménage).
 */
class CleanupLauncher(val ready: Boolean, val due: Boolean, val start: () -> Unit)

/**
 * Ménage guidé, d'où qu'on le lance : calcule les étapes du moment (les mêmes que Gérer › Autorisations et
 * Suppressions, plus le bouclier s'il ne filtre pas encore), affiche le parcours par-dessus l'écran en cours, et rend
 * le balai de la barre.
 */
@Composable
fun rememberGuidedCleanup(
    viewModel: ScanViewModel,
    actViewModel: ActViewModel = viewModel(factory = ActViewModel.Factory),
): CleanupLauncher {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedRules by viewModel.rules.collectAsStateWithLifecycle()
    val scorer by viewModel.scorer.collectAsStateWithLifecycle()
    val exposure by viewModel.shieldExposure.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    val usage by actViewModel.usage.collectAsStateWithLifecycle()
    val shield by actViewModel.shield.collectAsStateWithLifecycle()
    val session by actViewModel.cleanup.collectAsStateWithLifecycle()
    // Les apps inutilisées dépendent de l'accès aux données d'utilisation, peut-être accordé entre-temps.
    LifecycleResumeEffect(Unit) {
        actViewModel.refreshUsage()
        onPauseOrDispose { }
    }

    val userApps = remember(state.scannedApps) { state.scannedApps.filter { AppFilter.USER.matches(it.app) } }
    val shieldOn = shield.status == ShieldStatus.ON
    val deviceScore = remember(userApps) { PrivacyScore.average(userApps.map { it.score })?.value }
    val steps = remember(userApps, scorer, usage, exposure, loadedRules, shieldOn) {
        val revocations = ReductionAdvisor.byApp(ReductionAdvisor.revocations(userApps, scorer), scorer)
        val removals = ReductionAdvisor.removals(userApps, usage.lastUsed, System.currentTimeMillis())
        val shielded = exposure?.takeIf { BuildConfig.SHIELD_ENABLED && !it.active && !shieldOn }?.let { e ->
            userApps.associate { it.app.packageName to (loadedRules.scorer.shielded(it.result, e::forScore)?.score ?: it.score.value) }
        }
        GuidedCleanup.plan(userApps, revocations, removals, shielded)
    }
    var nothingToDo by remember { mutableStateOf(false) }

    session?.let { current ->
        GuidedCleanupDialog(
            session = current,
            apps = userApps,
            deviceScore = deviceScore,
            shieldOn = shieldOn,
            actViewModel = actViewModel,
            refreshApp = { viewModel.refreshApps(setOf(it)) },
            onFinish = { stepsDone ->
                viewModel.finishGuidedCleanup(deviceScore, stepsDone)
                actViewModel.endCleanup()
            },
        )
    }
    if (nothingToDo) {
        AlertDialog(
            onDismissRequest = { nothingToDo = false },
            title = { Text(stringResource(R.string.cleanup_title)) },
            text = { Text(stringResource(R.string.cleanup_nothing)) },
            confirmButton = { TextButton(onClick = { nothingToDo = false }) { Text(stringResource(R.string.cleanup_close)) } },
        )
    }

    val ready = !state.isLoading && !state.isAnalyzingTrackers && userApps.isNotEmpty()
    return CleanupLauncher(
        ready = ready,
        due = ready && steps.isNotEmpty() && session == null &&
            rewards.cleanupScore.let { last -> last == null || (deviceScore != null && deviceScore < last) },
        start = { if (steps.isEmpty()) nothingToDo = true else actViewModel.startCleanup(steps, deviceScore) },
    )
}

/**
 * Le parcours, une étape à la fois. Chaque étape est constatée faite d'après le téléphone lui-même (autorisation
 * retirée, app absente, bouclier actif), au retour des réglages Android : rien n'est coché sur parole.
 *
 * @param apps apps utilisateur du scan, à jour.
 * @param shieldOn le Bouclier VYLIA filtre.
 * @param refreshApp relit les autorisations d'une app (retour des réglages Android).
 * @param onFinish fin du parcours, avec le nombre d'étapes faites.
 */
@Composable
internal fun GuidedCleanupDialog(
    session: CleanupSession,
    apps: List<AuditedApp>,
    deviceScore: Int?,
    shieldOn: Boolean,
    actViewModel: ActViewModel,
    refreshApp: (String) -> Unit,
    onFinish: (stepsDone: Int) -> Unit,
) {
    val step = session.current
    // Constat des étapes déjà faites, à chaque changement du scan ou du bouclier.
    LaunchedEffect(session.steps, apps, shieldOn) {
        session.steps.filter { it.action.key !in session.done && isDone(it.action, apps, shieldOn) }
            .forEach { actViewModel.cleanupDone(it.action.key) }
    }
    // Retour des réglages Android : les autorisations de l'app de l'étape ont peut-être changé.
    LifecycleResumeEffect(step) {
        (step?.action as? CleanupAction.Revoke)?.let { refreshApp(it.revocations.app.app.packageName) }
        onPauseOrDispose { }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                if (step == null) stringResource(R.string.cleanup_summary_title)
                else stringResource(R.string.cleanup_step_title, session.index + 1, session.steps.size),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step == null) Summary(session, deviceScore) else StepContent(step, done = step.action.key in session.done, actViewModel)
            }
        },
        confirmButton = {
            when {
                step == null -> Button(onClick = { onFinish(session.done.size) }) { Text(stringResource(R.string.cleanup_close)) }
                step.action.key in session.done -> Button(onClick = { actViewModel.cleanupNext() }) {
                    Text(stringResource(if (session.index + 1 < session.steps.size) R.string.cleanup_next else R.string.cleanup_see_summary))
                }
                else -> TextButton(onClick = { actViewModel.cleanupNext() }) { Text(stringResource(R.string.cleanup_skip)) }
            }
        },
        dismissButton = {
            if (step != null) TextButton(onClick = { actViewModel.cleanupNext(stop = true) }) { Text(stringResource(R.string.cleanup_stop)) }
        },
    )
}

private fun isDone(action: CleanupAction, apps: List<AuditedApp>, shieldOn: Boolean): Boolean = when (action) {
    is CleanupAction.Revoke -> {
        val app = apps.find { it.app.packageName == action.revocations.app.app.packageName }
        // App supprimée entre-temps : ses autorisations sont parties avec elle.
        app == null || app.app.grantedPermissions.none { it in action.revoked }
    }
    is CleanupAction.Remove -> apps.none { it.app.packageName == action.removal.app.app.packageName }
    CleanupAction.Shield -> shieldOn
}

@Composable
private fun StepContent(step: CleanupStep, done: Boolean, actViewModel: ActViewModel) {
    val manage = rememberManageApp()
    val resources = LocalResources.current
    when (val action = step.action) {
        is CleanupAction.Revoke -> {
            val entry = action.revocations
            AppLine(entry.app, stringResource(R.string.cleanup_revoke_title, entry.app.app.label))
            val permissions = entry.suggestions.joinToString(" · ") { s ->
                PermissionGlossary.titleOf(s.finding.permission)?.let(resources::getString) ?: s.finding.permission
            }
            Text(permissions, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.cleanup_revoke_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gains(step)
            if (!done) OutlinedButton(onClick = { manage(entry.app) }) { Text(stringResource(R.string.reduce_action_permissions)) }
        }
        is CleanupAction.Remove -> {
            val removal = action.removal
            AppLine(removal.app, stringResource(R.string.cleanup_remove_title, removal.app.app.label))
            Text(
                if (removal.app.score.value < BadgesManager.INTRUSIVE_THRESHOLD) stringResource(R.string.cleanup_remove_intrusive)
                else lastUsedText(removal.lastUsedAt, usageKnown = true),
                style = MaterialTheme.typography.bodyMedium,
            )
            Gains(step)
            if (!done) OutlinedButton(onClick = { manage(removal.app) }) { Text(stringResource(R.string.reduce_action_uninstall)) }
        }
        CleanupAction.Shield -> {
            Text(stringResource(R.string.cleanup_shield_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.cleanup_shield_text), style = MaterialTheme.typography.bodyMedium)
            Gains(step)
            val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) actViewModel.startShield()
            }
            if (!done) {
                Button(onClick = { actViewModel.shieldConsentIntent()?.let(consent::launch) ?: actViewModel.startShield() }) {
                    Text(stringResource(R.string.detail_shield_turn_on))
                }
            }
        }
    }
    if (done) Text(stringResource(R.string.cleanup_step_done), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun AppLine(app: AuditedApp, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(app.app.icon, 32.dp)
        Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Gains(step: CleanupStep) {
    val style = MaterialTheme.typography.labelLarge
    if (step.appBefore != null && step.appAfter != null) {
        Text(stringResource(R.string.cleanup_app_score, step.appBefore, step.appAfter), style = style)
    }
    Text(deviceGainText(step.deviceBefore, step.deviceAfter), style = style, color = MaterialTheme.colorScheme.primary)
}

/** « Score du téléphone : 64 → 66 », ou le gain en décimales s'il ne change pas la moyenne arrondie. */
@Composable
private fun deviceGainText(before: Double, after: Double): String {
    val from = GuidedCleanup.rounded(before)
    val to = GuidedCleanup.rounded(after)
    return if (to != from) stringResource(R.string.cleanup_device_score, from, to)
    else stringResource(R.string.cleanup_device_gain_small, (after - before).formatted(currentLocale(), decimals = 1))
}

@Composable
private fun Summary(session: CleanupSession, deviceScore: Int?) {
    val done = session.done.size
    Text(
        if (done == 0) stringResource(R.string.cleanup_summary_none)
        else pluralStringResource(R.plurals.cleanup_summary_done, done, done, session.steps.size),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (done > 0 && session.startScore != null && deviceScore != null) {
        Text(stringResource(R.string.cleanup_device_score, session.startScore, deviceScore), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    if (done > 0) {
        VyliaCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Badge.GUIDED_CLEANUP.emoji, style = MaterialTheme.typography.headlineMedium)
                Column {
                    Text(stringResource(Badge.GUIDED_CLEANUP.title), fontWeight = FontWeight.Bold)
                    Text(stringResource(Badge.GUIDED_CLEANUP.description), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(stringResource(R.string.cleanup_summary_again), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
