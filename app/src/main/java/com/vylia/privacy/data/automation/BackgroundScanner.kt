package com.vylia.privacy.data.automation

import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.alert.ScanCompletion
import com.vylia.privacy.domain.usecase.FullAuditUseCase

/** Scan de fond programmé : audit complet des apps utilisateur, qui ajoute un point au Suivi et évalue l'alerte de seuil. */
class BackgroundScanner(
    private val settings: SettingsRepository,
    private val fullAudit: FullAuditUseCase,
    private val completion: ScanCompletion,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run() {
        val apps = fullAudit()
        if (apps.isEmpty()) return
        completion.onFullScan(apps, background = true)
        val now = clock()
        settings.setLastScan(now)
        settings.setLastBackgroundScan(now)
    }
}
