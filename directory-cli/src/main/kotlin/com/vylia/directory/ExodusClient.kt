package com.vylia.directory

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Accès aux données Exodus, réduit à ce dont l'annuaire a besoin (remplacé par un faux dans les tests). */
interface ExodusSource {
    /** Réponse brute de `/api/search/{handle}/details`, ou `null` si l'app est inconnue d'Exodus. */
    fun details(handle: String): String?

    /** Réponse brute de `/api/trackers`. */
    fun trackers(): String
}

/**
 * Client de l'API Exodus Privacy (https://github.com/Exodus-Privacy/exodus/blob/v1/doc/api.md).
 *
 * - Jeton obligatoire, lu dans l'environnement (`EXODUS_API_TOKEN`, secret GitHub Actions), envoyé dans l'en-tête
 *   `Authorization: Token …` et jamais écrit dans les journaux.
 * - User-Agent propre au projet, comme le demande Exodus.
 * - Débit : une requête toutes les [intervalMs] (2 s, bien sous la limite de 30/s) ; `/api/trackers`, limité à
 *   3 requêtes par minute, n'est appelé qu'une fois par exécution.
 * - Seul appel réseau de la génération.
 */
class ExodusClient(
    private val token: String,
    private val baseUrl: String = "https://reports.exodus-privacy.eu.org",
    private val intervalMs: Long = 2_000,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val clock: () -> Long = System::currentTimeMillis,
) : ExodusSource {

    private var last = 0L

    override fun details(handle: String): String? {
        require(HANDLE.matches(handle)) { "nom de paquet invalide : $handle" }
        return get("/api/search/${URLEncoder.encode(handle, Charsets.UTF_8)}/details", allowNotFound = true)
            ?.takeIf { it.trim() != "[]" }
    }

    override fun trackers(): String = checkNotNull(get("/api/trackers", allowNotFound = false))

    private fun get(path: String, allowNotFound: Boolean): String? {
        val wait = last + intervalMs - clock()
        if (wait > 0) sleep(wait)
        last = clock()
        val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Token $token")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            return when (val code = connection.responseCode) {
                200 -> connection.inputStream.use { input ->
                    val bytes = input.readNBytes(MAX_BYTES + 1)
                    check(bytes.size <= MAX_BYTES) { "réponse trop volumineuse pour $path" }
                    bytes.toString(Charsets.UTF_8)
                }
                404 -> if (allowNotFound) null else error("HTTP 404 pour $path")
                // Le jeton n'apparaît jamais dans le message.
                else -> error("HTTP $code pour $path")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "vylia-annuaire/1.0 (+https://github.com/GuillaumeCHR/VYLIA)"
        private const val MAX_BYTES = 20 * 1024 * 1024
        private val HANDLE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}

/**
 * Cache des réponses Exodus, gardé d'une semaine sur l'autre (`actions/cache`) : une app dont le dernier rapport n'a
 * pas changé n'est pas retraitée, et une génération hors ligne (`--offline`) reste possible pour vérifier le site.
 */
class ReportCache(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun read(handle: String): String? = file(handle).takeIf { it.exists() }?.readText()

    fun write(handle: String, content: String) {
        val tmp = File(dir, "$handle.json.tmp")
        tmp.writeText(content)
        check(tmp.renameTo(file(handle)) || run { file(handle).delete(); tmp.renameTo(file(handle)) }) { "cache illisible" }
    }

    fun readTrackers(): String? = File(dir, TRACKERS).takeIf { it.exists() }?.readText()

    fun writeTrackers(content: String) = File(dir, TRACKERS).writeText(content)

    private fun file(handle: String) = File(dir, "$handle.json")

    private companion object {
        const val TRACKERS = "_trackers.json"
    }
}
