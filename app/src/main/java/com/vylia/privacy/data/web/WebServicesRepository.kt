package com.vylia.privacy.data.web

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import com.vylia.privacy.domain.web.WebServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La liste des services web : celle de l'app (`assets/web_services.json`), ou sa dernière version téléchargée depuis
 * le dépôt VYLIA si elle est lisible et plausible. La liste embarquée illisible est un bug de build.
 */
class WebServicesRepository(private val context: Context) {

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE)

    @Volatile
    private var loaded: WebServices? = null

    val services: WebServices
        get() = loaded ?: load().also { loaded = it }

    val size: Int get() = services.services.size

    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val text = fetch.text(REMOTE_URL, MAX_BYTES) ?: return@withContext Outcome.Unchanged
        val fresh = checked(text)
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(downloaded)) { "installation de la liste impossible" }
        loaded = fresh
        Outcome.Installed(fresh.services.size)
    }

    private fun load(): WebServices {
        if (downloaded.exists()) {
            try {
                return checked(downloaded.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Liste des services web téléchargée illisible, liste de l'app utilisée", e)
            }
        }
        return WebServices.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    private fun checked(text: String): WebServices = WebServices.parse(text).also {
        require(it.services.size >= MIN_SERVICES) { "liste incomplète (${it.services.size} services)" }
    }

    companion object {
        private const val TAG = "WebServicesRepository"
        private const val ASSET = "web_services.json"
        private const val DOWNLOADED_FILE = "downloaded_web_services.json"
        const val REMOTE_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/web_services.json"
        private const val MAX_BYTES = 1L * 1024 * 1024
        private const val MIN_SERVICES = 3
    }
}
