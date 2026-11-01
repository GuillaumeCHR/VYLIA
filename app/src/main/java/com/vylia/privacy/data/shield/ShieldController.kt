package com.vylia.privacy.data.shield

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import com.vylia.privacy.domain.rewards.BadgesManager
import com.vylia.privacy.domain.shield.BlockMatch
import com.vylia.privacy.domain.shield.BlockedRequest
import com.vylia.privacy.domain.shield.Blocklist
import com.vylia.privacy.domain.shield.ShieldFilter
import com.vylia.privacy.domain.shield.ShieldState
import com.vylia.privacy.domain.shield.ShieldStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * Point d'entrée du Bouclier VYLIA pour l'interface et pour [ShieldVpnService] (même processus) : état en direct,
 * compteur de requêtes bloquées, liste de blocage chargée une seule fois.
 *
 * Seul le total de requêtes bloquées est écrit sur le disque ; les domaines bloqués ne restent qu'en mémoire.
 */
class ShieldController(
    private val context: Context,
    private val badgesManager: BadgesManager,
) {
    private val prefs = context.getSharedPreferences("vylia_shield", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        ShieldState(
            blockedTotal = prefs.getLong(KEY_TOTAL, 0),
            filters = ShieldFilter.entries.filterTo(HashSet()) { prefs.getBoolean(KEY_FILTER + it.name, true) }
                .ifEmpty { ShieldFilter.entries.toSet() },
            pauseMinutes = prefs.getInt(KEY_PAUSE_MINUTES, ShieldState.PAUSE_STEPS.first())
                .takeIf { it in ShieldState.PAUSE_STEPS } ?: ShieldState.PAUSE_STEPS.first(),
        ),
    )
    val state: StateFlow<ShieldState> = _state.asStateFlow()

    @Volatile
    private var blocklist: Blocklist? = null
    private var unsaved = 0

    /** Liste embarquée, lue au premier démarrage du bouclier (environ 86 000 domaines). Appel bloquant. */
    @Synchronized
    fun blocklist(): Blocklist = blocklist ?: context.assets.open(ASSET).bufferedReader().use { Blocklist.parse(it.readText()) }
        .also { blocklist = it }

    /** Intention à lancer pour obtenir l'accord de l'utilisateur, ou `null` s'il est déjà donné. */
    fun consentIntent(): Intent? = VpnService.prepare(context)

    /** Un VPN d'une autre app est actif : activer le bouclier le remplacera. */
    fun otherVpnActive(): Boolean {
        if (_state.value.status == ShieldStatus.ON || _state.value.status == ShieldStatus.STARTING) return false
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.allNetworks.any { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    fun start() {
        _state.update { it.copy(status = ShieldStatus.STARTING) }
        context.startService(Intent(context, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START))
    }

    fun stop() {
        context.startService(Intent(context, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_STOP))
    }

    /**
     * Met le bouclier en pause pour la durée choisie : le VPN reste en place mais laisse tout passer, puis le filtrage
     * reprend seul à l'heure dite, sans rien relancer (Android limite les démarrages en arrière-plan).
     */
    fun pause(now: Long = System.currentTimeMillis()) {
        _state.update { if (it.status == ShieldStatus.ON) it.copy(pausedUntil = now + it.pauseMinutes * 60_000L) else it }
    }

    /** Durée de la prochaine pause (mémorisée) ; une pause en cours est prolongée ou raccourcie d'autant. */
    fun setPauseMinutes(minutes: Int) {
        if (minutes !in ShieldState.PAUSE_STEPS) return
        _state.update { s ->
            val until = s.pausedUntil?.let { it + (minutes - s.pauseMinutes) * 60_000L }
            s.copy(pauseMinutes = minutes, pausedUntil = until)
        }
        prefs.edit().putInt(KEY_PAUSE_MINUTES, minutes).apply()
    }

    /** Active ou coupe un filtrage ; le dernier actif ne peut pas être coupé (c'est le rôle de l'interrupteur). */
    fun setFilter(filter: ShieldFilter, enabled: Boolean) {
        val next = _state.updateAndGet { s ->
            val filters = if (enabled) s.filters + filter else s.filters - filter
            if (filters.isEmpty()) s else s.copy(filters = filters)
        }
        prefs.edit().putBoolean(KEY_FILTER + filter.name, filter in next.filters).apply()
    }

    /** Filtrages appliqués par le VPN, lus à chaque requête. */
    internal fun filters(): Set<ShieldFilter> = _state.value.filters

    fun resume() {
        _state.update { it.copy(pausedUntil = null) }
    }

    /** Lu par le VPN à chaque requête. */
    internal fun isPaused(): Boolean = _state.value.isPaused(System.currentTimeMillis())

    internal fun onStatus(status: ShieldStatus) {
        _state.update {
            when {
                status == ShieldStatus.ON && it.status != ShieldStatus.ON -> it.copy(status = status, blockedSession = 0, pausedUntil = null)
                status != ShieldStatus.ON -> it.copy(status = status, pausedUntil = null)
                else -> it.copy(status = status)
            }
        }
        if (status != ShieldStatus.ON && status != ShieldStatus.STARTING) flush()
    }

    /** Appelé par le fil de lecture du VPN, un seul à la fois. */
    internal fun onBlocked(domain: String, match: BlockMatch) {
        val next = _state.updateAndGet { it.onBlocked(BlockedRequest(domain, match.trackerName, System.currentTimeMillis())) }
        // Badge « Bouclier actif » : débloqué au premier blocage (sans effet s'il l'est déjà).
        if (next.blockedSession == 1L) badgesManager.onShieldBlocked()
        if (++unsaved >= SAVE_EVERY) flush()
    }

    /** Supprimer mes données : le total repart de zéro (l'état en cours du bouclier est gardé). */
    fun clear() {
        _state.update { it.copy(blockedSession = 0, blockedTotal = 0, recent = emptyList()) }
        flush()
    }

    @Synchronized
    private fun flush() {
        unsaved = 0
        prefs.edit().putLong(KEY_TOTAL, _state.value.blockedTotal).apply()
    }

    companion object {
        const val ASSET = "shield_blocklist.json"
        private const val KEY_TOTAL = "blocked_total"
        private const val KEY_FILTER = "filter_"
        private const val KEY_PAUSE_MINUTES = "pause_minutes"
        /** Le total est écrit toutes les [SAVE_EVERY] requêtes bloquées, et à l'arrêt du bouclier. */
        private const val SAVE_EVERY = 25
    }
}
