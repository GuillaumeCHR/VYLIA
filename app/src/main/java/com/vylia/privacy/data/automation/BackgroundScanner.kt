package com.vylia.privacy.data.automation

import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.usecase.FullAuditUseCase
import com.vylia.privacy.domain.usecase.historyPoint

/** Scan de fond programmé : audit complet des apps utilisateur, qui ajoute un point au Suivi. */
class BackgroundScanner(
    private val settings: SettingsRepository,
    private val fullAudit: FullAuditUseCase,
    private val history: HistoryRecorder,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run() {
        val apps = fullAudit()
        if (apps.isEmpty()) return
        history.record(apps.associate { it.app.packageName to it.historyPoint() })
        val now = clock()
        settings.setLastScan(now)
        settings.setLastBackgroundScan(now)
    }
}
