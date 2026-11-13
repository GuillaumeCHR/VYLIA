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
    /** Avant l'audit : attendre ce dont le score dépend (couverture des traqueurs par le bouclier). */
    private val beforeScan: suspend () -> Unit = {},
) {
    suspend fun run() {
        beforeScan()
        val apps = fullAudit()
        if (apps.isEmpty()) return
        completion.onFullScan(apps, background = true)
        val now = clock()
        settings.setLastScan(now)
        settings.setLastBackgroundScan(now)
    }
}
