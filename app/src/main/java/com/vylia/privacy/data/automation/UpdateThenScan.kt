package com.vylia.privacy.data.automation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.vylia.privacy.data.databases.NetworkState
import kotlinx.coroutines.CancellationException

/**
 * Scan périodique : d'abord la mise à jour des bases, puis le scan, dans la même tâche (l'équivalent d'une chaîne
 * WorkManager `beginUniqueWork().then()`, avec le JobScheduler déjà en place). Le scan part toujours, même si la mise à
 * jour échoue en tout ou en partie, ou s'il n'y a pas de réseau : il utilise alors les bases en place.
 */
object UpdateThenScan {
    suspend fun run(
        update: suspend () -> Unit,
        scan: suspend () -> Unit,
        onUpdateError: (Exception) -> Unit = { Log.w("VyliaAutomation", "Mise à jour des bases en échec, scan avec les bases en place", it) },
    ) {
        try {
            update()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onUpdateError(e)
        }
        scan()
    }

    /** Réseau actuel : aucun, facturé (données mobiles) ou non facturé (Wi-Fi). */
    fun network(context: Context): NetworkState {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return NetworkState.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetworkState.NONE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) NetworkState.UNMETERED else NetworkState.METERED
    }
}
