package com.vylia.privacy.data.databases

import com.vylia.privacy.domain.databases.Http
import com.vylia.privacy.domain.databases.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * Seul accès réseau des bases : HTTPS imposé, hôtes connus seulement, pas de redirection (une base ne peut venir que de
 * l'adresse officielle), taille bornée. Les sources compressent en gzip : moins de données transférées (environ 6 Mo
 * au lieu de 22 pour toutes les listes) ; la borne porte sur le contenu décompressé, pour qu'un fichier piégé ne
 * puisse pas remplir le téléphone. Rien n'est envoyé d'autre que la requête elle-même.
 */
class HttpsClient(private val allowedHosts: Set<String> = ALLOWED_HOSTS) : Http {

    override suspend fun request(url: String, validators: Validators?, maxBytes: Long, dest: File?): Http.Response =
        withContext(Dispatchers.IO) {
            val parsed = URL(url)
            require(parsed.protocol == "https") { "adresse non HTTPS" }
            require(parsed.host in allowedHosts) { "hôte inattendu : ${parsed.host}" }
            val connection = parsed.openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = if (dest == null) "HEAD" else "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                // Demandé explicitement : la décompression est faite ici, pour en borner la taille.
                connection.setRequestProperty("Accept-Encoding", "gzip")
                validators?.etag?.let { connection.setRequestProperty("If-None-Match", it) }
                validators?.lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> return@withContext Http.Response.NotModified
                    HttpURLConnection.HTTP_OK -> Unit
                    else -> error("HTTP $code")
                }
                val received = Validators(
                    etag = connection.getHeaderField("ETag")?.take(200),
                    lastModified = connection.getHeaderField("Last-Modified")?.take(100),
                )
                if (dest == null) return@withContext Http.Response.Ok(received, 0)
                val counting = CountingInputStream(connection.inputStream)
                val gzip = connection.contentEncoding.equals("gzip", ignoreCase = true)
                (if (gzip) GZIPInputStream(counting) else counting).use { input ->
                    dest.outputStream().use { output -> copyBounded(input, output, maxBytes) }
                }
                Http.Response.Ok(received, counting.count)
            } finally {
                connection.disconnect()
            }
        }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, max: Long) {
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

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        /** Sources officielles des bases ; toute autre adresse est refusée. */
        val ALLOWED_HOSTS = setOf(
            "reports.exodus-privacy.eu.org",
            "raw.githubusercontent.com",
            "easylist.to",
        )
    }
}
