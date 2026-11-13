package com.vylia.privacy.data.alternatives

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.act.AlternativeCatalogue
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Catalogue d'alternatives : celui de l'app, ou sa dernière version téléchargée depuis le dépôt VYLIA (Paramètres ›
 * Bases de données) si elle est lisible et plausible. Le catalogue embarqué illisible est un bug de build.
 */
class AlternativesRepository(private val context: Context) {

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE)

    @Volatile
    private var loaded: AlternativeCatalogue? = null

    val catalogue: AlternativeCatalogue
        get() = loaded ?: load().also { loaded = it }

    /** Nombre d'alternatives proposées. */
    val size: Int get() = catalogue.groups.sumOf { it.alternatives.size }

    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val text = fetch.text(REMOTE_URL, MAX_BYTES) ?: return@withContext Outcome.Unchanged
        val fresh = checked(text)
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(downloaded)) { "installation du catalogue impossible" }
        loaded = fresh
        Outcome.Installed(fresh.groups.sumOf { it.alternatives.size })
    }

    private fun load(): AlternativeCatalogue {
        if (downloaded.exists()) {
            try {
                return checked(downloaded.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Catalogue téléchargé illisible, catalogue de l'app utilisé", e)
            }
        }
        return parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    private fun checked(text: String): AlternativeCatalogue = parse(text).also { catalogue ->
        require(catalogue.groups.size >= MIN_GROUPS) { "catalogue incomplet (${catalogue.groups.size} familles)" }
    }

    companion object {
        private const val TAG = "AlternativesRepository"
        const val ASSET = "alternatives.json"
        private const val DOWNLOADED_FILE = "downloaded_alternatives.json"
        const val REMOTE_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/alternatives.json"
        private const val MAX_BYTES = 2L * 1024 * 1024
        private const val MIN_GROUPS = 5
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(content: String): AlternativeCatalogue = json.decodeFromString(content.removePrefix("\uFEFF"))
    }
}
