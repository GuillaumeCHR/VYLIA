package com.vylia.privacy.data.automation

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.vylia.privacy.VyliaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Exécute les tâches de [AutomationScheduler] : contrôle des installations, et scan de fond précédé de la mise à jour des bases. */
class AutomationJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as VyliaApplication).container
        running[params.jobId] = scope.launch {
            try {
                when (params.jobId) {
                    AutomationScheduler.INSTALL_JOB -> container.installWatcher.check()
                    AutomationScheduler.SCAN_JOB -> UpdateThenScan.run(
                        update = { container.databaseCenter.updateBeforeScan(UpdateThenScan.network(this@AutomationJobService)) },
                        scan = { container.backgroundScanner.run() },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tâche ${params.jobId} interrompue", e)
            }
            running.remove(params.jobId)
            jobFinished(params, false)
        }
        return true
    }

    /** Android reprend la main (batterie, durée) : la tâche sera relancée plus tard. */
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "VyliaAutomation"
    }
}
