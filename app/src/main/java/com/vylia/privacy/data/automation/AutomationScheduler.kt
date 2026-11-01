package com.vylia.privacy.data.automation

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.vylia.privacy.data.preferences.BackgroundScan
import com.vylia.privacy.data.preferences.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Tâches de fond, confiées au planificateur d'Android (JobScheduler, sans bibliothèque) : il les regroupe avec
 * celles des autres apps pour ménager la batterie, et les reprogramme après un redémarrage.
 */
class AutomationScheduler(private val context: Context, private val settings: SettingsRepository) {
    private val jobs = context.getSystemService(JobScheduler::class.java)

    /** Aligne les tâches programmées sur les réglages. Sans effet sur une tâche déjà programmée à la bonne fréquence. */
    fun sync() {
        ensure(INSTALL_JOB, if (settings.installAlerts.value) INSTALL_CHECK_INTERVAL else null, batteryNotLow = false)
        val scan = settings.backgroundScan.value
        ensure(SCAN_JOB, if (scan == BackgroundScan.OFF) null else TimeUnit.DAYS.toMillis(scan.intervalDays), batteryNotLow = true)
    }

    private fun ensure(id: Int, interval: Long?, batteryNotLow: Boolean) {
        val pending = jobs.getPendingJob(id)
        if (interval == null) {
            if (pending != null) jobs.cancel(id)
            return
        }
        // Reprogrammer remettrait le compteur à zéro : un scan mensuel n'aurait jamais lieu si l'app est ouverte souvent.
        if (pending?.intervalMillis == interval) return
        val job = JobInfo.Builder(id, ComponentName(context, AutomationJobService::class.java))
            .setPeriodic(interval)
            .setPersisted(true)
            .setRequiresBatteryNotLow(batteryNotLow)
            .build()
        jobs.schedule(job)
    }

    companion object {
        const val INSTALL_JOB = 1
        const val SCAN_JOB = 2

        /** Le plus court intervalle qu'Android accorde à une tâche périodique. */
        val INSTALL_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(15)
    }
}
