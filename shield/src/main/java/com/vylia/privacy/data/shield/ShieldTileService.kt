package com.vylia.privacy.data.shield

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vylia.privacy.domain.shield.ShieldIndicator
import com.vylia.privacy.domain.shield.ShieldIndicatorState
import com.vylia.privacy.shield.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tuile des réglages rapides : affiche l'état en direct du bouclier (le même que la pastille de l'app) et réagit à un
 * appui de la même façon. L'accord VPN d'Android et le motif d'un contournement ont besoin d'un écran : la tuile
 * ouvre alors l'app.
 */
class ShieldTileService : TileService() {

    private val controller get() = (application as ShieldHost).shieldController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onStartListening() {
        watching?.cancel()
        watching = scope.launch { controller.indicator.collect(::render) }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val result = try {
            controller.tap()
        } catch (_: IllegalStateException) {
            // Android refuse parfois de démarrer un service depuis l'arrière-plan : l'app prend le relais.
            null
        }
        when (result) {
            ShieldTapResult.Done -> Unit
            is ShieldTapResult.NeedsConsent, is ShieldTapResult.ShowBypass, null -> unlockAndRun(::openApp)
        }
    }

    private fun openApp() {
        val intent = openAppIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(indicator: ShieldIndicator) {
        val tile = qsTile ?: return
        val (state, subtitle) = when (indicator.state) {
            ShieldIndicatorState.ACTIVE -> Tile.STATE_ACTIVE to R.string.shield_tile_active
            ShieldIndicatorState.INACTIVE -> Tile.STATE_INACTIVE to R.string.shield_tile_inactive
            ShieldIndicatorState.STARTING -> Tile.STATE_ACTIVE to R.string.shield_tile_starting
            ShieldIndicatorState.BYPASSED -> Tile.STATE_INACTIVE to R.string.shield_tile_bypassed
        }
        tile.state = state
        tile.label = getString(R.string.shield_tile_label)
        // Texte en plus de la couleur : l'état se lit sans distinguer les teintes, et TalkBack l'annonce.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(subtitle)
        tile.contentDescription = getString(R.string.shield_tile_label) + ", " + getString(subtitle)
        tile.updateTile()
    }
}
