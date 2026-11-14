package com.vylia.privacy.data.preinstalled

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import com.vylia.privacy.domain.preinstalled.PreinstalledDatabase
import com.vylia.privacy.domain.preinstalled.UadList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Liste des apps préinstallées d'UAD-ng (GPL v3) : la dernière téléchargée si elle est lisible, sinon celle embarquée.
 * Le fichier fait 1,6 Mo : il est lu à la première demande ([load]), hors du fil principal, et seulement en mode Avancé.
 */
class PreinstalledRepository(private val context: Context) {

    /** [downloadedAt] : date du téléchargement, `null` pour la liste embarquée. */
    data class State(val database: PreinstalledDatabase, val downloadedAt: Long?)

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE_NAME)
    private val mutex = Mutex()
    private val _current = MutableStateFlow<State?>(null)

    /** `null` tant que la liste n'a pas été lue. */
    val current: StateFlow<State?> = _current.asStateFlow()

    /** Nombre de paquets de la liste en service, `null` si elle n'a pas encore été lue. */
    val size: Int? get() = _current.value?.database?.size

    suspend fun load(): State = _current.value ?: mutex.withLock {
        _current.value ?: withContext(Dispatchers.IO) { read() }.also { _current.value = it }
    }

    /**
     * Télécharge la liste d'UAD-ng. Elle n'est installée qu'après contrôle : assez d'entrées valides, et pas plus de
     * 10 % de moins que la liste en service (une liste vidée ou tronquée ne remplace pas la bonne).
     */
    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val text = fetch.text(UPDATE_URL, MAX_BYTES, conditional = downloaded.exists()) ?: return@withContext Outcome.Unchanged
        val fresh = UadList.parse(text)
        val inService = load().database.size
        require(UadList.acceptable(fresh.size, inService)) { "liste incomplète (${fresh.size} paquets)" }
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE_NAME.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(downloaded)) { "installation de la liste impossible" }
        _current.value = State(fresh, downloaded.lastModified())
        Outcome.Installed(fresh.size)
    }

    private fun read(): State {
        if (downloaded.exists()) {
            try {
                val database = UadList.parse(downloaded.readText())
                if (database.size >= UadList.MIN_ENTRIES) return State(database, downloaded.lastModified())
            } catch (e: Exception) {
                Log.w(TAG, "Liste UAD-ng téléchargée illisible, retour à la liste embarquée", e)
            }
        }
        return State(UadList.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }), null)
    }

    companion object {
        private const val TAG = "PreinstalledRepository"
        const val ASSET = "uad_lists.json"
        const val UPDATE_URL =
            "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
        private const val DOWNLOADED_FILE_NAME = "downloaded_uad_lists.json"
        /** Le fichier fait environ 1,6 Mo ; au-delà de 8 Mo, ce n'est pas lui. */
        private const val MAX_BYTES = 8L * 1024 * 1024
    }
}
