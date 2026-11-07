package com.vylia.privacy.data.shield

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Process
import com.vylia.privacy.domain.shield.BlockMatch
import com.vylia.privacy.domain.shield.BlockedRequest
import com.vylia.privacy.domain.shield.Blocklist
import com.vylia.privacy.domain.shield.ShieldBypass
import com.vylia.privacy.domain.shield.ShieldFilter
import com.vylia.privacy.domain.shield.ShieldIndicator
import com.vylia.privacy.domain.shield.ShieldState
import com.vylia.privacy.domain.shield.ShieldStatus
import com.vylia.privacy.domain.shield.ShieldTapEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** Résultat d'un appui sur le contrôle du bouclier, pour l'écran ou la tuile qui l'a reçu. */
sealed interface ShieldTapResult {
    /** Rien d'autre à faire : le bouclier démarre, s'arrête, ou l'appui est sans effet (activation en cours). */
    data object Done : ShieldTapResult

    /** Lancer cette intention (accord VPN d'Android), puis appeler [ShieldController.start] si elle est acceptée. */
    data class NeedsConsent(val intent: Intent) : ShieldTapResult

    /** Afficher le motif du contournement et le geste qui y répond. */
    data class ShowBypass(val bypass: ShieldBypass) : ShieldTapResult
}

/**
 * Point d'entrée du Bouclier VYLIA pour l'interface et pour [ShieldVpnService] (même processus) : état en direct,
 * compteur de requêtes bloquées, liste de blocage chargée une seule fois.
 *
 * Seul le total de requêtes bloquées est écrit sur le disque ; les domaines bloqués ne restent qu'en mémoire.
 */
class ShieldController(
    private val context: Context,
    /** Appelé à la première requête bloquée de chaque session (badge « Bouclier actif » côté app). */
    private val onFirstBlock: () -> Unit = {},
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

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Un DNS privé est réglé sur un service (nom d'hôte) sur le réseau par défaut de VYLIA, suivi en direct. */
    private val privateDnsHostname = MutableStateFlow(false)

    /**
     * VPN présents, avec l'app qui les possède : l'UID de VYLIA pour le bouclier, un autre UID ou [Process.INVALID_UID]
     * pour une autre app (Android masque ce détail aux autres apps). `null` avant Android 11, qui ne le dit pas.
     */
    private val vpnOwners = MutableStateFlow<Map<Network, Int?>>(emptyMap())

    /**
     * État affiché du bouclier, le même pour tous les écrans et la tuile : dérivé en direct du service, du DNS privé et
     * des autres VPN. Un autre VPN qui évince le bouclier ([ShieldVpnService.onRevoke]) le fait passer aussitôt à
     * contourné.
     */
    val indicator: StateFlow<ShieldIndicator> = combine(
        _state.map { it.status }.distinctUntilChanged(),
        privateDnsHostname,
        vpnOwners,
    ) { status, dns, owners -> ShieldIndicator.derive(status, dns, otherVpn(status, owners)) }
        .stateIn(scope, SharingStarted.Eagerly, ShieldIndicator.INACTIVE)

    @Volatile
    private var blocklist: Blocklist? = null
    private var unsaved = 0

    init {
        watchVpns()
        watchPrivateDns()
    }

    /** Suit les réseaux VPN, ceux du bouclier compris (distingués par leur propriétaire). */
    private fun watchVpns() {
        @Suppress("DEPRECATION") // Lecture immédiate : les rappels ne partent qu'un peu plus tard.
        vpnOwners.value = connectivity.allNetworks
            .mapNotNull { network -> connectivity.getNetworkCapabilities(network)?.let { network to it } }
            .filter { (_, caps) -> caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
            .associate { (network, caps) -> network to caps.owner() }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                vpnOwners.update { it + (network to caps.owner()) }
            }

            override fun onLost(network: Network) {
                vpnOwners.update { it - network }
            }
        })
    }

    /**
     * VYLIA est exclue du tunnel : son réseau par défaut est le vrai réseau (Wi-Fi, données mobiles), celui dont Android
     * applique le réglage de DNS privé, réglage global au téléphone.
     */
    private fun watchPrivateDns() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) {
                privateDnsHostname.value = link.privateDnsServerName != null
            }

            override fun onLost(network: Network) {
                privateDnsHostname.value = false
            }
        })
    }

    private fun NetworkCapabilities.owner(): Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ownerUid else null

    /** Avant Android 11, le propriétaire est inconnu : un VPN présent quand le bouclier ne tourne pas est celui d'une autre app. */
    private fun otherVpn(status: ShieldStatus, owners: Map<Network, Int?>): Boolean = owners.values.any { uid ->
        if (uid != null) uid != Process.myUid() else status != ShieldStatus.ON && status != ShieldStatus.STARTING
    }

    /**
     * Appui sur le contrôle du bouclier : le même effet sur tous les écrans et dans la tuile (voir
     * [ShieldIndicator.tapEffect]).
     */
    fun tap(): ShieldTapResult {
        val current = indicator.value
        return when (current.tapEffect) {
            ShieldTapEffect.DEACTIVATE -> {
                stop()
                ShieldTapResult.Done
            }
            ShieldTapEffect.ACTIVATE -> activate()
            ShieldTapEffect.SHOW_BYPASS -> current.bypass?.let(ShieldTapResult::ShowBypass) ?: ShieldTapResult.Done
            ShieldTapEffect.NONE -> ShieldTapResult.Done
        }
    }

    /** Active le bouclier, ou renvoie l'accord à demander d'abord. Sert aussi à reprendre la main sur un autre VPN. */
    fun activate(): ShieldTapResult = consentIntent()?.let(ShieldTapResult::NeedsConsent) ?: run {
        start()
        ShieldTapResult.Done
    }

    /** Liste embarquée, lue au premier démarrage du bouclier (environ 86 000 domaines). Appel bloquant. */
    @Synchronized
    fun blocklist(): Blocklist = blocklist ?: context.assets.open(ASSET).bufferedReader().use { Blocklist.parse(it.readText()) }
        .also { blocklist = it }

    /** Intention à lancer pour obtenir l'accord de l'utilisateur, ou `null` s'il est déjà donné. */
    fun consentIntent(): Intent? = VpnService.prepare(context)

    /** Un VPN d'une autre app est actif : activer le bouclier le remplacera. */
    fun otherVpnActive(): Boolean {
        val status = _state.value.status
        if (status == ShieldStatus.ON || status == ShieldStatus.STARTING) return false
        return otherVpn(status, vpnOwners.value)
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
        if (next.blockedSession == 1L) onFirstBlock()
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
