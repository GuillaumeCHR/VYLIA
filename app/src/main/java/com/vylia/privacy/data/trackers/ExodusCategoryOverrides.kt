package com.vylia.privacy.data.trackers

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Catégories ajoutées aux traqueurs qu'Exodus ne catégorise pas (environ un quart), révisées dans le dépôt VYLIA
 * (`exodus_category_overrides.json`, voir tools/README.md). Elles servent au score (poids par catégorie) et au bouclier
 * (seules certaines catégories sont bloquées), pour la base Exodus embarquée comme pour une base téléchargée.
 * Version de l'app, ou dernière version téléchargée depuis le dépôt si elle est lisible et validée.
 */
class ExodusCategoryOverrides(private val context: Context) {

    @Serializable
    private data class FileContent(
        val validated: Boolean = false,
        @kotlinx.serialization.SerialName("last_reviewed") val lastReviewed: String = "",
        val categories: Map<String, List<String>> = emptyMap(),
    )

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE)

    @Volatile
    var categories: Map<String, List<String>> = load()
        private set

    val size: Int get() = categories.size

    /** Télécharge la version du dépôt VYLIA ; elle n'est gardée que si elle est validée et plausible. */
    suspend fun update(fetch: Fetch): Outcome {
        val text = fetch.text(REMOTE_URL, MAX_BYTES) ?: return Outcome.Unchanged
        val parsed = parse(text)
        require(parsed.size >= MIN_ENTRIES) { "liste incomplète (${parsed.size} traqueurs)" }
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(downloaded)) { "installation impossible" }
        categories = parsed
        return Outcome.Installed(parsed.size)
    }

    private fun load(): Map<String, List<String>> {
        if (downloaded.exists()) {
            try {
                parse(downloaded.readText()).takeIf { it.size >= MIN_ENTRIES }?.let { return it }
            } catch (e: Exception) {
                Log.w(TAG, "Catégories téléchargées illisibles, version de l'app utilisée", e)
            }
        }
        return parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    companion object {
        private const val TAG = "ExodusCategories"
        const val ASSET = "exodus_category_overrides.json"
        private const val DOWNLOADED_FILE = "downloaded_exodus_category_overrides.json"
        const val REMOTE_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/exodus_category_overrides.json"
        private const val MAX_BYTES = 512L * 1024
        private const val MIN_ENTRIES = 20
        private val json = Json { ignoreUnknownKeys = true }

        /** Une liste non validée (en cours de révision) ne s'applique pas, comme dans les scripts du dépôt. */
        fun parse(text: String): Map<String, List<String>> {
            val file = json.decodeFromString<FileContent>(text.removePrefix("﻿"))
            if (!file.validated) return emptyMap()
            return file.categories.entries.asSequence().take(5_000).associate { (name, categories) ->
                Untrusted.text(name, 100) to categories.take(10).map { Untrusted.text(it, 50) }
            }
        }
    }
}
