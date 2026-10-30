package com.vipacy.app.data.trackers

import android.content.Context
import com.vipacy.app.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TrackerUpdater(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val UPDATE_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
        const val DOWNLOADED_FILE_NAME = "downloaded_trackers.json"
        const val OBSOLESCENCE_THRESHOLD_MS = 90L * 24L * 60L * 60L * 1000L // 90 days
    }

    suspend fun downloadUpdate(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val file = File(context.filesDir, DOWNLOADED_FILE_NAME)
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                settingsRepository.updateTimestamp(System.currentTimeMillis())
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isDatabaseObsolete(currentTimestamp: Long): Boolean {
        return (System.currentTimeMillis() - currentTimestamp) > OBSOLESCENCE_THRESHOLD_MS
    }
}
