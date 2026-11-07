package com.vylia.privacy.domain.act

import com.vylia.privacy.domain.model.AuditedApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Catalogue d'alternatives par catégorie du Play Store (assets/alternatives.json, voir tools/alternatives/build_catalogue.py). */
@Serializable
data class AlternativeCatalogue(val version: String, val groups: List<AlternativeGroup>) {

    /** Paquets de toutes les alternatives : leur installation compte pour le trophée Bon échange. */
    val packages: Set<String> by lazy { groups.flatMap { g -> g.alternatives.map { it.packageName } }.toSet() }

    /** Famille qui remplace cette app : d'abord par paquet, sinon par catégorie de règles. */
    fun groupFor(app: AuditedApp): AlternativeGroup? =
        groups.firstOrNull { app.app.packageName in it.replaces }
            ?: groups.firstOrNull { app.result.categoryId in it.categories }
}

@Serializable
data class AlternativeGroup(
    val id: String,
    /** Textes par langue (« en », « fr ») ; l'anglais sert de repli. */
    val title: Map<String, String>,
    val replaces: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val alternatives: List<Alternative>,
)

@Serializable
data class Alternative(
    val name: String,
    @SerialName("package") val packageName: String,
    /** Identifiant sur F-Droid, ou `null` si l'app n'y est pas. */
    val fdroid: String? = null,
    /** Identifiant sur Google Play, ou `null`. */
    val play: String? = null,
    /** Site de l'app quand elle n'est sur aucun des deux magasins (ex. F-Droid lui-même). */
    val web: String? = null,
    @SerialName("open_source") val openSource: Boolean = false,
    /** Coût relevé sur AlternativeTo (« Free », « Freemium »…), `null` si inconnu. */
    val cost: String? = null,
    /** Note moyenne (étoiles) sur la fiche Google Play au moment du relevé ; absente si l'app n'y est pas notée. */
    val rating: Double? = null,
    val pitch: Map<String, String>,
)

/** Texte dans la langue demandée, sinon en anglais. */
fun Map<String, String>.localized(language: String): String = this[language] ?: this["en"] ?: values.first()
