package com.vylia.privacy.ui.safety

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vylia.privacy.R
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.domain.safety.SupportResource
import com.vylia.privacy.domain.safety.SurveillanceChoice
import com.vylia.privacy.domain.safety.SurveillancePath
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.settings.openUrl

/**
 * « Que faire ? » après une détection de surveillance, ou depuis l'Aide : ligne d'urgence, principes, puis le parcours
 * selon qui pourrait être à l'origine de la surveillance, avec quelques exemples de ressources internationales.
 *
 * Discrétion : le choix n'est gardé qu'en mémoire (`remember`, ni sauvegardé ni restauré) et aucun nom d'app n'est
 * affiché.
 */
@Composable
fun SurveillanceHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SurveillanceHelp(onDismiss)
        }
    }
}

@Composable
private fun SurveillanceHelp(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as VyliaApplication).container.supportResourcesRepository }
    val resources = remember(repository) { repository.resources }
    // Seulement en mémoire : la réponse n'est enregistrée nulle part, même pas dans l'état sauvegardé de l'écran.
    var choice by remember { mutableStateOf<SurveillanceChoice?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.safety_close)) }
            Text(
                stringResource(R.string.safety_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    stringResource(R.string.safety_emergency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Heading(R.string.safety_principles_title)
            listOf(
                R.string.safety_principle_wait,
                R.string.safety_principle_traces,
                R.string.safety_principle_device,
                R.string.safety_principle_certainty,
            ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()
            Heading(R.string.safety_question)
            Text(stringResource(R.string.safety_question_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                SurveillanceChoice.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == option, role = Role.RadioButton) { choice = option }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == option, onClick = null)
                        Text(stringResource(option.label()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            choice?.paths?.forEach { path ->
                HorizontalDivider()
                PathSection(path, resources.forPath(path), context)
            }

            HorizontalDivider()
            Heading(R.string.safety_limit_title)
            Text(stringResource(R.string.safety_limit_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PathSection(path: SurveillancePath, found: List<SupportResource>, context: Context) {
    Heading(path.title())
    Text(stringResource(path.text()), style = MaterialTheme.typography.bodyMedium)
    if (path == SurveillancePath.CRIMINAL) Text(stringResource(R.string.safety_criminal_tips), style = MaterialTheme.typography.bodyMedium)
    if (found.isNotEmpty()) {
        Text(stringResource(R.string.safety_examples), style = MaterialTheme.typography.labelLarge)
        val language = currentLocale().language
        found.forEach { ResourceRow(it, language, context) }
        Text(stringResource(R.string.safety_resources_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourceRow(resource: SupportResource, language: String, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(resource.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        resource.description(language)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            resource.phone?.let { phone ->
                val callLabel = stringResource(R.string.safety_resource_call, phone)
                // Ouvre le clavier du téléphone avec le numéro : rien n'est composé sans votre geste.
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(callLabel) }
            }
            val siteLabel = stringResource(R.string.safety_resource_site_named, resource.name)
            OutlinedButton(
                onClick = { openUrl(context, resource.url) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = siteLabel },
            ) { Text(stringResource(R.string.safety_resource_site)) }
        }
    }
}

@Composable
private fun Heading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
}

private fun SurveillanceChoice.label(): Int = when (this) {
    SurveillanceChoice.PARTNER -> R.string.safety_choice_partner
    SurveillanceChoice.CRIMINAL -> R.string.safety_choice_criminal
    SurveillanceChoice.STATE -> R.string.safety_choice_state
}

private fun SurveillancePath.title(): Int = when (this) {
    SurveillancePath.PARTNER -> R.string.safety_partner_title
    SurveillancePath.CRIMINAL -> R.string.safety_criminal_title
    SurveillancePath.STATE -> R.string.safety_state_title
}

private fun SurveillancePath.text(): Int = when (this) {
    SurveillancePath.PARTNER -> R.string.safety_partner_text
    SurveillancePath.CRIMINAL -> R.string.safety_criminal_text
    SurveillancePath.STATE -> R.string.safety_state_text
}
