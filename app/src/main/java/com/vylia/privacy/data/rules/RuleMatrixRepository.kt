package com.vylia.privacy.data.rules

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Règles de score v2 (`vylia_rules_v2.json`), 100 % locales :
 * 1. copie importée par l'utilisateur (sélecteur de fichiers, typiquement depuis Téléchargements),
 * 2. sinon, ou si la copie est illisible, règles intégrées dans `assets/`.
 *
 * L'import passe par le Storage Access Framework : aucune permission de stockage n'est requise.
 */
class RuleMatrixRepository(private val context: Context) {

    enum class Source { IMPORTED, BUNDLED }

    data class LoadedRules(val rules: Rules, val source: Source) {
        val scorer = PrivacyScorer(rules)
    }

    private val importedFile = File(context.filesDir, IMPORTED_FILE_NAME)

    private val _current = MutableStateFlow(load())
    val current: StateFlow<LoadedRules> = _current.asStateFlow()

    /** Valide le fichier choisi avant de remplacer la copie importée ; renvoie le nombre de catégories. */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Fichier illisible")
            val rules = parse(content)
            require("DEFAULT" in rules.categories) { "Catégorie DEFAULT absente" }
            importedFile.writeText(content)
            _current.value = LoadedRules(rules, Source.IMPORTED)
            rules.categories.size
        }
    }

    fun resetToBundled() {
        importedFile.delete()
        _current.value = loadBundled()
    }

    private fun load(): LoadedRules {
        if (importedFile.exists()) {
            try {
                return LoadedRules(parse(importedFile.readText()), Source.IMPORTED)
            } catch (e: Exception) {
                Log.w(TAG, "Règles importées invalides, retour aux règles intégrées", e)
            }
        }
        return loadBundled()
    }

    /** Les règles intégrées font partie de l'app : illisibles, c'est un bug de build, pas un cas à masquer. */
    private fun loadBundled(): LoadedRules =
        LoadedRules(parse(context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }), Source.BUNDLED)

    companion object {
        private const val TAG = "RuleMatrixRepository"
        const val BUNDLED_ASSET = "vylia_rules_v2.json"
        private const val IMPORTED_FILE_NAME = "imported_vylia_rules_v2.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(content: String): Rules = json.decodeFromString(content)
    }
}
