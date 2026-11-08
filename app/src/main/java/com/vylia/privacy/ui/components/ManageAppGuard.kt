package com.vylia.privacy.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.scoring.ScoreResult
import com.vylia.privacy.ui.safety.SurveillanceHelpDialog

/** Logique de la protection, sans Android : une app à capacités de surveillance passe par l'avertissement. */
object ManageAppGuard {
    fun needsWarning(result: ScoreResult): Boolean = result.securityFindings.isNotEmpty()
}

/**
 * Ouvre la fiche système d'une app (où se trouve Désinstaller). Pour une app qui a des capacités de surveillance, un
 * avertissement s'affiche d'abord : la supprimer peut prévenir la personne qui l'a installée, qui peut réagir de façon
 * dangereuse, et des preuves peuvent se perdre. « Voir que faire » ouvre l'aide, « Continuer » la fiche système.
 */
@Composable
fun rememberManageApp(): (AuditedApp) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(false) }

    pending?.let { packageName ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.guard_title)) },
            text = { Text(stringResource(R.string.guard_text)) },
            confirmButton = {
                TextButton(onClick = { pending = null; help = true }) { Text(stringResource(R.string.guard_help)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null; openAppSettings(context, packageName) }) { Text(stringResource(R.string.guard_continue)) }
            },
        )
    }
    if (help) SurveillanceHelpDialog(onDismiss = { help = false })

    return { app ->
        if (ManageAppGuard.needsWarning(app.result)) pending = app.app.packageName
        else openAppSettings(context, app.app.packageName)
    }
}
