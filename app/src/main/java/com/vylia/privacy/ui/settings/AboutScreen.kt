package com.vylia.privacy.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.VyliaLogotype
import com.vylia.privacy.ui.components.YogiStudioLogo
import com.vylia.privacy.ui.splash.VyliaLogo
import com.vylia.privacy.ui.theme.CyanAccent

/**
 * « À propos » : logo, version, éditeur (Yogi Studio), encart « Vie privée » (chaque phrase est vraie et
 * vérifiable, lien vers les règles de confidentialité), licence GPL v3 et code source, crédits Exodus Privacy.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    SubPage(modifier, R.string.settings_about, onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VyliaLogo(modifier = Modifier.size(88.dp))
            VyliaLogotype(fontSize = 32.sp)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, color = CyanAccent)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_publisher), style = MaterialTheme.typography.bodyLarge)
            YogiStudioLogo(markSize = 56.dp)
        }

        InfoCard(R.string.about_privacy_title) {
            // L'essentiel en tête : rien n'est collecté, l'analyse se fait sur le téléphone.
            Text(
                stringResource(R.string.about_privacy_headline),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            listOf(
                R.string.about_privacy_no_ads,
                R.string.about_privacy_self,
                R.string.about_privacy_network,
                R.string.about_privacy_automation,
                R.string.about_privacy_local,
            ).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
            }
            // Règles de confidentialité du site, dans la langue de l'app (exigées par Google Play dans l'app).
            val privacyUrl = stringResource(R.string.about_privacy_policy_url)
            OutlinedButton(onClick = { openUrl(context, privacyUrl) }) { Text(stringResource(R.string.about_privacy_policy_button)) }
        }

        InfoCard(R.string.about_license_title) {
            listOf(R.string.about_license, R.string.about_license_trademarks, R.string.about_shield_lists, R.string.about_font).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.about_source, SOURCE_URL), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { openUrl(context, SOURCE_URL) }) { Text(stringResource(R.string.about_source_button)) }
            OutlinedButton(onClick = { openUrl(context, GPL_URL) }) { Text(stringResource(R.string.about_license_button)) }
        }

        InfoCard(R.string.about_contact_title) {
            Text(stringResource(R.string.about_contact, SUPPORT_EMAIL), style = MaterialTheme.typography.bodyMedium)
            val subject = stringResource(R.string.about_contact_subject, BuildConfig.VERSION_NAME)
            OutlinedButton(onClick = { composeEmail(context, subject) }) { Text(stringResource(R.string.about_contact_button)) }
        }

        ExodusCreditsCard()

        EchapCreditsCard()

        MvtCreditsCard()

        CreditsSection()
    }
}

/** Ouvre l'app de messagerie sur un e-mail à Yogi Studio ; sans app d'e-mail, l'adresse reste lisible à l'écran. */
private fun composeEmail(context: Context, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        // Aucune app d'e-mail.
    }
}

private const val SUPPORT_EMAIL = "yogistudiofr@gmail.com"
private const val SOURCE_URL = "https://github.com/GuillaumeCHR/VYLIA"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
