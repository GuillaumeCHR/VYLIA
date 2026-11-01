package com.vylia.privacy.data.dns

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.domain.act.PrivateDnsCatalogue
import com.vylia.privacy.domain.act.PrivateDnsValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** DNS privé tel qu'Android l'applique au réseau actuel. */
sealed interface PrivateDnsStatus {
    /** Android 8 : le DNS privé n'existe qu'à partir d'Android 9. */
    data object Unsupported : PrivateDnsStatus
    data object NoNetwork : PrivateDnsStatus
    data object Off : PrivateDnsStatus
    /** Mode automatique : chiffré quand le réseau le permet, sans service choisi. */
    data object Automatic : PrivateDnsStatus
    /** Un service est choisi par son nom d'hôte. */
    data class Hostname(val hostname: String) : PrivateDnsStatus
}

/**
 * Liste des services de DNS privé : celle de l'app, ou sa dernière version téléchargée à votre demande (« Mettre à
 * jour la liste »), contrôlée avant usage. Rien n'est téléchargé sans votre geste.
 */
class PrivateDnsRepository(private val context: Context) {
    private val downloaded = File(context.filesDir, DOWNLOADED_FILE)
    private val _catalogue = MutableStateFlow(load())
    val catalogue: StateFlow<PrivateDnsCatalogue> = _catalogue.asStateFlow()

    /** Télécharge la liste publiée avec le code source de VYLIA. Renvoie vrai si elle a changé. */
    suspend fun update(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(REMOTE_URL).openConnection() as HttpsURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            val text = try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
                connection.inputStream.use { RuleMatrixRepository.readBounded(it, PrivateDnsCatalogue.MAX_BYTES) }
            } finally {
                connection.disconnect()
            }
            val fresh = parse(text)
            require(fresh.version >= bundled().version) { "liste plus ancienne que celle de l'app" }
            val changed = fresh != _catalogue.value
            val tmp = File(context.filesDir, "$DOWNLOADED_FILE.tmp")
            tmp.writeText(text)
            check(tmp.renameTo(downloaded)) { "écriture impossible" }
            _catalogue.value = fresh
            changed
        }
    }

    fun status(): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return PrivateDnsStatus.Unsupported
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val link = connectivity.activeNetwork?.let(connectivity::getLinkProperties) ?: return PrivateDnsStatus.NoNetwork
        val name = link.privateDnsServerName
        return when {
            name != null -> PrivateDnsStatus.Hostname(name)
            link.isPrivateDnsActive -> PrivateDnsStatus.Automatic
            else -> PrivateDnsStatus.Off
        }
    }

    private fun load(): PrivateDnsCatalogue {
        val bundled = bundled()
        if (downloaded.exists()) {
            try {
                val fresh = parse(downloaded.readText())
                if (fresh.version >= bundled.version) return fresh
            } catch (e: Exception) {
                Log.w(TAG, "Liste DNS téléchargée invalide, liste de l'app utilisée", e)
            }
        }
        return bundled
    }

    /** La liste de l'app fait partie du build : illisible, c'est un bug à voir tout de suite. */
    private fun bundled(): PrivateDnsCatalogue = parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })

    companion object {
        private const val TAG = "PrivateDnsRepository"
        const val ASSET = "private_dns.json"
        private const val DOWNLOADED_FILE = "private_dns.json"
        /** Même fichier que l'asset, publié avec le code source (branche principale). */
        const val REMOTE_URL = "https://raw.githubusercontent.com/GuillaumeCHR/VYLIA/main/app/src/main/assets/private_dns.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PrivateDnsCatalogue =
            PrivateDnsValidator.validate(json.decodeFromString<PrivateDnsCatalogue>(text.removePrefix("﻿")))
    }
}
