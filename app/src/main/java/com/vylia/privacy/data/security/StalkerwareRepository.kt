package com.vylia.privacy.data.security

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.security.StalkerwareDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Base des stalkerwares connus (Echap) : la dernière téléchargée depuis les Paramètres si elle est lisible, sinon celle
 * embarquée dans l'app. Comme la base Exodus, la mise à jour ne se fait qu'à la demande, et n'envoie rien : c'est un
 * simple téléchargement du fichier public `ioc.yaml`.
 */
class StalkerwareRepository(private val context: Context) {

    /** [downloadedAt] : date du téléchargement, `null` pour la base embarquée. */
    data class State(val database: StalkerwareDatabase.Loaded, val downloadedAt: Long?)

    private val downloaded = File(context.filesDir, DOWNLOADED_FILE_NAME)
    private val _current = MutableStateFlow(load())
    val current: StateFlow<State> = _current.asStateFlow()

    /**
     * Télécharge `ioc.yaml` d'Echap. Il n'est installé qu'après contrôle : HTTPS sans redirection, taille bornée,
     * structure reconnue et assez de stalkerwares valides. Sinon la base actuelle reste en place.
     * Renvoie le nombre de stalkerwares de la nouvelle base.
     */
    suspend fun update(): Result<Int> = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "$DOWNLOADED_FILE_NAME.tmp")
        try {
            val connection = URL(UPDATE_URL).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            // Pas de redirection : la base ne peut venir que de l'adresse officielle.
            connection.instanceFollowRedirects = false
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
                require(connection.contentLengthLong <= MAX_BYTES) { "base trop volumineuse" }
                connection.inputStream.use { input -> tmp.outputStream().use { output -> copyBounded(input, output) } }
            } finally {
                connection.disconnect()
            }
            val database = StalkerwareDatabase.parseIoc(tmp.readText())
            require(database.apps >= MIN_APPS) { "base incomplète (${database.apps} stalkerwares)" }
            check(tmp.renameTo(downloaded)) { "installation de la base impossible" }
            _current.value = State(database, downloaded.lastModified())
            Result.success(database.apps)
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(e)
        }
    }

    private fun load(): State {
        if (downloaded.exists()) {
            try {
                // Relue à chaque lancement : elle a pu être installée par une version moins stricte.
                val database = StalkerwareDatabase.parseIoc(downloaded.readText())
                if (database.apps >= MIN_APPS) return State(database, downloaded.lastModified())
            } catch (e: Exception) {
                Log.w(TAG, "Base Echap téléchargée illisible, retour à la base embarquée", e)
            }
        }
        return State(embedded(), null)
    }

    /** La base embarquée fait partie de l'app : illisible, c'est un bug de build, l'erreur remonte. */
    private fun embedded(): StalkerwareDatabase.Loaded =
        StalkerwareDatabase.parse(context.assets.open(StalkerwareDatabase.ASSET).bufferedReader().use { it.readText() })

    private fun copyBounded(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= MAX_BYTES) { "base trop volumineuse" }
            output.write(buffer, 0, n)
        }
    }

    companion object {
        private const val TAG = "StalkerwareRepository"
        const val UPDATE_URL = "https://raw.githubusercontent.com/AssoEchap/stalkerware-indicators/master/ioc.yaml"
        private const val DOWNLOADED_FILE_NAME = "downloaded_stalkerware_ioc.yaml"
        /** Le fichier fait environ 100 Ko ; au-delà de 2 Mo, ce n'est pas lui. */
        private const val MAX_BYTES = 2L * 1024 * 1024
        /** Echap recense plus de 130 stalkerwares Android : beaucoup moins, c'est une base tronquée ou falsifiée. */
        const val MIN_APPS = 100
    }
}
