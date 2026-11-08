package com.vylia.privacy.domain.safety

import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Qui pourrait être à l'origine d'une surveillance : le parcours d'aide en dépend. Le choix de l'utilisateur n'est
 * jamais enregistré.
 */
enum class SurveillancePath {
    /** Un proche, un conjoint ou un ex-conjoint : le cas le plus fréquent des stalkerwares. */
    PARTNER,

    /** Un logiciel espion sophistiqué, d'auteur inconnu ou criminel. */
    CRIMINAL,

    /** Les autorités du pays (activiste, journaliste, avocat·e…) : ce parcours ne renvoie jamais vers elles. */
    STATE,
}

/** Réponse à « Qui pourrait être à l'origine de cette surveillance ? ». */
enum class SurveillanceChoice(val paths: List<SurveillancePath>) {
    PARTNER(listOf(SurveillancePath.PARTNER)),
    CRIMINAL(listOf(SurveillancePath.CRIMINAL)),
    STATE(listOf(SurveillancePath.STATE)),

    /** Je ne sais pas : les trois parcours, le plus fréquent en tête. */
    UNKNOWN(listOf(SurveillancePath.PARTNER, SurveillancePath.CRIMINAL, SurveillancePath.STATE)),
}

/** Nature d'une ressource ; [AUTHORITY] (police, justice, service de l'État) est exclue du parcours [SurveillancePath.STATE]. */
enum class ResourceKind { HELPLINE, NGO, LAB, AUTHORITY }

/**
 * Un exemple de ressource d'aide, déclaré dans `support_resources.json` (données, pas code). Les ressources sont
 * internationales : aucun organisme national n'est cité.
 */
@Serializable
data class SupportResource(
    val id: String,
    val path: SurveillancePath,
    val kind: ResourceKind,
    val name: String,
    val url: String,
    val phone: String? = null,
    /** Texte court par langue (« en », « fr »…). */
    val description: Map<String, String> = emptyMap(),
) {
    fun description(language: String): String? = description[language] ?: description["en"]
}

class SupportResources(val version: Int, val resources: List<SupportResource>) {

    val size: Int get() = resources.size

    /** Ressources d'un parcours. Le parcours « autorités » n'en propose jamais qui soit une autorité. */
    fun forPath(path: SurveillancePath): List<SupportResource> =
        resources.filter { it.path == path && !(path == SurveillancePath.STATE && it.kind == ResourceKind.AUTHORITY) }

    @Serializable
    private data class FileContent(val version: Int = 0, val resources: List<SupportResource> = emptyList())

    companion object {
        const val MAX_BYTES = 256 * 1024
        private const val MAX_RESOURCES = 300
        private val ID = Regex("[a-z0-9_]{1,40}")
        private val PHONE = Regex("[0-9+][0-9 .\\-/]{1,24}")
        private val LANGUAGE = Regex("[a-z]{2}")
        private val json = Json { ignoreUnknownKeys = true }

        /** Lit le fichier en écartant toute entrée invalide : lien non HTTPS, numéro avec des lettres, texte démesuré… */
        fun parse(text: String): SupportResources {
            val file = json.decodeFromString<FileContent>(text.removePrefix("﻿"))
            val valid = file.resources.asSequence().take(MAX_RESOURCES).mapNotNull(::clean).distinctBy { it.id }.toList()
            return SupportResources(file.version, valid)
        }

        private fun clean(r: SupportResource): SupportResource? {
            if (!ID.matches(r.id)) return null
            if (r.url.length > 300 || !r.url.startsWith("https://") || r.url.any { it.isWhitespace() || it == '"' }) return null
            if (r.phone != null && !PHONE.matches(r.phone)) return null
            if (r.name.isBlank() || r.name.length > 120) return null
            if (r.description.any { (lang, text) -> !LANGUAGE.matches(lang) || text.length > 400 }) return null
            return r.copy(
                name = Untrusted.text(r.name, 120),
                description = r.description.mapValues { Untrusted.text(it.value, 400) },
            )
        }

    }
}
