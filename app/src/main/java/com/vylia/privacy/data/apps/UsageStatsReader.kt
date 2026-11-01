package com.vylia.privacy.data.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Date de dernière utilisation de chaque app, lue localement dans les statistiques d'utilisation d'Android.
 * Nécessite l'« accès aux données d'utilisation », un accès spécial que l'utilisateur active lui-même ;
 * rien ne quitte le téléphone.
 */
class UsageStatsReader(private val context: Context) {

    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Dernière utilisation (ms) par paquet sur l'année écoulée ; une app absente n'a pas été ouverte sur la période.
     * `null` sans accès.
     */
    fun lastUsed(now: Long = System.currentTimeMillis()): Map<String, Long>? {
        if (!hasAccess()) return null
        return usageStats.queryAndAggregateUsageStats(now - LOOKBACK_MS, now)
            .filterValues { it.lastTimeUsed > 0 }
            .mapValues { it.value.lastTimeUsed }
    }

    private companion object {
        const val LOOKBACK_MS = 365L * 24 * 60 * 60 * 1000
    }
}
