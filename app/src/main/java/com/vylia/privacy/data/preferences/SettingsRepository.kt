package com.vylia.privacy.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vylia_settings", Context.MODE_PRIVATE)

    private val _lastUpdateTimestamp = MutableStateFlow(
        prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis())
    )
    val lastUpdateTimestamp: StateFlow<Long> = _lastUpdateTimestamp.asStateFlow()

    init {
        // Init if not set
        if (!prefs.contains(KEY_LAST_UPDATE)) {
            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        }
    }

    /** Mode Avancé : apps système incluses. Mode Basique (défaut) : seules les apps utilisateur sont notées. */
    private val _advancedMode = MutableStateFlow(prefs.getBoolean(KEY_ADVANCED_MODE, false))
    val advancedMode: StateFlow<Boolean> = _advancedMode.asStateFlow()

    /** Fin du dernier scan complet (traqueurs compris) ; `null` avant le tout premier. */
    private val _lastScanTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_SCAN, 0L).takeIf { it > 0 })
    val lastScanTimestamp: StateFlow<Long?> = _lastScanTimestamp.asStateFlow()

    fun setLastScan(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN, timestamp).apply()
        _lastScanTimestamp.value = timestamp
    }

    fun updateTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
        _lastUpdateTimestamp.value = timestamp
    }

    fun setAdvancedMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADVANCED_MODE, enabled).apply()
        _advancedMode.value = enabled
    }

    companion object {
        private const val KEY_LAST_UPDATE = "last_exodus_update_timestamp"
        private const val KEY_ADVANCED_MODE = "advanced_mode"
        private const val KEY_LAST_SCAN = "last_scan_timestamp"
    }
}
