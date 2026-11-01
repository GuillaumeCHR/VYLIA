package com.vylia.privacy.data.alternatives

import android.content.Context
import com.vylia.privacy.domain.act.AlternativeCatalogue
import kotlinx.serialization.json.Json

/** Catalogue d'alternatives embarqué ; lu une seule fois. Un catalogue illisible est un bug de build. */
class AlternativesRepository(private val context: Context) {

    val catalogue: AlternativeCatalogue by lazy {
        parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    companion object {
        const val ASSET = "alternatives.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(content: String): AlternativeCatalogue = json.decodeFromString(content)
    }
}
