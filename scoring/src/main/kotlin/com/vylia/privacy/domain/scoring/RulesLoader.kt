package com.vylia.privacy.domain.scoring

import kotlinx.serialization.json.Json

/**
 * Lecture des règles de score (`vylia_rules_v3.json`), la même pour l'app et pour l'annuaire public : texte brut
 * contrôlé par [RulesValidator], puis règles décodées et validées face aux règles de référence.
 */
object RulesLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): Rules = json.decodeFromString(content.removePrefix("﻿"))

    /** Les deux contrôles de [RulesValidator], dans l'ordre : texte brut, puis règles décodées. */
    fun checked(content: String, reference: Rules): Rules {
        val text = content.removePrefix("﻿")
        RulesValidator.checkDocument(text)
        return parse(text).also { RulesValidator.validate(it, reference) }
    }
}
