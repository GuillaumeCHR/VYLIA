package com.vylia.privacy.data.security

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.security.MvtDatabase
import com.vylia.privacy.domain.security.MvtIndicators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import java.io.File

/**
 * Indicateurs MVT des logiciels espions de surveillance ciblée : les derniers téléchargés depuis les Paramètres s'ils
 * sont lisibles, sinon ceux embarqués dans l'app. Comme pour Exodus et Echap, la mise à jour ne se fait qu'à la
 * demande et n'envoie rien : ce sont de simples téléchargements de fichiers publics sur GitHub.
 */
class MvtRepository(private val context: Context) {

    /** [downloadedAt] : date du téléchargement, `null` pour la base embarquée. */
    data class State(val database: MvtDatabase.Loaded, val downloadedAt: Long?)

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE_NAME)
    /** Copie de l'index de la dernière base installée : relue quand la source répond qu'il n'a pas changé. */
    private val index = File(context.filesDir, "downloaded_mvt_index.yaml")
    private val _current = MutableStateFlow(load())
    val current: StateFlow<State> = _current.asStateFlow()

    /**
     * Télécharge `indicators.yaml` puis le fichier STIX2 de chaque campagne. La base n'est installée qu'après
     * contrôle : tailles bornées, assez de campagnes et de domaines valides. Sinon la base actuelle reste en place.
     *
     * Requêtes conditionnelles : la base est « inchangée » si l'index et tous les fichiers de campagne répondent 304.
     * Si l'un d'eux a changé, la base est refaite en entier (les fichiers inchangés sont alors retéléchargés).
     */
    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        var budget = MAX_TOTAL_BYTES
        suspend fun get(url: String, max: Long, conditional: Boolean): String? =
            fetch.text(url, minOf(max, budget), conditional)?.also { budget -= it.length }

        val fresh = get(MvtIndicators.INDEX_URL, MAX_INDEX_BYTES, conditional = index.exists())
        val indexText = fresh ?: index.readText()
        val sources = MvtIndicators.parseIndex(indexText)
        require(sources.size >= MIN_CAMPAIGNS) { "index incomplet (${sources.size} campagnes)" }
        val unchangedIndex = fresh == null && downloaded.exists()
        val texts = sources.map { get(it.url, MAX_STIX_BYTES, conditional = unchangedIndex) }
        if (unchangedIndex && texts.all { it == null }) return@withContext Outcome.Unchanged
        val campaigns = sources.zip(texts).map { (source, text) ->
            MvtIndicators.parseStix(source.name, text ?: checkNotNull(get(source.url, MAX_STIX_BYTES, conditional = false)))
        }
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE_NAME.tmp")
        try {
            tmp.writeText(MvtIndicators.toJson(campaigns))
            val database = MvtDatabase.parse(tmp.readText())
            require(database.total >= MIN_CAMPAIGNS && database.domains.size >= MIN_DOMAINS) {
                "base incomplète (${database.total} campagnes, ${database.domains.size} domaines)"
            }
            check(tmp.renameTo(downloaded)) { "installation de la base impossible" }
            index.writeText(indexText)
            _current.value = State(database, downloaded.lastModified())
            Outcome.Installed(database.total)
        } finally {
            tmp.delete()
        }
    }

    private fun load(): State {
        if (downloaded.exists()) {
            try {
                // Relue à chaque lancement : elle a pu être installée par une version moins stricte.
                val database = MvtDatabase.parse(downloaded.readText())
                if (database.total >= MIN_CAMPAIGNS && database.domains.size >= MIN_DOMAINS) return State(database, downloaded.lastModified())
            } catch (e: Exception) {
                Log.w(TAG, "Base MVT téléchargée illisible, retour à la base embarquée", e)
            }
        }
        return State(embedded(), null)
    }

    /** La base embarquée fait partie de l'app : illisible, c'est un bug de build, l'erreur remonte. */
    private fun embedded(): MvtDatabase.Loaded =
        MvtDatabase.parse(context.assets.open(MvtDatabase.ASSET).bufferedReader().use { it.readText() })

    companion object {
        private const val TAG = "MvtRepository"
        private const val DOWNLOADED_FILE_NAME = "downloaded_mvt_iocs.json"
        private const val MAX_INDEX_BYTES = 1L * 1024 * 1024
        /** Le plus gros fichier repris (une campagne Android d'Amnesty) fait quelques mégaoctets. */
        private const val MAX_STIX_BYTES = 10L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 60L * 1024 * 1024
        /** MVT publie 18 campagnes hors stalkerwares, et environ 5 000 domaines : beaucoup moins, c'est une base tronquée. */
        const val MIN_CAMPAIGNS = 10
        const val MIN_DOMAINS = 1_000
    }
}
