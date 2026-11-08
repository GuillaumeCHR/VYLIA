package com.vylia.privacy.data.databases

import android.content.Context
import android.util.Log
import com.vylia.privacy.domain.databases.CompaniesLookup
import com.vylia.privacy.domain.databases.Company
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome
import com.vylia.privacy.domain.databases.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * AdGuard companiesdb (CC BY-SA 4.0) : nomme les domaines bloqués par le bouclier (« Criteo · ciblage »). Téléchargée
 * seulement, jamais embarquée. Des 1,5 Mo reçus, seul un index réduit est gardé, compressé (environ 100 Ko), et chargé
 * à la demande.
 */
class CompaniesRepository(private val context: Context) {

    private val index = File(context.filesDir, "companies_index.gz")

    @Volatile
    private var loaded: CompaniesLookup? = null

    fun lookup(domain: String): Company? = lookup().lookup(domain)

    val size: Int? get() = if (index.exists()) lookup().size else null

    private fun lookup(): CompaniesLookup = loaded ?: read().also { loaded = it }

    suspend fun update(fetch: Fetch): Outcome = withContext(Dispatchers.IO) {
        val trackers = fetch.text(TRACKERS_URL, MAX_BYTES, conditional = index.exists())
        val companies = fetch.text(COMPANIES_URL, MAX_BYTES, conditional = index.exists())
        if (trackers == null && companies == null) return@withContext Outcome.Unchanged
        val fresh = CompaniesLookup.parse(
            trackers ?: checkNotNull(fetch.text(TRACKERS_URL, MAX_BYTES, conditional = false)),
            companies ?: checkNotNull(fetch.text(COMPANIES_URL, MAX_BYTES, conditional = false)),
        )
        require(fresh.size >= MIN_DOMAINS) { "base incomplète (${fresh.size} domaines)" }
        val tmp = File(context.filesDir, "companies_index.gz.tmp")
        try {
            GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { out ->
                fresh.toLines().forEach { out.write(it); out.write("\n") }
            }
            check(tmp.renameTo(index)) { "installation de la base impossible" }
        } finally {
            tmp.delete()
        }
        loaded = fresh
        Outcome.Installed(fresh.size)
    }

    private fun read(): CompaniesLookup {
        if (!index.exists()) return CompaniesLookup.EMPTY
        return try {
            GZIPInputStream(index.inputStream().buffered()).bufferedReader().useLines(CompaniesLookup::fromLines)
        } catch (e: Exception) {
            Log.w(TAG, "Index companiesdb illisible", e)
            CompaniesLookup.EMPTY
        }
    }

    companion object {
        private const val TAG = "CompaniesRepository"
        const val TRACKERS_URL = "https://raw.githubusercontent.com/AdguardTeam/companiesdb/main/dist/trackers.json"
        const val COMPANIES_URL = "https://raw.githubusercontent.com/AdguardTeam/companiesdb/main/dist/companies.json"
        private const val MAX_BYTES = 10L * 1024 * 1024
        private const val MIN_DOMAINS = 1_000
    }
}
