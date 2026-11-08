package com.vylia.privacy.data.databases

import android.util.Log
import com.vylia.privacy.domain.databases.DatabaseMeta
import com.vylia.privacy.domain.databases.DatabaseMetaStore
import kotlinx.serialization.json.Json
import java.io.File

/** État des bases (`filesDir/databases.json`) : en-têtes, dates, nombre d'entrées, échecs. */
class FileDatabaseMetaStore(private val file: File) : DatabaseMetaStore {

    override fun load(): Map<String, DatabaseMeta> {
        if (!file.exists()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, DatabaseMeta>>(file.readText())
        } catch (e: Exception) {
            // Perdre cet état ne fait que retélécharger les bases une fois.
            Log.w(TAG, "État des bases illisible, repart de zéro", e)
            emptyMap()
        }
    }

    override fun save(meta: Map<String, DatabaseMeta>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(meta))
        if (!tmp.renameTo(file)) tmp.delete()
    }

    private companion object {
        const val TAG = "DatabaseMetaStore"
        val json = Json { ignoreUnknownKeys = true }
    }
}
