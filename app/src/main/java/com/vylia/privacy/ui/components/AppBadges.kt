package com.vylia.privacy.ui.components

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R
import com.vylia.privacy.domain.scoring.BadgeDef
import com.vylia.privacy.domain.scoring.SecurityFinding

/**
 * Textes traduits des badges et des patterns de sécurité des règles v2.1. Un badge ou un pattern ajouté par import
 * de règles n'a pas de traduction : on affiche alors le libellé et la description du JSON.
 */
private val BadgeTexts = mapOf(
    // region generated:badges (tools/strings/generate.py)
    "PERM_FEATHER" to (R.string.badgedef_perm_feather to R.string.badgedef_perm_feather_description),
    "PERM_ON_TARGET" to (R.string.badgedef_perm_on_target to R.string.badgedef_perm_on_target_description),
    "PERM_NOSY" to (R.string.badgedef_perm_nosy to R.string.badgedef_perm_nosy_description),
    "PERM_FISHING" to (R.string.badgedef_perm_fishing to R.string.badgedef_perm_fishing_description),
    "PERM_OUTLAW" to (R.string.badgedef_perm_outlaw to R.string.badgedef_perm_outlaw_description),
    "PERM_GLUTTON" to (R.string.badgedef_perm_glutton to R.string.badgedef_perm_glutton_description),
    "SEC_WANTED" to (R.string.badgedef_sec_wanted to R.string.badgedef_sec_wanted_description),
    "SEC_BIG_EARS" to (R.string.badgedef_sec_big_ears to R.string.badgedef_sec_big_ears_description),
    "SEC_SPY_PIANIST" to (R.string.badgedef_sec_spy_pianist to R.string.badgedef_sec_spy_pianist_description),
    "SEC_MOLOTOV" to (R.string.badgedef_sec_molotov to R.string.badgedef_sec_molotov_description),
    "TRK_ORGANIC" to (R.string.badgedef_trk_organic to R.string.badgedef_trk_organic_description),
    "TRK_MECHANIC" to (R.string.badgedef_trk_mechanic to R.string.badgedef_trk_mechanic_description),
    "TRK_SANDWICH" to (R.string.badgedef_trk_sandwich to R.string.badgedef_trk_sandwich_description),
    "TRK_AD_NETWORK" to (R.string.badgedef_trk_ad_network to R.string.badgedef_trk_ad_network_description),
    "TRK_GPS_SNITCH" to (R.string.badgedef_trk_gps_snitch to R.string.badgedef_trk_gps_snitch_description),
    "TRK_FULL_HOUSE" to (R.string.badgedef_trk_full_house to R.string.badgedef_trk_full_house_description),
    "TRK_HORNET_NEST" to (R.string.badgedef_trk_hornet_nest to R.string.badgedef_trk_hornet_nest_description),
    "TRK_BLACK_BOX" to (R.string.badgedef_trk_black_box to R.string.badgedef_trk_black_box_description),
    "PERM_ROLLING" to (R.string.badgedef_perm_rolling to R.string.badgedef_perm_rolling_description),
    "PERM_NIGHT_OWL" to (R.string.badgedef_perm_night_owl to R.string.badgedef_perm_night_owl_description),
    "PERM_DORMANT" to (R.string.badgedef_perm_dormant to R.string.badgedef_perm_dormant_description),
    "COMBO_TOP_OF_CLASS" to (R.string.badgedef_combo_top_of_class to R.string.badgedef_combo_top_of_class_description),
    "COMBO_DOUBLE_TROUBLE" to (R.string.badgedef_combo_double_trouble to R.string.badgedef_combo_double_trouble_description),
    // endregion
)

private val SecurityTexts = mapOf(
    // region generated:security (tools/strings/generate.py)
    "KNOWN_STALKERWARE" to (R.string.security_known_stalkerware to R.string.security_known_stalkerware_description),
    "KEYLOGGER_CAPABLE" to (R.string.security_keylogger_capable to R.string.security_keylogger_capable_description),
    "COVERT_RECORDING" to (R.string.security_covert_recording to R.string.security_covert_recording_description),
    "STALKERWARE_PROFILE" to (R.string.security_stalkerware_profile to R.string.security_stalkerware_profile_description),
    "OVERLAY_TROJAN" to (R.string.security_overlay_trojan to R.string.security_overlay_trojan_description),
    // endregion
)

fun Resources.badgeLabel(badge: BadgeDef): String = BadgeTexts[badge.id]?.first?.let(::getString) ?: badge.label

fun Resources.badgeDescription(badge: BadgeDef): String? =
    BadgeTexts[badge.id]?.second?.let(::getString) ?: badge.description

fun Resources.securityLabel(finding: SecurityFinding): String =
    SecurityTexts[finding.id]?.first?.let(::getString) ?: finding.label

fun Resources.securityDescription(finding: SecurityFinding): String? =
    SecurityTexts[finding.id]?.second?.let(::getString) ?: finding.description

/** Pictogramme ludique de chaque badge (champ `icon` des règles). */
fun badgeEmoji(badge: BadgeDef): String = when (badge.icon) {
    "ic_badge_feather" -> "🪶"
    "ic_badge_target" -> "🎯"
    "ic_badge_nosy" -> "👀"
    "ic_badge_fishhook" -> "🎣"
    "ic_badge_mask" -> "🥷"
    "ic_badge_glutton" -> "🍔"
    "ic_badge_spy" -> "🕵️"
    "ic_badge_ear" -> "👂"
    "ic_badge_keyboard" -> "⌨️"
    "ic_badge_flask" -> "🧪"
    "ic_badge_leaf" -> "🌿"
    "ic_badge_wrench" -> "🔧"
    "ic_badge_megaphone" -> "📢"
    "ic_badge_tower" -> "📡"
    "ic_badge_pin" -> "📍"
    "ic_badge_confetti" -> "🎊"
    "ic_badge_bug" -> "🐝"
    "ic_badge_box" -> "📦"
    "ic_badge_trophy" -> "🏆"
    "ic_badge_skull" -> "💀"
    "ic_badge_clapper" -> "🎬"
    "ic_badge_owl" -> "🦉"
    "ic_badge_moon" -> "🌙"
    else -> "🏷️"
}

/**
 * Couleur d'un badge selon sa gravité. Philosophie non anxiogène : les bonnes pratiques sont aussi visibles
 * que les points d'attention, et le rouge est réservé aux cas graves.
 */
fun badgeColor(severity: String): Color = when (severity) {
    "success" -> Color(0xFF10B981)
    "pro" -> Color(0xFF38BDF8)
    "warning" -> Color(0xFFF59E0B)
    "danger" -> Color(0xFFEF4444)
    else -> Color(0xFF94A3B8)
}

/** Pastille d'un badge : pictogramme et libellé, sur un fond teinté de sa couleur. */
@Composable
fun BadgeChip(badge: BadgeDef, modifier: Modifier = Modifier) {
    val color = badgeColor(badge.severity)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = "${badgeEmoji(badge)} ${LocalResources.current.badgeLabel(badge)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(badges: List<BadgeDef>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { BadgeChip(it) }
    }
}

/**
 * Fiche système de l'app (Désinstaller, Autorisations, Forcer l'arrêt) : aucune permission requise,
 * contrairement à ACTION_DELETE, et sans passer par le Play Store.
 */
fun openAppSettings(context: Context, packageName: String) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
}
