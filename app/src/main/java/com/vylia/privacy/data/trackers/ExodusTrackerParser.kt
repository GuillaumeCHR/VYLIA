package com.vylia.privacy.data.trackers

import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerSignature
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lit le format de l'API Exodus Privacy (`/api/trackers`) :
 * `{"trackers": {"<id>": {"id": …, "name": …, "code_signature": "com.foo.|com.bar.", …}}}`.
 *
 * Les traqueurs sans signature de code, ou dont la regex est invalide, sont ignorés.
 */
class ExodusTrackerParser {

    @Serializable
    private data class ExodusFile(val trackers: Map<String, ExodusTracker> = emptyMap())

    @Serializable
    private data class ExodusTracker(
        val id: Int,
        val name: String,
        @SerialName("code_signature") val codeSignature: String = "",
        val website: String? = null,
        val categories: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): List<TrackerSignature> =
        json.decodeFromString<ExodusFile>(content).trackers.values
            .filter { it.codeSignature.isNotBlank() }
            .mapNotNull { tracker ->
                val regex = runCatching { Regex(tracker.codeSignature) }.getOrNull() ?: return@mapNotNull null
                TrackerSignature(
                    tracker = Tracker(tracker.id, tracker.name, tracker.website?.ifBlank { null }, tracker.categories),
                    codeSignature = regex,
                )
            }
            .sortedBy { it.tracker.id }
}
