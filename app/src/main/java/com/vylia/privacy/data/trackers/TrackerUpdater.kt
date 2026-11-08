package com.vylia.privacy.data.trackers

import android.content.Context
import com.vylia.privacy.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import java.io.File

class TrackerUpdater(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val UPDATE_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
        const val DOWNLOADED_FILE_NAME = "downloaded_trackers.json"
        /** La base fait environ 300 Ko ; au-delà de 5 Mo, ce n'est pas elle. */
        const val MAX_BYTES = 5L * 1024 * 1024
        /** Exodus recense plus de 400 traqueurs : une base beaucoup plus petite est tronquée ou falsifiée. */
        const val MIN_TRACKERS = 100
        const val OBSOLESCENCE_THRESHOLD_MS = 90L * 24L * 60L * 60L * 1000L // 90 days
    }

    /**
     * Télécharge la base publique d'Exodus Privacy. Elle n'est installée qu'après contrôle : HTTPS imposé, taille
     * bornée, contenu lisible et plausible (assez de traqueurs valides). Sinon la base actuelle reste en place.
     * Renvoie le nombre de traqueurs.
     */
    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val file = fetch.get(UPDATE_URL, MAX_BYTES) ?: return@withContext Outcome.Unchanged
        val signatures = ExodusTrackerParser().parse(file.readText())
        require(signatures.size >= MIN_TRACKERS) { "base incomplète (${signatures.size} traqueurs)" }
        val target = File(context.filesDir, DOWNLOADED_FILE_NAME)
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE_NAME.tmp")
        try {
            file.copyTo(tmp, overwrite = true)
            check(tmp.renameTo(target)) { "installation de la base impossible" }
        } finally {
            tmp.delete()
        }
        settingsRepository.updateTimestamp(System.currentTimeMillis())
        Outcome.Installed(signatures.size)
    }

    fun isDatabaseObsolete(currentTimestamp: Long): Boolean {
        return (System.currentTimeMillis() - currentTimestamp) > OBSOLESCENCE_THRESHOLD_MS
    }
}
