package com.vylia.privacy.data.trackers

import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerSignature
import com.vylia.privacy.domain.security.Untrusted
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
        @SerialName("network_signature") val networkSignature: String = "",
    )

    /** Traqueur Exodus et ses domaines (`network_signature`), pour le bouclier. */
    data class NetworkTracker(val name: String, val categories: List<String>, val networkSignature: String)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * La base peut être téléchargée : elle est traitée comme hostile. Seules les signatures faites de noms de paquets
     * séparés par « | » sont gardées (c'est le cas de toute la base Exodus) : une expression régulière piégée
     * (quantificateurs imbriqués) pourrait sinon bloquer l'analyse de chaque app. Textes et listes sont bornés.
     */
    fun parse(content: String, overrides: Map<String, List<String>> = emptyMap()): List<TrackerSignature> =
        json.decodeFromString<ExodusFile>(content.removePrefix("\uFEFF")).trackers.values
            .asSequence()
            .take(MAX_TRACKERS)
            .filter { SIGNATURE.matches(it.codeSignature) && it.codeSignature.length <= MAX_SIGNATURE }
            .mapNotNull { tracker ->
                val regex = runCatching { Regex(tracker.codeSignature) }.getOrNull() ?: return@mapNotNull null
                val website = tracker.website?.takeIf { it.length <= 300 && it.startsWith("https://") }
                TrackerSignature(
                    tracker = Tracker(
                        tracker.id,
                        Untrusted.text(tracker.name, 100),
                        website,
                        categoriesOf(tracker, overrides),
                    ),
                    codeSignature = regex,
                )
            }
            .toList()
            .sortedBy { it.tracker.id }

    /**
     * Les traqueurs et leurs domaines, pour le bouclier ; les catégories manquantes sont complétées comme pour le score.
     * Les signatures sont des textes bornés, réduites à des domaines par le bouclier (le reste est écarté).
     */
    fun parseNetwork(content: String, overrides: Map<String, List<String>> = emptyMap()): List<NetworkTracker> =
        json.decodeFromString<ExodusFile>(content.removePrefix("\uFEFF")).trackers.values
            .asSequence()
            .take(MAX_TRACKERS)
            .filter { it.networkSignature.isNotEmpty() && it.networkSignature.length <= MAX_SIGNATURE }
            .sortedBy { it.id }
            .map { NetworkTracker(Untrusted.text(it.name, 100), categoriesOf(it, overrides), it.networkSignature) }
            .toList()

    /** Catégories d'Exodus, sinon celles ajoutées dans le dépôt VYLIA (exodus_category_overrides.json). */
    private fun categoriesOf(tracker: ExodusTracker, overrides: Map<String, List<String>>): List<String> =
        tracker.categories.ifEmpty { overrides[tracker.name].orEmpty() }.take(10).map { Untrusted.text(it, 50) }

    companion object {
        /** Noms de paquets (ou débuts de noms) séparés par « | ». */
        private val SIGNATURE = Regex("[A-Za-z0-9_.|-]+")
        private const val MAX_SIGNATURE = 2_000
        private const val MAX_TRACKERS = 5_000
    }
}
