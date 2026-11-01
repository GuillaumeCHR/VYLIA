package com.vylia.privacy.data.trackers

import android.util.Log
import com.vylia.privacy.domain.model.InstalledApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Résultats d'analyse DEX persistés entre deux lancements, par `packageName + versionCode + lastUpdateTime`.
 *
 * Fichier JSON privé (le projet n'utilise ni Room ni DataStore ; inutile d'ajouter une dépendance pour
 * quelques centaines d'entrées). Tout est invalidé quand la base de signatures change ([TrackerMatcher.version]).
 * Seules les analyses réussies sont mémorisées : un échec sera retenté au scan suivant.
 */
class TrackerScanCache(private val file: File) {

    @Serializable
    private data class Content(val signatureVersion: String = "", val entries: Map<String, List<Int>> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var content: Content? = null

    suspend fun get(app: InstalledApp, signatureVersion: String): List<Int>? = mutex.withLock {
        loaded(signatureVersion).entries[key(app)]
    }

    suspend fun put(app: InstalledApp, signatureVersion: String, trackerIds: List<Int>) = mutex.withLock {
        val current = loaded(signatureVersion)
        // Une seule entrée par paquet : l'ancienne version de l'app est oubliée.
        val prefix = app.packageName + SEPARATOR
        val entries = current.entries.filterKeys { !it.startsWith(prefix) } + (key(app) to trackerIds)
        content = current.copy(entries = entries).also(::save)
    }

    /** Oublie toutes les analyses : la liste de vos apps n'est plus conservée (Supprimer mes données). */
    suspend fun clear() = mutex.withLock {
        content = null
        file.delete()
    }

    private fun loaded(signatureVersion: String): Content {
        val current = content ?: read()
        return (if (current.signatureVersion == signatureVersion) current else Content(signatureVersion)).also { content = it }
    }

    private fun read(): Content = try {
        if (file.exists()) json.decodeFromString<Content>(file.readText()) else Content()
    } catch (e: Exception) {
        Log.w(TAG, "Cache d'analyse illisible, ignoré", e)
        Content()
    }

    /** Écriture atomique : un arrêt brutal ne laisse jamais un fichier tronqué. */
    private fun save(content: Content) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(content))
        if (!tmp.renameTo(file)) Log.w(TAG, "Écriture du cache d'analyse impossible")
    }

    private fun key(app: InstalledApp) = "${app.packageName}$SEPARATOR${app.versionCode}$SEPARATOR${app.lastUpdateTime}"

    private companion object {
        const val TAG = "TrackerScanCache"
        const val SEPARATOR = '|'
    }
}
