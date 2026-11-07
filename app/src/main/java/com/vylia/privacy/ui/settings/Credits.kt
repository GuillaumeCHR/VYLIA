package com.vylia.privacy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.VyliaCard
import com.vylia.privacy.ui.components.currentLocale
import com.vylia.privacy.ui.theme.Emerald
import com.vylia.privacy.ui.theme.Slate300
import com.vylia.privacy.ui.theme.Slate900

/**
 * Crédits de l'écran À propos, façon générique de film, sur le modèle de Molkky Board. Ton léger, mais chaque
 * affirmation est vraie (comme l'encart « Vie privée ») : zéro traqueur, zéro serveur, VYLIA exclue de son propre score.
 */
@Composable
internal fun CreditsSection() {
    VyliaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.about_credits),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Terminal()
            Text(stringResource(R.string.app_full_name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.credits_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.titleMedium,
            )
            Note(stringResource(R.string.credits_compiled, if (BuildConfig.DEBUG) "assembleDebug" else "assembleRelease"))
            CREDITS.forEach { Credit(it) }
            Heading(R.string.credits_stats_title)
            ProductionStats()
            Heading(R.string.credits_thanks_title)
            listOf(R.string.credits_thanks_exodus, R.string.credits_thanks_echap, R.string.credits_thanks_lists, R.string.credits_thanks_apps).forEach { Body(it) }
            Spacer(Modifier.height(12.dp))
            Note(stringResource(R.string.credits_outro))
            Note(stringResource(R.string.credits_post_credits))
        }
    }
}

/** Un rôle du générique : intitulé, nom, note en italique. */
private class CreditBlock(@param:StringRes val role: Int, @param:StringRes val name: Int, @param:StringRes val note: Int?)

private val CREDITS = listOf(
    CreditBlock(R.string.credits_production, R.string.credits_production_name, R.string.credits_production_note),
    CreditBlock(R.string.credits_dev, R.string.credits_dev_name, R.string.credits_dev_note),
    CreditBlock(R.string.credits_pair, R.string.credits_pair_name, R.string.credits_pair_note),
    CreditBlock(R.string.credits_engine, R.string.credits_engine_name, R.string.credits_engine_note),
    CreditBlock(R.string.credits_investigation, R.string.credits_investigation_name, R.string.credits_investigation_note),
    CreditBlock(R.string.credits_counter_spy, R.string.credits_counter_spy_name, R.string.credits_counter_spy_note),
    CreditBlock(R.string.credits_shield, R.string.credits_shield_name, R.string.credits_shield_note),
    CreditBlock(R.string.credits_cast, R.string.credits_cast_name, R.string.credits_cast_note),
    CreditBlock(R.string.credits_font, R.string.credits_font_name, R.string.credits_font_note),
    CreditBlock(R.string.credits_hardware, R.string.credits_hardware_name, R.string.credits_hardware_note),
)

@Composable
private fun Credit(block: CreditBlock) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Heading(block.role)
        Text(stringResource(block.name), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        block.note?.let { Note(stringResource(it)) }
    }
}

@Composable
private fun Heading(@StringRes text: Int) {
    Text(
        stringResource(text).uppercase(currentLocale()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Faux terminal, ardoise et vert émeraude dans les deux thèmes ; TalkBack le lit comme une seule phrase. */
@Composable
private fun Terminal() {
    val summary = stringResource(R.string.credits_terminal_description)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Slate900,
        contentColor = Slate300,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$ vylia --credits", style = TerminalStyle(), color = Emerald)
            listOf(
                R.string.credits_terminal_spies to "OK",
                R.string.credits_terminal_grid to "OK",
                R.string.credits_terminal_trackers to stringResource(R.string.credits_terminal_none),
                R.string.credits_terminal_cloud to stringResource(R.string.credits_terminal_no_cloud),
                R.string.credits_terminal_loading to "OK",
            ).forEach { (label, value) -> TerminalLine(stringResource(label), value) }
        }
    }
}

@Composable
private fun TerminalStyle() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

@Composable
private fun TerminalLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // La valeur garde toujours sa place ; un libellé trop long pour l'écran se termine par « … ».
        Text("> $label", style = TerminalStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(4f, fill = false))
        Text(DOTS, style = TerminalStyle(), maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
        Text(" $value", style = TerminalStyle(), maxLines = 1, color = Emerald)
    }
}

private val DOTS = ".".repeat(60)

/** Les chiffres qui comptent. Les zéros sont vrais : pas de pub, pas de traqueur, pas de compte, pas de serveur. */
@Composable
private fun ProductionStats() {
    val rows = listOf(
        R.string.credits_stat_ads to "0",
        R.string.credits_stat_trackers to "0",
        R.string.credits_stat_accounts to "0",
        R.string.credits_stat_servers to stringResource(R.string.credits_stat_servers_value),
        R.string.credits_stat_sent to "0",
        R.string.credits_stat_self_score to stringResource(R.string.credits_stat_self_score_value),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            // Grands chiffres en bout de ligne ; une valeur en mots passe sous son libellé, pour ne pas l'écraser.
            if (value.all { it.isDigit() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = Emerald, modifier = Modifier.padding(start = 12.dp))
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.titleSmall, color = Emerald)
                }
            }
        }
    }
}
