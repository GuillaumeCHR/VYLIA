package com.vylia.privacy.data.alert

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.alert.AlertState
import com.vylia.privacy.domain.alert.ThresholdAlert
import com.vylia.privacy.domain.alert.ThresholdCrossing
import com.vylia.privacy.domain.alert.ThresholdSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Alerte de seuil, 100 % locale : réglage et état dans les préférences, franchissements (50 au plus) dans un fichier
 * JSON. [State.since] : depuis quand le score est sous le seuil (état `TRIGGERED`).
 */
class ThresholdAlertRepository(context: Context) {

    data class State(
        val setting: ThresholdSetting,
        val state: AlertState?,
        val since: Long?,
        val crossings: List<ThresholdCrossing>,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crossingsFile = File(context.filesDir, CROSSINGS_FILE)

    private val _current = MutableStateFlow(load())
    val current: StateFlow<State> = _current.asStateFlow()

    /** Activation, désactivation ou nouveau seuil : l'état est recalculé en silence avec le dernier score connu. */
    @Synchronized
    fun setSetting(setting: ThresholdSetting, score: Int?, now: Long = System.currentTimeMillis()) {
        val clamped = setting.copy(threshold = setting.threshold.coerceIn(0, 100))
        val state = ThresholdAlert.settle(clamped, score)
        save(_current.value.copy(setting = clamped, state = state, since = if (state == AlertState.TRIGGERED) now else null))
    }

    /**
     * Fin d'un scan complet. [crossing] n'est construit que si la notification doit partir (la cause demande un calcul) ;
     * il est alors enregistré et renvoyé à l'appelant, qui notifie.
     */
    @Synchronized
    fun onScan(score: Int, now: Long, crossing: () -> ThresholdCrossing): ThresholdCrossing? {
        val current = _current.value
        val decision = ThresholdAlert.onScan(current.setting, current.state, score)
        val state = decision.state ?: return null
        val since = when {
            state != AlertState.TRIGGERED -> null
            current.state == AlertState.TRIGGERED -> current.since ?: now
            else -> now
        }
        val made = if (decision.notify) crossing() else null
        val crossings = made?.let { ThresholdAlert.append(current.crossings, it) } ?: current.crossings
        save(current.copy(state = state, since = since, crossings = crossings))
        return made
    }

    /** Restauration d'une sauvegarde : franchissements fusionnés par date, 50 au plus. */
    @Synchronized
    fun mergeCrossings(restored: List<ThresholdCrossing>): Int {
        val current = _current.value
        val known = current.crossings.map { it.at }.toSet()
        val added = restored.filter { it.at !in known }
        val merged = added.fold(current.crossings) { list, c -> ThresholdAlert.append(list, c) }
        save(current.copy(crossings = merged))
        return added.size
    }

    @Synchronized
    fun clearCrossings() = save(_current.value.copy(crossings = emptyList()))

    private fun load(): State {
        val setting = ThresholdSetting(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            threshold = prefs.getInt(KEY_THRESHOLD, ThresholdSetting.DEFAULT_THRESHOLD).coerceIn(0, 100),
        )
        val state = prefs.getString(KEY_STATE, null)?.let { name -> AlertState.entries.find { it.name == name } }
        val since = prefs.getLong(KEY_SINCE, 0L).takeIf { it > 0 }
        val crossings = try {
            if (crossingsFile.exists()) json.decodeFromString<List<ThresholdCrossing>>(crossingsFile.readText()) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Franchissements illisibles, ignorés", e)
            emptyList()
        }
        return State(setting, state, since, crossings.takeLast(ThresholdAlert.MAX_CROSSINGS))
    }

    private fun save(state: State) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, state.setting.enabled)
            .putInt(KEY_THRESHOLD, state.setting.threshold)
            .putString(KEY_STATE, state.state?.name)
            .putLong(KEY_SINCE, state.since ?: 0L)
            .apply()
        if (state.crossings != _current.value.crossings || !crossingsFile.exists()) {
            crossingsFile.writeText(json.encodeToString(state.crossings))
        }
        _current.value = state
    }

    private companion object {
        const val TAG = "ThresholdAlert"
        const val PREFS = "threshold_alert"
        const val CROSSINGS_FILE = "threshold_crossings.json"
        const val KEY_ENABLED = "enabled"
        const val KEY_THRESHOLD = "threshold"
        const val KEY_STATE = "state"
        const val KEY_SINCE = "since"
        val json = Json { ignoreUnknownKeys = true }
    }
}
