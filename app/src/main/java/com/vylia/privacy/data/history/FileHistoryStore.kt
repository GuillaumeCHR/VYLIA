package com.vylia.privacy.data.history

import android.util.Log
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryStore
import kotlinx.serialization.json.Json
import java.io.File

/** Suivi stocké en JSON dans les fichiers privés de l'app (exclus des sauvegardes Android). */
class FileHistoryStore(private val file: File) : HistoryStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): History? = try {
        if (file.exists()) json.decodeFromString<History>(file.readText()) else null
    } catch (e: Exception) {
        Log.w(TAG, "Suivi illisible, repart de zéro", e)
        null
    }

    override fun save(history: History) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(history))
        if (!tmp.renameTo(file)) Log.w(TAG, "Écriture du suivi impossible")
    }

    private companion object {
        const val TAG = "FileHistoryStore"
    }
}
