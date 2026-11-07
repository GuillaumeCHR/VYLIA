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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Indicateurs MVT des logiciels espions de surveillance ciblée : les derniers téléchargés depuis les Paramètres s'ils
 * sont lisibles, sinon ceux embarqués dans l'app. Comme pour Exodus et Echap, la mise à jour ne se fait qu'à la
 * demande et n'envoie rien : ce sont de simples téléchargements de fichiers publics sur GitHub.
 */
class MvtRepository(private val context: Context) {

    /** [downloadedAt] : date du téléchargement, `null` pour la base embarquée. */
    data class State(val database: MvtDatabase.Loaded, val downloadedAt: Long?)

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE_NAME)
    private val _current = MutableStateFlow(load())
    val current: StateFlow<State> = _current.asStateFlow()

    /**
     * Télécharge `indicators.yaml` puis le fichier STIX2 de chaque campagne. La base n'est installée qu'après
     * contrôle : HTTPS vers raw.githubusercontent.com sans redirection, tailles bornées, assez de campagnes et de
     * domaines valides. Sinon la base actuelle reste en place. Renvoie la nouvelle base.
     */
    suspend fun update(): Result<MvtDatabase.Loaded> = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE_NAME.tmp")
        try {
            var budget = MAX_TOTAL_BYTES
            fun get(url: String, max: Long): String {
                val bytes = fetch(url, minOf(max, budget))
                budget -= bytes.size
                return bytes.toString(Charsets.UTF_8)
            }
            val sources = MvtIndicators.parseIndex(get(MvtIndicators.INDEX_URL, MAX_INDEX_BYTES))
            require(sources.size >= MIN_CAMPAIGNS) { "index incomplet (${sources.size} campagnes)" }
            val campaigns = sources.map { MvtIndicators.parseStix(it.name, get(it.url, MAX_STIX_BYTES)) }
            tmp.writeText(MvtIndicators.toJson(campaigns))
            val database = MvtDatabase.parse(tmp.readText())
            require(database.total >= MIN_CAMPAIGNS && database.domains.size >= MIN_DOMAINS) {
                "base incomplète (${database.total} campagnes, ${database.domains.size} domaines)"
            }
            check(tmp.renameTo(downloaded)) { "installation de la base impossible" }
            _current.value = State(database, downloaded.lastModified())
            Result.success(database)
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(e)
        }
    }

    private fun fetch(url: String, max: Long): ByteArray {
        val connection = URL(url).openConnection() as HttpsURLConnection
        require(connection.url.host == HOST) { "hôte inattendu" }
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        // Pas de redirection : les fichiers ne peuvent venir que de l'adresse annoncée.
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            require(connection.contentLengthLong <= max) { "fichier trop volumineux" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= max) { "fichier trop volumineux" }
                    output.write(buffer, 0, n)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
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
        private const val HOST = "raw.githubusercontent.com"
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
