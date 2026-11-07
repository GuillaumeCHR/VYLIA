package com.vylia.privacy.data.rules

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vylia.privacy.domain.scoring.IocDatabase
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.data.security.StalkerwareRepository
import com.vylia.privacy.data.security.MvtRepository
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
 * L'import et l'export passent par le Storage Access Framework : aucune permission de stockage n'est requise.
 * Un fichier importé passe par [RulesValidator] (texte brut puis règles décodées) avant de remplacer quoi que ce soit.
 */
class RuleMatrixRepository(
    private val context: Context,
    private val stalkerware: StalkerwareRepository,
    private val mvt: MvtRepository,
) {

    enum class Source { IMPORTED, BUNDLED }

    /**
     * Le moteur reçoit aussi les logiciels espions connus (Echap, MVT), qui ne dépendent pas des règles importées. [fingerprint] :
     * empreinte du fichier de règles et de la base, enregistrée avec chaque point du Suivi pour reconnaître une baisse
     * due à une mise à jour des règles plutôt qu'à une app.
     */
    data class LoadedRules(
        val rules: Rules,
        val source: Source,
        val iocs: IocDatabase = IocDatabase.EMPTY,
        val fingerprint: String = "",
    ) {
        val scorer = PrivacyScorer(rules, iocs)
    }

    /** Stalkerwares d'Echap et logiciels espions de MVT, embarqués ou mis à jour. */
    private val iocs: IocDatabase get() = stalkerware.current.value.database.iocs + mvt.current.value.database.iocs

    private fun fingerprint(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) } + ":" + iocs.size
    }

    private val importedFile = File(context.filesDir, IMPORTED_FILE_NAME)

    private val _current = MutableStateFlow(load())
    val current: StateFlow<LoadedRules> = _current.asStateFlow()

    /** Valide le fichier choisi avant de remplacer la copie importée ; renvoie le nombre de catégories. */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { readBounded(it, RulesValidator.MAX_BYTES) }
                ?: error("Fichier illisible")
            val rules = checked(content, loadBundled().rules)
            // Le moteur valide badges et patterns de sécurité à la construction : avant d'écraser la copie.
            val loaded = LoadedRules(rules, Source.IMPORTED, iocs, fingerprint(content))
            importedFile.writeText(content)
            _current.value = loaded
            rules.categories.size
        }
    }

    /** Contenu des règles importées, `null` si ce sont les règles intégrées (sauvegarde). */
    fun importedContent(): String? =
        if (_current.value.source == Source.IMPORTED && importedFile.exists()) importedFile.readText() else null

    /** Le fichier des règles en vigueur, tel quel : la copie importée, sinon les règles intégrées. */
    fun currentContent(): String = importedContent() ?: bundledContent()

    /** Écrit les règles en vigueur dans le fichier choisi, pour les modifier ou les partager puis les réimporter. */
    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val content = currentContent()
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: error("Fichier impossible à écrire")
            Unit
        }
    }

    /** Après une mise à jour de la base Echap ou MVT : mêmes règles, nouvelle base, les scores se recalculent. */
    fun refreshStalkerware() {
        _current.value = _current.value.copy(iocs = iocs, fingerprint = fingerprint(currentContent()))
    }

    fun resetToBundled() {
        importedFile.delete()
        _current.value = loadBundled()
    }

    private fun load(): LoadedRules {
        if (importedFile.exists()) {
            try {
                // Revalidé à chaque lancement : la copie a pu être importée par une version moins stricte.
                val content = importedFile.readText()
                val rules = checked(content, loadBundled().rules)
                return LoadedRules(rules, Source.IMPORTED, iocs, fingerprint(content))
            } catch (e: Exception) {
                Log.w(TAG, "Règles importées invalides, retour aux règles intégrées", e)
            }
        }
        return loadBundled()
    }

    /** Les règles intégrées font partie de l'app : illisibles, c'est un bug de build, pas un cas à masquer. */
    private fun loadBundled(): LoadedRules = bundledContent().let { LoadedRules(parse(it), Source.BUNDLED, iocs, fingerprint(it)) }

    private fun bundledContent(): String = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }

    companion object {
        private const val TAG = "RuleMatrixRepository"
        const val BUNDLED_ASSET = "vylia_rules_v3.json"
        private const val IMPORTED_FILE_NAME = "imported_vylia_rules_v2.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(content: String): Rules = json.decodeFromString(content.removePrefix("\uFEFF"))

        /** Les deux contrôles de [RulesValidator], dans l'ordre : texte brut, puis règles décodées. */
        fun checked(content: String, bundled: Rules): Rules {
            val text = content.removePrefix("\uFEFF")
            RulesValidator.checkDocument(text)
            return parse(text).also { RulesValidator.validate(it, bundled) }
        }

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
