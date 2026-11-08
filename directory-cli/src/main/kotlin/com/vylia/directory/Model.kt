package com.vylia.directory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Une app de la liste maintenue à la main (`directory/apps.json`), revue chaque trimestre. */
@Serializable
data class AppEntry(
    @SerialName("package") val packageName: String,
    val name: String,
    val category: String,
    val countries: List<String> = emptyList(),
)

@Serializable
data class AppList(val apps: List<AppEntry>)

/** Un traqueur Exodus : nom et catégories (complétées par les catégories ajoutées par VYLIA). */
data class ExodusTracker(val id: Int, val name: String, val categories: List<String>)

/** Le dernier rapport Exodus d'une app (route `/api/search/{handle}/details`). */
data class ExodusReport(
    val report: Long,
    val versionName: String,
    val versionCode: String,
    val updated: String,
    val trackerIds: List<Int>,
    val permissions: List<String>,
)

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = false }
