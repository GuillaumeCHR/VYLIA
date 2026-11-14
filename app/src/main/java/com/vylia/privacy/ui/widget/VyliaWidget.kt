package com.vylia.privacy.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.MainActivity
import com.vylia.privacy.R
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.shield.ShieldController
import com.vylia.privacy.data.shield.ShieldTapResult
import com.vylia.privacy.di.AppContainer
import com.vylia.privacy.domain.learn.GradeBands
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.widget.WidgetAction
import com.vylia.privacy.domain.widget.WidgetShield
import com.vylia.privacy.domain.widget.WidgetState
import java.util.Date

/**
 * Widget d'écran d'accueil : score du téléphone, état du Bouclier VYLIA et requêtes bloquées aujourd'hui. Une seule
 * entrée dans le sélecteur, redimensionnable : en dessous de [MEDIUM_MIN_WIDTH_DP], le score seul.
 *
 * Pas de mise à jour périodique : [WidgetUpdater] le redessine à la fin d'un scan et quand le bouclier change.
 */
class VyliaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, WidgetRenderer.render(context, manager, it)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, WidgetRenderer.render(context, manager, id))
    }

    companion object {
        /** Largeur à partir de laquelle le widget montre aussi le bouclier. */
        const val MEDIUM_MIN_WIDTH_DP = 200f

        internal fun container(context: Context): AppContainer = (context.applicationContext as VyliaApplication).container
    }
}

/**
 * Bouton du bouclier du widget : pause, reprise ou activation, le même effet que dans l'app. Récepteur à part et non
 * exporté : le fournisseur du widget doit l'être pour l'écran d'accueil, et une autre app pourrait sinon couper le
 * bouclier en lui envoyant cette action. Seul le `PendingIntent` de VYLIA l'atteint.
 */
class WidgetShieldReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = VyliaWidgetProvider.container(context)
        val controller = container.shieldController
        when (WidgetRenderer.state(container).action) {
            WidgetAction.PAUSE -> controller.pause()
            WidgetAction.RESUME -> controller.resume()
            WidgetAction.ACTIVATE -> {
                val result = try {
                    controller.activate()
                } catch (_: IllegalStateException) {
                    // Android refuse parfois de démarrer un service depuis l'arrière-plan : l'app prend le relais.
                    null
                }
                if (result != ShieldTapResult.Done) context.startActivity(WidgetRenderer.openProtect(context))
            }
            WidgetAction.OPEN_APP, WidgetAction.NONE -> Unit
        }
        container.widgetUpdater.refresh()
    }
}

/** Construit les vues du widget à partir de l'état de l'app. */
internal object WidgetRenderer {

    fun state(container: AppContainer, now: Long = System.currentTimeMillis()): WidgetState {
        val score = container.widgetScoreStore.score
        val scoring = container.ruleMatrixRepository.current.value.rules.scoring
        val grade = score?.let { s -> GradeBands.from(scoring).firstOrNull { s in it.min..it.max }?.grade }
        val controller: ShieldController = container.shieldController
        return WidgetState.of(
            score = score,
            grade = grade,
            indicator = controller.indicator.value,
            pausedUntil = controller.state.value.pausedUntil,
            blockedToday = controller.blockedToday(),
            needsConsent = controller.consentIntent() != null,
            now = now,
        )
    }

    fun render(context: Context, manager: AppWidgetManager, id: Int): RemoteViews {
        val state = state(VyliaWidgetProvider.container(context))
        val small = build(context, state, medium = false)
        if (!BuildConfig.SHIELD_ENABLED) return small
        val medium = build(context, state, medium = true)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android choisit la mise en page selon la taille réelle du widget, à chaque redimensionnement.
            RemoteViews(mapOf(SizeF(40f, 40f) to small, SizeF(VyliaWidgetProvider.MEDIUM_MIN_WIDTH_DP, 40f) to medium))
        } else {
            val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            if (width >= VyliaWidgetProvider.MEDIUM_MIN_WIDTH_DP) medium else small
        }
    }

    private fun build(context: Context, state: WidgetState, medium: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, if (medium) R.layout.widget_medium else R.layout.widget_small)
        views.setOnClickPendingIntent(R.id.widget_root, activity(context, openApp(context), REQUEST_OPEN))

        if (state.score != null && state.grade != null) {
            views.setTextViewText(R.id.widget_score, state.score.toString())
            views.setTextViewText(R.id.widget_grade, context.getString(R.string.widget_grade, state.grade))
            views.setContentDescription(R.id.widget_score, context.getString(R.string.widget_score_description, state.score, state.grade))
            setColor(context, views, R.id.widget_score, scoreColor(state.rating))
        } else {
            views.setTextViewText(R.id.widget_score, "—")
            views.setTextViewText(R.id.widget_grade, context.getString(R.string.widget_no_score))
            setColor(context, views, R.id.widget_score, R.color.widget_text)
        }
        if (!medium) return views

        views.setTextViewText(R.id.widget_shield, shieldText(context, state))
        val blocked = state.blockedToday.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        // En pause, rien n'est bloqué : la deuxième ligne donne l'heure de reprise, pour que tout tienne sur un 4 × 1.
        val detail = state.pausedUntil?.let { context.getString(R.string.widget_paused_until, DateFormat.getTimeFormat(context).format(Date(it))) }
            ?: context.resources.getQuantityString(R.plurals.widget_blocked_today, blocked, blocked)
        views.setTextViewText(R.id.widget_blocked, detail)
        views.setViewVisibility(R.id.widget_blocked, if (state.shield == WidgetShield.OFF && blocked == 0) View.GONE else View.VISIBLE)
        val label = when (state.action) {
            WidgetAction.PAUSE -> R.string.widget_action_pause
            WidgetAction.RESUME -> R.string.widget_action_resume
            WidgetAction.ACTIVATE -> R.string.widget_action_activate
            WidgetAction.OPEN_APP -> if (state.shield == WidgetShield.OFF) R.string.widget_action_activate else R.string.widget_action_open
            WidgetAction.NONE -> null
        }
        if (label == null) {
            views.setViewVisibility(R.id.widget_button, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_button, View.VISIBLE)
            views.setTextViewText(R.id.widget_button, context.getString(label))
            // Accord VPN ou contournement : il faut un écran, le bouton ouvre directement Filtrer.
            val click = if (state.action == WidgetAction.OPEN_APP) activity(context, openProtect(context), REQUEST_PROTECT)
            else PendingIntent.getBroadcast(
                context, REQUEST_SHIELD,
                Intent(context, WidgetShieldReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_button, click)
        }
        return views
    }

    private fun shieldText(context: Context, state: WidgetState): String = when (state.shield) {
        WidgetShield.ACTIVE -> context.getString(R.string.widget_shield_active)
        WidgetShield.PAUSED -> context.getString(R.string.widget_shield_paused)
        WidgetShield.STARTING -> context.getString(R.string.widget_shield_starting)
        WidgetShield.OFF -> context.getString(R.string.widget_shield_off)
        WidgetShield.BYPASSED -> context.getString(R.string.widget_shield_bypassed)
    }

    private fun scoreColor(rating: ScoreRating?): Int = when (rating) {
        ScoreRating.GOOD -> R.color.widget_score_good
        ScoreRating.FAIR -> R.color.widget_score_fair
        ScoreRating.POOR -> R.color.widget_score_poor
        null -> R.color.widget_text
    }

    /**
     * Couleur d'une ressource : à partir d'Android 12, résolue par l'écran d'accueil, qui suit le thème clair ou sombre
     * du moment ; avant, résolue ici.
     */
    private fun setColor(context: Context, views: RemoteViews, viewId: Int, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setColorStateList(viewId, "setTextColor", color)
        else views.setTextColor(viewId, context.getColor(color))
    }

    private fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun openProtect(context: Context): Intent = openApp(context).putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_PROTECT)

    private fun activity(context: Context, intent: Intent, request: Int): PendingIntent =
        PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private const val REQUEST_OPEN = 1
    private const val REQUEST_PROTECT = 2
    private const val REQUEST_SHIELD = 3

    fun ids(context: Context, manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(context, VyliaWidgetProvider::class.java))
}
