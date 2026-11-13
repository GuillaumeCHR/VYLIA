package com.vylia.privacy.data.trackers

import android.content.Context
import com.vylia.privacy.domain.model.TrackerSignature
import com.vylia.privacy.domain.repository.TrackerSignatureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Charge la base Exodus en privilégiant la version téléchargée, puis le snapshot `assets/` ;
 * signatures et automate de recherche sont construits une seule fois.
 */
class AssetTrackerSignatureRepository(
    private val context: Context,
    private val parser: ExodusTrackerParser = ExodusTrackerParser(),
    private val fileName: String = EXODUS_TRACKERS_ASSET,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Catégories ajoutées aux traqueurs qu'Exodus ne catégorise pas (une base téléchargée en a besoin). */
    private val overrides: () -> Map<String, List<String>> = { emptyMap() },
) : TrackerSignatureRepository {

    private class Loaded(val signatures: List<TrackerSignature>, val matcher: TrackerMatcher)

    private val mutex = Mutex()
    private var cached: Loaded? = null

    override suspend fun getSignatures(): List<TrackerSignature> = load().signatures

    override suspend fun getMatcher(): TrackerMatcher = load().matcher

    private suspend fun load(): Loaded = mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val downloadedFile = File(context.filesDir, TrackerUpdater.DOWNLOADED_FILE_NAME)
            val jsonContent = if (downloadedFile.exists()) {
                downloadedFile.readText()
            } else {
                context.assets.open(fileName).bufferedReader().use { it.readText() }
            }
            val signatures = parser.parse(jsonContent, overrides())
            Loaded(signatures, TrackerMatcher(signatures))
        }.also { cached = it }
    }

    fun invalidateCache() {
        cached = null
    }

    /** Version en service : la base téléchargée (sa date) ou celle de l'app. */
    fun version(): String {
        val downloadedFile = File(context.filesDir, TrackerUpdater.DOWNLOADED_FILE_NAME)
        return if (downloadedFile.exists()) "dl-${downloadedFile.lastModified()}" else "app"
    }

    /** Traqueurs Exodus et leurs domaines, depuis la base téléchargée ; `null` sans elle (le bouclier garde sa liste). */
    fun networkTrackers(): List<ExodusTrackerParser.NetworkTracker>? {
        val downloadedFile = File(context.filesDir, TrackerUpdater.DOWNLOADED_FILE_NAME)
        if (!downloadedFile.exists()) return null
        return runCatching { parser.parseNetwork(downloadedFile.readText(), overrides()) }.getOrNull()
    }

    /**
     * Traqueurs Exodus et leurs domaines, depuis la base téléchargée, sinon depuis celle de l'app : sert à dire ce que
     * le bouclier bloque de chaque traqueur (fiche d'une app). Appel bloquant.
     */
    fun networkTrackersOrBundled(): List<ExodusTrackerParser.NetworkTracker>? = networkTrackers()
        ?: runCatching { parser.parseNetwork(context.assets.open(fileName).bufferedReader().use { it.readText() }, overrides()) }.getOrNull()

    companion object {
        /** Snapshot allégé, rafraîchi par tools/update_exodus_trackers.py (données Exodus Privacy, ODbL). */
        const val EXODUS_TRACKERS_ASSET = "exodus_trackers.json"
    }
}
