package com.vylia.privacy.ui.shield

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vylia.privacy.R
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.shield.ShieldController
import com.vylia.privacy.data.shield.ShieldTapResult
import com.vylia.privacy.domain.shield.ShieldBypass
import com.vylia.privacy.domain.shield.ShieldIndicator
import com.vylia.privacy.domain.shield.ShieldIndicatorState

/** Contrôle du bouclier commun à toutes les barres d'en-tête : l'état affiché et l'effet d'un appui. */
class ShieldViewModel(private val controller: ShieldController) : ViewModel() {
    val indicator = controller.indicator

    fun tap(): ShieldTapResult = controller.tap()

    fun activate(): ShieldTapResult = controller.activate()

    fun start() = controller.start()

    fun stop() = controller.stop()

    companion object {
        val Factory = viewModelFactory {
            initializer { ShieldViewModel((this[APPLICATION_KEY] as VyliaApplication).container.shieldController) }
        }
    }
}

/** Icône, texte et couleur de chaque état : la couleur n'est jamais seule à porter l'information. */
private data class PillLook(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    val content: Color,
    val border: Color,
)

private fun ShieldIndicatorState.look(): PillLook = when (this) {
    ShieldIndicatorState.ACTIVE ->
        PillLook(R.drawable.ic_sym_verified_user, R.string.shield_pill_active, R.string.shield_pill_desc_active, Color(0xFF34D399), Color(0xFF10B981))
    ShieldIndicatorState.INACTIVE ->
        PillLook(R.drawable.ic_sym_remove_moderator, R.string.shield_pill_inactive, R.string.shield_pill_desc_inactive, Color(0xFFFCA5A5), Color(0xFFF87171))
    ShieldIndicatorState.STARTING ->
        PillLook(R.drawable.ic_sym_shield, R.string.shield_pill_starting, R.string.shield_pill_desc_starting, Color(0xFF7DD3FC), Color(0xFF38BDF8))
    ShieldIndicatorState.BYPASSED ->
        PillLook(R.drawable.ic_sym_gpp_maybe, R.string.shield_pill_bypassed, R.string.shield_pill_desc_bypassed, Color(0xFFFCD34D), Color(0xFFF59E0B))
}

/**
 * Pastille du bouclier, à droite du logotype : un appui désactive, active (avec l'accord VPN d'Android au premier
 * lancement) ou, si le bouclier est contourné, ouvre la feuille qui en donne le motif. Même comportement sur tous les
 * écrans. Quand la place manque (petit écran, grande police), seul le texte disparaît, l'icône et la description
 * accessible restent.
 */
/** Ouvre la destination Se protéger ; fourni par l'écran principal, absent ailleurs (pas de lien alors). */
val LocalOpenProtect = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun ShieldControl(modifier: Modifier = Modifier, viewModel: ShieldViewModel = viewModel(factory = ShieldViewModel.Factory)) {
    val indicator by viewModel.indicator.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<ShieldBypass?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.start()
    }
    val handle: (ShieldTapResult) -> Unit = { result ->
        when (result) {
            is ShieldTapResult.NeedsConsent -> consent.launch(result.intent)
            is ShieldTapResult.ShowBypass -> sheet = result.bypass
            ShieldTapResult.Done -> Unit
        }
    }

    AdaptivePill(indicator, onClick = { handle(viewModel.tap()) }, modifier = modifier)

    sheet?.let { bypass ->
        ShieldBypassSheet(
            bypass = bypass,
            onDismiss = { sheet = null },
            onTurnOff = {
                sheet = null
                viewModel.stop()
            },
            onTakeOver = {
                sheet = null
                handle(viewModel.activate())
            },
        )
    }
}

/** Pastille complète si elle tient dans la place laissée par la barre, sinon icône seule. */
@Composable
private fun AdaptivePill(indicator: ShieldIndicator, onClick: () -> Unit, modifier: Modifier) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0)
        val full = subcompose("full") { ShieldPill(indicator, onClick, showLabel = true) }.map { it.measure(loose.copy(maxWidth = Int.MAX_VALUE)) }
        val fits = full.all { it.width <= constraints.maxWidth }
        val placeables = if (fits) full else subcompose("compact") { ShieldPill(indicator, onClick, showLabel = false) }.map { it.measure(loose) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEach { it.placeRelative(0, (height - it.height) / 2) }
        }
    }
}

@Composable
private fun ShieldPill(indicator: ShieldIndicator, onClick: () -> Unit, showLabel: Boolean) {
    val look = indicator.state.look()
    val description = stringResource(look.description)
    val label = stringResource(look.label)
    // Cible tactile de 48 dp ; la pastille visible fait 32 dp de haut.
    Surface(
        onClick = onClick,
        enabled = indicator.state != ShieldIndicatorState.STARTING,
        shape = RoundedCornerShape(16.dp),
        color = look.border.copy(alpha = 0.15f),
        contentColor = look.content,
        border = BorderStroke(1.dp, look.border),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp)
            // La description dit l'état et l'effet d'un appui (« Bouclier actif. Désactiver ») ; le texte n'est pas relu.
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(start = 6.dp, end = if (showLabel) 9.dp else 6.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(look.icon), contentDescription = null, modifier = Modifier.size(18.dp))
            if (showLabel) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
    }
}

/** Feuille du bouclier contourné : le motif, et le geste qui y répond. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShieldBypassSheet(bypass: ShieldBypass, onDismiss: () -> Unit, onTurnOff: () -> Unit, onTakeOver: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.shield_bypass_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (bypass == ShieldBypass.PRIVATE_DNS) R.string.shield_bypass_dns else R.string.shield_bypass_vpn),
                style = MaterialTheme.typography.bodyMedium,
            )
            val buttonPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            when (bypass) {
                ShieldBypass.PRIVATE_DNS -> {
                    Button(onClick = { openNetworkSettings(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = buttonPadding) {
                        Text(stringResource(R.string.shield_bypass_open_network))
                    }
                    OutlinedButton(
                        onClick = onTurnOff,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentPadding = buttonPadding,
                        border = BorderStroke(1.dp, Color(0xFFF87171)),
                    ) {
                        Text(stringResource(R.string.shield_turn_off), color = Color(0xFFFCA5A5))
                    }
                }
                ShieldBypass.OTHER_VPN ->
                    Button(onClick = onTakeOver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = buttonPadding) {
                        Text(stringResource(R.string.shield_take_over))
                    }
            }
            // Le détail (bouclier, DNS privé, et ce qui les oppose) est dans Se protéger.
            LocalOpenProtect.current?.let { openProtect ->
                TextButton(
                    onClick = { onDismiss(); openProtect() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.shield_bypass_open_protect)) }
            }
        }
    }
}

private fun openNetworkSettings(context: Context) {
    for (action in listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(action))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
