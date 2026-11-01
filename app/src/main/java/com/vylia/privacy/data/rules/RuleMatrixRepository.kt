package com.vylia.privacy.data.rules

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.RulesValidator
import com.vylia.privacy.domain.scoring.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Règles de score v3 (`vylia_rules_v3.json`), 100 % locales :
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
            val content = context.contentResolver.openInputStream(uri)?.use { readBounded(it, RulesValidator.MAX_BYTES) }
                ?: error("Fichier illisible")
            val rules = parse(content)
            RulesValidator.validate(rules, allowedExclusions = loadBundled().rules.excludedPackages)
            // Le moteur valide badges et patterns de sécurité à la construction : avant d'écraser la copie.
            val loaded = LoadedRules(rules, Source.IMPORTED)
            importedFile.writeText(content)
            _current.value = loaded
            rules.categories.size
        }
    }

    /** Contenu des règles importées, `null` si ce sont les règles intégrées (sauvegarde). */
    fun importedContent(): String? =
        if (_current.value.source == Source.IMPORTED && importedFile.exists()) importedFile.readText() else null

    fun resetToBundled() {
        importedFile.delete()
        _current.value = loadBundled()
    }

    private fun load(): LoadedRules {
        if (importedFile.exists()) {
            try {
                // Revalidé à chaque lancement : la copie a pu être importée par une version moins stricte.
                val rules = parse(importedFile.readText())
                val bundled = loadBundled()
                RulesValidator.validate(rules, allowedExclusions = bundled.rules.excludedPackages)
                return LoadedRules(rules, Source.IMPORTED)
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
        const val BUNDLED_ASSET = "vylia_rules_v3.json"
        private const val IMPORTED_FILE_NAME = "imported_vylia_rules_v2.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(content: String): Rules = json.decodeFromString(content.removePrefix("\uFEFF"))

        /** Lecture bornée : un fichier trop gros est refusé avant d'être chargé en entier. */
        fun readBounded(input: java.io.InputStream, maxBytes: Int): String {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                require(out.size() <= maxBytes) { "fichier trop volumineux (${maxBytes / 1024 / 1024} Mo au maximum)" }
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
