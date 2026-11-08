package com.vylia.privacy.data.safety

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import com.vylia.privacy.domain.safety.SupportResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exemples de ressources d'aide en cas de surveillance (`support_resources.json`) : ceux de l'app, ou la dernière
 * version téléchargée depuis le dépôt VYLIA si elle est lisible. Un lien mort ou un numéro changé se corrige ainsi sans
 * nouvelle version de l'app. Rien n'est téléchargé à l'ouverture de l'écran d'aide.
 */
class SupportResourcesRepository(private val context: Context) {

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE)

    @Volatile
    private var loaded: SupportResources? = null

    val resources: SupportResources
        get() = loaded ?: load().also { loaded = it }

    val size: Int get() = resources.size

    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val text = fetch.text(REMOTE_URL, SupportResources.MAX_BYTES.toLong()) ?: return@withContext Outcome.Unchanged
        val fresh = checked(text)
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(downloaded)) { "installation impossible" }
        loaded = fresh
        Outcome.Installed(fresh.size)
    }

    private fun load(): SupportResources {
        if (downloaded.exists()) {
            try {
                return checked(downloaded.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Ressources d'aide téléchargées illisibles, version de l'app utilisée", e)
            }
        }
        return SupportResources.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    private fun checked(text: String): SupportResources = SupportResources.parse(text).also {
        require(it.size >= MIN_RESOURCES) { "fichier incomplet (${it.size} ressources)" }
    }

    companion object {
        private const val TAG = "SupportResources"
        const val ASSET = "support_resources.json"
        private const val DOWNLOADED_FILE = "downloaded_support_resources.json"
        const val REMOTE_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/support_resources.json"
        private const val MIN_RESOURCES = 1
    }
}
