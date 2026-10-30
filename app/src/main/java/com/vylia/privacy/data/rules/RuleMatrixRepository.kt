package com.vylia.privacy.data.rules

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vylia.privacy.domain.scoring.RuleMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Matrice de règles contextuelles, 100 % locale :
 * 1. copie importée par l'utilisateur (sélecteur de fichiers, typiquement depuis Téléchargements),
 * 2. sinon, ou si la copie est illisible, matrice intégrée dans `assets/`.
 *
 * L'import passe par le Storage Access Framework : aucune permission de stockage n'est requise.
 */
class RuleMatrixRepository(private val context: Context) {

    enum class Source { IMPORTED, BUNDLED }

    data class LoadedMatrix(val matrix: RuleMatrix, val source: Source)

    private val importedFile = File(context.filesDir, IMPORTED_FILE_NAME)

    private val _current = MutableStateFlow(load())
    val current: StateFlow<LoadedMatrix> = _current.asStateFlow()

    /** Valide le fichier choisi avant de remplacer la copie importée ; renvoie le nombre de catégories. */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Fichier illisible")
            val matrix = RuleMatrix.fromJson(json)
            require(matrix.rules.isNotEmpty()) { "Aucune règle dans ce fichier" }
            importedFile.writeText(json)
            _current.value = LoadedMatrix(matrix, Source.IMPORTED)
            matrix.rules.size
        }
    }

    fun resetToBundled() {
        importedFile.delete()
        _current.value = loadBundled()
    }

    private fun load(): LoadedMatrix {
        if (importedFile.exists()) {
            try {
                return LoadedMatrix(RuleMatrix.fromJson(importedFile.readText()), Source.IMPORTED)
            } catch (e: Exception) {
                Log.w(TAG, "Matrice importée invalide, retour à la matrice intégrée", e)
            }
        }
        return loadBundled()
    }

    private fun loadBundled(): LoadedMatrix = try {
        val json = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
        LoadedMatrix(RuleMatrix.fromJson(json), Source.BUNDLED)
    } catch (e: Exception) {
        Log.e(TAG, "Matrice intégrée illisible : règles contextuelles désactivées", e)
        LoadedMatrix(RuleMatrix(emptyList()), Source.BUNDLED)
    }

    private companion object {
        const val TAG = "RuleMatrixRepository"
        const val BUNDLED_ASSET = "privacy_rules_matrix_advanced.json"
        const val IMPORTED_FILE_NAME = "imported_privacy_rules_matrix.json"
    }
}
