package com.vylia.privacy.data.alert

import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.domain.model.AuditedApp

/** Envoie la notification « stalkerware connu détecté » pour une app. */
fun interface StalkerwareNotifier {
    fun notify(app: AuditedApp)
}

/**
 * Alerte stalkerware (Paramètres › Automatisations, désactivée par défaut) : une notification par app reconnue dans la
 * base d'Echap, une seule fois. Une app qui n'est plus détectée (désinstallée) est oubliée : réinstallée, elle sera de
 * nouveau signalée.
 */
class StalkerwareAlerts(private val settings: SettingsRepository, private val notifier: StalkerwareNotifier) {

    /**
     * [full] : la liste couvre toutes les apps (scan complet), sinon seulement des apps nouvelles (contrôle des
     * installations). [notify] : `false` quand l'écran Scan est ouvert, l'alerte rouge y est déjà visible ; l'app est
     * alors notée comme signalée.
     */
    @Synchronized
    fun check(apps: List<AuditedApp>, full: Boolean, notify: Boolean) {
        if (!settings.stalkerwareAlerts.value) return
        val detected = apps.filter { it.result.knownStalkerware != null }
        val notified = settings.notifiedStalkerware
        val fresh = newDetections(detected.map { it.app.packageName }.toSet(), notified)
        if (notify) detected.filter { it.app.packageName in fresh }.forEach(notifier::notify)
        settings.notifiedStalkerware = remembered(notified, detected.map { it.app.packageName }.toSet(), full)
    }

    companion object {
        fun newDetections(detected: Set<String>, notified: Set<String>): Set<String> = detected - notified

        /** Après un scan complet, seules les apps encore détectées restent ; un contrôle partiel ne fait qu'ajouter. */
        fun remembered(notified: Set<String>, detected: Set<String>, full: Boolean): Set<String> =
            if (full) detected else notified + detected
    }
}
