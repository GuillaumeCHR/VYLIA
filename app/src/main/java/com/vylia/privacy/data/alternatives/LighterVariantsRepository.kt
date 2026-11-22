package com.vylia.privacy.data.alternatives

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.act.LighterVariants
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Les versions allégées : celles de l'app (`assets/lighter_variants.json`), ou leur dernière version téléchargée depuis
 * le dépôt VYLIA (Paramètres › Bases de données) si elle est lisible. La liste embarquée illisible est un bug de build.
 */
class LighterVariantsRepository(private val context: Context) {

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE)

    @Volatile
    private var loaded: LighterVariants? = null

    val variants: LighterVariants
        get() = loaded ?: load().also { loaded = it }

    val size: Int get() = variants.variants.size

    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val text = fetch.text(REMOTE_URL, MAX_BYTES) ?: return@withContext Outcome.Unchanged
        val fresh = LighterVariants.parse(text)
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(downloaded)) { "installation de la liste impossible" }
        loaded = fresh
        Outcome.Installed(fresh.variants.size)
    }

    private fun load(): LighterVariants {
        if (downloaded.exists()) {
            try {
                return LighterVariants.parse(downloaded.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Versions allégées téléchargées illisibles, liste de l'app utilisée", e)
            }
        }
        return LighterVariants.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    companion object {
        private const val TAG = "LighterVariantsRepo"
        private const val ASSET = "lighter_variants.json"
        private const val DOWNLOADED_FILE = "downloaded_lighter_variants.json"
        const val REMOTE_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/lighter_variants.json"
        private const val MAX_BYTES = 512L * 1024
    }
}
