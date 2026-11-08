package com.vylia.privacy.data.databases

import android.util.Log
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.security.MvtRepository
import com.vylia.privacy.data.trackers.AssetTrackerSignatureRepository
import com.vylia.privacy.domain.databases.DatabaseGroup
import com.vylia.privacy.domain.databases.DatabaseSource
import com.vylia.privacy.domain.databases.DatabaseUpdater
import com.vylia.privacy.domain.databases.UpdateSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Point d'entrée des bases pour l'interface et le scan de fond : mise à jour (groupée ou d'une base), puis ce qui en
 * découle. Les sources du bouclier sont fournies par [shield] : sans le module du bouclier, elles disparaissent.
 *
 * Après une mise à jour :
 * - Exodus ou ses catégories : nouvelles signatures, notes recalculées ; l'écran Scan relance l'analyse des traqueurs
 *   (rapide si seules les catégories ont changé : le cache d'analyse reste valable) ;
 * - Echap, MVT : moteur de score recalculé ; les domaines MVT passent aussitôt au bouclier ;
 * - règles : publiées par leur dépôt (sauf règles importées) ;
 * - listes du bouclier, Exodus ou catégories : liste du bouclier recompilée, sans couper le tunnel s'il tourne.
 */
class DatabaseCenter(
    val updater: DatabaseUpdater,
    private val settings: SettingsRepository,
    private val signatures: AssetTrackerSignatureRepository,
    private val rules: RuleMatrixRepository,
    private val mvt: MvtRepository,
    private val shield: ShieldBridge?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Ce que l'app attend du bouclier, s'il est présent. */
    interface ShieldBridge {
        fun sources(): List<DatabaseSource>
        fun setListEnabled(id: String, enabled: Boolean)
        fun rebuild()
        fun setSpywareDomains(domains: Map<String, String>)
    }

    /** Bilan de la dernière mise à jour lancée depuis l'écran (pas celle du scan de fond). */
    private val _lastSummary = MutableStateFlow<UpdateSummary?>(null)
    val lastSummary: StateFlow<UpdateSummary?> = _lastSummary.asStateFlow()

    fun sources(): List<DatabaseSource> = updater.sources()

    suspend fun updateAll(): UpdateSummary = after(updater.updateAll()).also { _lastSummary.value = it }

    suspend fun updateOne(id: String): UpdateSummary = after(updater.updateOne(id)).also { _lastSummary.value = it }

    /** Ajoute une base proposée (liste CNAME) et la télécharge ; la retirer l'efface et recompile le bouclier. */
    suspend fun setProposedEnabled(id: String, enabled: Boolean): UpdateSummary? {
        shield?.setListEnabled(id, enabled)
        if (enabled) return updateOne(id)
        updater.forget(id)
        withContext(Dispatchers.Default) { shield?.rebuild() }
        return null
    }

    fun clearSummary() {
        _lastSummary.value = null
    }

    /**
     * Mise à jour avant le scan périodique : une fois par semaine au plus, seulement si l'option est active. Sur un
     * réseau facturé avec « Uniquement en Wi-Fi », seul un contrôle est fait (pour annoncer les mises à jour
     * disponibles). Ne lève jamais : le scan doit partir quoi qu'il arrive.
     */
    suspend fun updateBeforeScan(network: NetworkState) {
        try {
            if (!AutoUpdatePolicy.isDue(settings.updateBeforeScan.value, settings.lastAutoUpdate, clock())) return
            when (AutoUpdatePolicy.action(network, settings.updateWifiOnly.value)) {
                AutoUpdatePolicy.Action.SKIP -> return
                AutoUpdatePolicy.Action.CHECK -> withTimeoutOrNull(BUDGET_MS) { updater.checkAll() }
                AutoUpdatePolicy.Action.UPDATE -> {
                    val summary = withTimeoutOrNull(BUDGET_MS) { after(updater.updateAll()) }
                    if (summary != null && (summary.updated.isNotEmpty() || summary.unchanged.isNotEmpty())) {
                        settings.lastAutoUpdate = clock()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Mise à jour des bases avant le scan interrompue", e)
        }
    }

    /** Version de chaque base du score, enregistrée avec chaque point du Suivi. */
    fun scoreVersions(): Map<String, String> {
        val meta = updater.meta.value
        return sources().filter { it.affectsScore }.associate { it.id to (meta[it.id]?.version ?: "app") }
    }

    private suspend fun after(summary: UpdateSummary): UpdateSummary {
        val changed = summary.updated.toSet()
        if ("exodus" in changed || "exodus_categories" in changed) {
            signatures.invalidateCache()
            rules.refreshDatabases()
        }
        if ("echap" in changed || "mvt" in changed) rules.refreshStalkerware()
        if ("mvt" in changed) shield?.setSpywareDomains(mvt.current.value.database.domains)
        val shieldChanged = sources().any { it.group == DatabaseGroup.SHIELD && it.id in changed }
        if (shieldChanged || "exodus" in changed || "exodus_categories" in changed) {
            withContext(Dispatchers.Default) {
                runCatching { shield?.rebuild() }.onFailure { Log.w(TAG, "Liste du bouclier non recompilée", it) }
            }
        }
        return summary
    }

    companion object {
        private const val TAG = "DatabaseCenter"
        /** Le scan de fond doit avoir le temps de tourner ensuite : la mise à jour a 3 minutes au plus. */
        const val BUDGET_MS = 3L * 60 * 1000
    }
}

/** Réseau au moment de la mise à jour automatique. */
enum class NetworkState { NONE, METERED, UNMETERED }

/** Règles de la mise à jour automatique, sans Android (testées). */
object AutoUpdatePolicy {
    const val MIN_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    enum class Action { SKIP, CHECK, UPDATE }

    /** Une mise à jour par semaine au plus, même si le scan était plus fréquent. */
    fun isDue(enabled: Boolean, lastAutoUpdate: Long?, now: Long): Boolean =
        enabled && (lastAutoUpdate == null || now - lastAutoUpdate >= MIN_INTERVAL_MS - GRACE_MS)

    /** JobScheduler décale les tâches de quelques heures : une marge évite de sauter une semaine. */
    private const val GRACE_MS = 6L * 60 * 60 * 1000

    fun action(network: NetworkState, wifiOnly: Boolean): Action = when {
        network == NetworkState.NONE -> Action.SKIP
        network == NetworkState.METERED && wifiOnly -> Action.CHECK
        else -> Action.UPDATE
    }
}
