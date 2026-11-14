package com.vylia.privacy.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.core.content.edit
import com.vylia.privacy.data.shield.ShieldController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Score du téléphone affiché par le widget : celui de l'app au dernier scan ou recalcul. */
class WidgetScoreStore(context: Context) {
    private val prefs = context.getSharedPreferences("vylia_widget", Context.MODE_PRIVATE)

    val score: Int? get() = prefs.getInt(KEY_SCORE, -1).takeIf { it in 0..100 }

    fun set(score: Int?) {
        prefs.edit { if (score == null) remove(KEY_SCORE) else putInt(KEY_SCORE, score) }
    }

    private companion object {
        const val KEY_SCORE = "score"
    }
}

/**
 * Redessine les widgets posés, seulement s'il y en a : à chaque nouveau score, aussitôt que le bouclier change d'état
 * ou de pause, et pour le compteur de requêtes bloquées au plus toutes les [COUNTER_REFRESH_MS], pour ne pas coûter
 * de batterie. Le bouclier tourne dans le même processus : tant qu'il filtre, ces abonnements vivent.
 */
@OptIn(FlowPreview::class)
class WidgetUpdater(
    private val context: Context,
    private val store: WidgetScoreStore,
    private val shield: ShieldController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pauseEnd: Job? = null

    init {
        scope.launch {
            combine(shield.indicator, shield.state.map { it.pausedUntil }.distinctUntilChanged()) { indicator, until -> indicator to until }
                .distinctUntilChanged()
                .drop(1)
                .collect { (_, until) ->
                    refresh()
                    schedulePauseEnd(until)
                }
        }
        scope.launch {
            shield.state.map { it.blockedToday }.distinctUntilChanged().sample(COUNTER_REFRESH_MS).collect { refresh() }
        }
    }

    /** La pause finit d'elle-même, sans changement d'état : le widget repasse à « actif » à l'heure dite. */
    private fun schedulePauseEnd(until: Long?) {
        pauseEnd?.cancel()
        val wait = (until ?: return) - System.currentTimeMillis()
        if (wait <= 0) return
        pauseEnd = scope.launch {
            delay(wait + 1_000)
            refresh()
        }
    }

    /** Nouveau score du téléphone (fin d'un scan complet, recalcul), `null` : effacé avec les données. */
    fun onScore(score: Int?) {
        if (score == store.score) return
        store.set(score)
        refresh()
    }

    fun refresh() {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = WidgetRenderer.ids(context, manager)
        if (ids.isEmpty()) return
        ids.forEach { manager.updateAppWidget(it, WidgetRenderer.render(context, manager, it)) }
    }

    private companion object {
        const val COUNTER_REFRESH_MS = 3 * 60_000L
    }
}
