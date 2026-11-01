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

    /** Supprimer mes données : plus de trace des derniers scans. */
    fun clearScanTimestamps() {
        prefs.edit().remove(KEY_LAST_SCAN).remove(KEY_LAST_BACKGROUND_SCAN).apply()
        _lastScanTimestamp.value = null
        _lastBackgroundScan.value = null
    }

    /** Vrai une fois la langue par défaut (anglais) appliquée au premier lancement. */
    val languageInitialized: Boolean get() = prefs.getBoolean(KEY_LANGUAGE_INITIALIZED, false)

    fun setLanguageInitialized() {
        prefs.edit().putBoolean(KEY_LANGUAGE_INITIALIZED, true).apply()
    }

    fun setAdvancedMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADVANCED_MODE, enabled).apply()
        _advancedMode.value = enabled
    }

    /** Notification à l'installation d'une app (désactivée par défaut). */
    private val _installAlerts = MutableStateFlow(prefs.getBoolean(KEY_INSTALL_ALERTS, false))
    val installAlerts: StateFlow<Boolean> = _installAlerts.asStateFlow()

    fun setInstallAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INSTALL_ALERTS, enabled).apply()
        _installAlerts.value = enabled
    }

    /** Fréquence des scans de fond programmés (désactivés par défaut). */
    private val _backgroundScan = MutableStateFlow(
        BackgroundScan.entries.find { it.name == prefs.getString(KEY_BACKGROUND_SCAN, null) } ?: BackgroundScan.OFF
    )
    val backgroundScan: StateFlow<BackgroundScan> = _backgroundScan.asStateFlow()

    fun setBackgroundScan(frequency: BackgroundScan) {
        prefs.edit().putString(KEY_BACKGROUND_SCAN, frequency.name).apply()
        _backgroundScan.value = frequency
    }

    /** Dernier scan de fond réussi ; `null` s'il n'y en a pas encore eu. */
    private val _lastBackgroundScan = MutableStateFlow(prefs.getLong(KEY_LAST_BACKGROUND_SCAN, 0L).takeIf { it > 0 })
    val lastBackgroundScan: StateFlow<Long?> = _lastBackgroundScan.asStateFlow()

    fun setLastBackgroundScan(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKGROUND_SCAN, timestamp).apply()
        _lastBackgroundScan.value = timestamp
    }

    /**
     * Repère de la surveillance des installations : numéro de séquence des paquets Android (remis à zéro à chaque
     * démarrage, d'où le compteur de démarrages) et heure du dernier contrôle.
     */
    data class InstallCursor(val bootCount: Int, val sequence: Int, val checkedAt: Long)

    var installCursor: InstallCursor?
        get() = if (!prefs.contains(KEY_CURSOR_AT)) null else InstallCursor(
            prefs.getInt(KEY_CURSOR_BOOT, -1),
            prefs.getInt(KEY_CURSOR_SEQUENCE, 0),
            prefs.getLong(KEY_CURSOR_AT, 0L),
        )
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_CURSOR_BOOT); remove(KEY_CURSOR_SEQUENCE); remove(KEY_CURSOR_AT)
                } else {
                    putInt(KEY_CURSOR_BOOT, value.bootCount); putInt(KEY_CURSOR_SEQUENCE, value.sequence); putLong(KEY_CURSOR_AT, value.checkedAt)
                }
            }.apply()
        }

    companion object {
        private const val KEY_INSTALL_ALERTS = "install_alerts"
        private const val KEY_BACKGROUND_SCAN = "background_scan"
        private const val KEY_LAST_BACKGROUND_SCAN = "last_background_scan"
        private const val KEY_CURSOR_BOOT = "install_cursor_boot"
        private const val KEY_CURSOR_SEQUENCE = "install_cursor_sequence"
        private const val KEY_CURSOR_AT = "install_cursor_at"
        private const val KEY_LAST_UPDATE = "last_exodus_update_timestamp"
        private const val KEY_ADVANCED_MODE = "advanced_mode"
        private const val KEY_LAST_SCAN = "last_scan_timestamp"
        private const val KEY_LANGUAGE_INITIALIZED = "language_initialized"
    }
}

/** Fréquence des scans de fond. */
enum class BackgroundScan(val intervalDays: Long) { OFF(0), WEEKLY(7), MONTHLY(30) }
