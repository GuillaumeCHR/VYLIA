package com.vylia.privacy.data.trackers

import android.content.Context
import com.vylia.privacy.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import java.net.URL

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
     */
    suspend fun downloadUpdate(): Result<Unit> = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE_NAME.tmp")
        try {
            val connection = URL(UPDATE_URL).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            // Pas de redirection : la base ne peut venir que de l'adresse officielle.
            connection.instanceFollowRedirects = false
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("HTTP error: ${connection.responseCode}"))
                }
                val declared = connection.contentLengthLong
                require(declared <= MAX_BYTES) { "base trop volumineuse" }
                connection.inputStream.use { input -> tmp.outputStream().use { output -> copyBounded(input, output) } }
            } finally {
                connection.disconnect()
            }
            val signatures = ExodusTrackerParser().parse(tmp.readText())
            require(signatures.size >= MIN_TRACKERS) { "base incomplète (${signatures.size} traqueurs)" }
            check(tmp.renameTo(File(context.filesDir, DOWNLOADED_FILE_NAME))) { "installation de la base impossible" }
            settingsRepository.updateTimestamp(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(e)
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= MAX_BYTES) { "base trop volumineuse" }
            output.write(buffer, 0, n)
        }
    }

    fun isDatabaseObsolete(currentTimestamp: Long): Boolean {
        return (System.currentTimeMillis() - currentTimestamp) > OBSOLESCENCE_THRESHOLD_MS
    }
}
