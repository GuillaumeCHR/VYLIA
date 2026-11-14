package com.vylia.privacy.data.preinstalled

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import com.vylia.privacy.domain.preinstalled.PreinstalledDatabase
import com.vylia.privacy.domain.preinstalled.UadList
import com.vylia.privacy.domain.preinstalled.UadTranslations
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
    private val downloadedTranslations = File(context.filesDir, DOWNLOADED_TRANSLATIONS)
    private val mutex = Mutex()
    private val _current = MutableStateFlow<State?>(null)

    /** `null` tant que la liste n'a pas été lue. */
    val current: StateFlow<State?> = _current.asStateFlow()

    /** Nombre de paquets de la liste en service, `null` si elle n'a pas encore été lue. */
    val size: Int? get() = _current.value?.database?.size

    /** Nombre de descriptions traduites en service, `null` si la liste n'a pas encore été lue. */
    val translationCount: Int? get() = _current.value?.database?.translations?.size

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
        val inService = load().database
        require(UadList.acceptable(fresh.size, inService.size)) { "liste incomplète (${fresh.size} paquets)" }
        install(text, downloaded)
        _current.value = State(fresh.withTranslations(inService.translations), downloaded.lastModified())
        Outcome.Installed(fresh.size)
    }

    /**
     * Télécharge les traductions des descriptions, publiées dans le dépôt VYLIA. Refusées si elles sont d'une autre
     * langue, trop peu nombreuses, ou en perdent plus de 10 % : les traductions actuelles restent en place.
     */
    suspend fun updateTranslations(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val text = fetch.text(TRANSLATIONS_URL, MAX_TRANSLATION_BYTES, conditional = downloadedTranslations.exists())
            ?: return@withContext Outcome.Unchanged
        val fresh = UadTranslations.parse(text)
        val inService = load()
        val current = inService.database.translations.size
        require(fresh.language == TRANSLATION_LANGUAGE) { "langue inattendue (${fresh.language})" }
        require(fresh.size >= MIN_TRANSLATIONS && fresh.size * 10L >= current * 9L) { "traductions incomplètes (${fresh.size})" }
        install(text, downloadedTranslations)
        _current.value = inService.copy(database = inService.database.withTranslations(fresh))
        Outcome.Installed(fresh.size)
    }

    private fun install(text: String, target: File) {
        val tmp = File(context.filesDir, "${target.name}.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(target)) { "installation de ${target.name} impossible" }
    }

    private fun read(): State {
        val translations = readTranslations()
        if (downloaded.exists()) {
            try {
                val database = UadList.parse(downloaded.readText())
                if (database.size >= UadList.MIN_ENTRIES) return State(database.withTranslations(translations), downloaded.lastModified())
            } catch (e: Exception) {
                Log.w(TAG, "Liste UAD-ng téléchargée illisible, retour à la liste embarquée", e)
            }
        }
        val embedded = UadList.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
        return State(embedded.withTranslations(translations), null)
    }

    /** Traductions téléchargées si elles sont lisibles, sinon celles embarquées ; aucune si l'asset manque. */
    private fun readTranslations(): UadTranslations {
        if (downloadedTranslations.exists()) {
            try {
                val translations = UadTranslations.parse(downloadedTranslations.readText())
                if (translations.language == TRANSLATION_LANGUAGE && translations.size >= MIN_TRANSLATIONS) return translations
            } catch (e: Exception) {
                Log.w(TAG, "Traductions UAD-ng téléchargées illisibles, retour à celles embarquées", e)
            }
        }
        return try {
            UadTranslations.parse(context.assets.open(TRANSLATIONS_ASSET).bufferedReader().use { it.readText() })
        } catch (e: java.io.FileNotFoundException) {
            UadTranslations.EMPTY
        }
    }

    companion object {
        private const val TAG = "PreinstalledRepository"
        const val ASSET = "uad_lists.json"
        const val UPDATE_URL =
            "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
        private const val DOWNLOADED_FILE_NAME = "downloaded_uad_lists.json"
        /** Le fichier fait environ 1,6 Mo ; au-delà de 8 Mo, ce n'est pas lui. */
        private const val MAX_BYTES = 8L * 1024 * 1024
        const val TRANSLATIONS_ASSET = "uad_descriptions_fr.json"
        const val TRANSLATIONS_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/uad_descriptions_fr.json"
        private const val DOWNLOADED_TRANSLATIONS = "downloaded_uad_descriptions_fr.json"
        private const val TRANSLATION_LANGUAGE = "fr"
        private const val MAX_TRANSLATION_BYTES = 8L * 1024 * 1024
        /** Environ 4 900 descriptions traduites : beaucoup moins, c'est un fichier tronqué. */
        private const val MIN_TRANSLATIONS = 3_000
    }
}
