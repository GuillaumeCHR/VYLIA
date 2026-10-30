package com.vipacy.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vipacy_settings", Context.MODE_PRIVATE)

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

    fun updateTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
        _lastUpdateTimestamp.value = timestamp
    }

    companion object {
        private const val KEY_LAST_UPDATE = "last_exodus_update_timestamp"
    }
}
