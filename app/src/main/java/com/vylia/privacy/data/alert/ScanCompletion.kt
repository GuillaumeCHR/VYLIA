package com.vylia.privacy.data.alert

import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.domain.alert.DropCauseAnalyzer
import com.vylia.privacy.domain.alert.ThresholdCrossing
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.usecase.deviceScore
import com.vylia.privacy.domain.usecase.historyPoint

/** Envoie la notification d'un franchissement de seuil. */
fun interface ThresholdNotifier {
    fun notify(crossing: ThresholdCrossing)
}

/**
 * Fin d'un scan complet, quelle que soit son origine (écran Scan, scan de fond) : un point au Suivi, l'alerte
 * stalkerware, puis l'alerte de seuil. La cause d'une baisse se mesure contre le dernier point du Suivi, lu avant
 * d'enregistrer le nouveau.
 */
class ScanCompletion(
    private val history: HistoryRecorder,
    private val alerts: ThresholdAlertRepository,
    private val notifier: ThresholdNotifier,
    private val stalkerware: StalkerwareAlerts,
    private val rules: () -> RuleMatrixRepository.LoadedRules,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [background] : scan de fond ; sinon l'écran Scan est ouvert et montre déjà une alerte stalkerware. */
    @Synchronized
    fun onFullScan(apps: List<AuditedApp>, background: Boolean) {
        val userApps = apps.filter { AppFilter.USER.matches(it.app) }
        if (userApps.isEmpty()) return
        stalkerware.check(userApps, full = true, notify = background)
        val loaded = rules()
        val previous = history.history.value.snapshots.lastOrNull()
        history.record(userApps.associate { it.app.packageName to it.historyPoint() }, loaded.fingerprint)
        val score = userApps.deviceScore()?.value ?: return
        val now = clock()
        alerts.onScan(score, now) {
            val cause = DropCauseAnalyzer.analyze(previous, userApps, loaded.fingerprint, loaded.scorer)
            ThresholdCrossing(now, score, alerts.current.value.setting.threshold, cause)
        }?.let(notifier::notify)
    }
}
