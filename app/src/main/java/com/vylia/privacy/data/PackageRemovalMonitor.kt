package com.vylia.privacy.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Désinstallations en direct (broadcast système local, aucun réseau), pour mettre à jour
 * scores et récompenses sans relancer de scan. Les mises à jour d'app (`EXTRA_REPLACING`) sont ignorées.
 */
class PackageRemovalMonitor(context: Context) {
    private val packageManager = context.packageManager
    private val _removals = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val removals: SharedFlow<String> = _removals.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (!isInstalled(packageName)) _removals.tryEmit(packageName)
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED).apply { addDataScheme("package") }
        // Broadcast protégé : seul le système peut l'émettre. On revérifie malgré tout l'absence du paquet.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun isInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
