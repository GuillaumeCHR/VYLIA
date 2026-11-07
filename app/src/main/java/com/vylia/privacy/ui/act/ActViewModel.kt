package com.vylia.privacy.ui.act

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.alternatives.AlternativesRepository
import com.vylia.privacy.data.apps.UsageStatsReader
import com.vylia.privacy.data.dns.PrivateDnsRepository
import com.vylia.privacy.data.dns.PrivateDnsStatus
import com.vylia.privacy.data.shield.ShieldController
import com.vylia.privacy.domain.shield.ShieldFilter
import com.vylia.privacy.domain.shield.ShieldState
import com.vylia.privacy.domain.act.AlternativeCatalogue
import com.vylia.privacy.domain.act.PrivateDnsCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Accès aux données d'utilisation et dernière utilisation de chaque app (`null` tant qu'il n'est pas accordé). */
data class UsageState(val hasAccess: Boolean = false, val lastUsed: Map<String, Long>? = null)

/** Données propres à l'onglet Agir ; les apps, les règles et les récompenses viennent de ScanViewModel. */
/** Résultat de « Mettre à jour la liste » des services DNS. */
enum class DnsListUpdate { RUNNING, UPDATED, UNCHANGED, FAILED }

class ActViewModel(
    private val usageStatsReader: UsageStatsReader,
    private val alternatives: AlternativesRepository,
    private val privateDns: PrivateDnsRepository,
    private val shieldController: ShieldController,
) : ViewModel() {

    val shield: StateFlow<ShieldState> = shieldController.state

    /** Domaines de la liste de blocage (`null` le temps de la lire). */
    private val _blocklistSize = MutableStateFlow<Int?>(null)
    val blocklistSize: StateFlow<Int?> = _blocklistSize.asStateFlow()

    private val _otherVpn = MutableStateFlow(false)
    val otherVpn: StateFlow<Boolean> = _otherVpn.asStateFlow()

    init {
        // Lecture anticipée : le bouclier démarre ensuite sans attendre la liste.
        viewModelScope.launch { _blocklistSize.value = withContext(Dispatchers.IO) { runCatching { shieldController.blocklist().size }.getOrNull() } }
    }

    /** Intention d'accord VPN à lancer avant [startShield], ou `null` si l'accord est déjà donné. */
    fun shieldConsentIntent() = shieldController.consentIntent()

    fun startShield() = shieldController.start()

    fun stopShield() = shieldController.stop()

    fun pauseShield() = shieldController.pause()

    fun resumeShield() = shieldController.resume()

    fun setShieldPauseMinutes(minutes: Int) = shieldController.setPauseMinutes(minutes)

    fun setShieldFilter(filter: ShieldFilter, enabled: Boolean) = shieldController.setFilter(filter, enabled)

    fun refreshShieldContext() {
        _otherVpn.value = shieldController.otherVpnActive()
    }

    val dnsCatalogue: StateFlow<PrivateDnsCatalogue> = privateDns.catalogue

    private val _dnsStatus = MutableStateFlow<PrivateDnsStatus>(PrivateDnsStatus.NoNetwork)
    val dnsStatus: StateFlow<PrivateDnsStatus> = _dnsStatus.asStateFlow()

    private val _dnsListUpdate = MutableStateFlow<DnsListUpdate?>(null)
    val dnsListUpdate: StateFlow<DnsListUpdate?> = _dnsListUpdate.asStateFlow()

    /** Au retour des réglages Android : le DNS privé vient peut-être d'être activé ou désactivé. */
    fun refreshDnsStatus() {
        _dnsStatus.value = privateDns.status()
    }

    fun updateDnsList() {
        if (_dnsListUpdate.value == DnsListUpdate.RUNNING) return
        viewModelScope.launch {
            _dnsListUpdate.value = DnsListUpdate.RUNNING
            _dnsListUpdate.value = privateDns.update().fold(
                onSuccess = { changed -> if (changed) DnsListUpdate.UPDATED else DnsListUpdate.UNCHANGED },
                onFailure = { DnsListUpdate.FAILED },
            )
        }
    }

    private val _usage = MutableStateFlow(UsageState())
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    val catalogue: AlternativeCatalogue get() = alternatives.catalogue

    /** À chaque retour sur l'écran : l'utilisateur vient peut-être d'accorder l'accès dans les réglages. */
    fun refreshUsage() {
        viewModelScope.launch {
            _usage.value = withContext(Dispatchers.IO) {
                val lastUsed = usageStatsReader.lastUsed()
                UsageState(hasAccess = lastUsed != null, lastUsed = lastUsed)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as VyliaApplication).container
                ActViewModel(
                    container.usageStatsReader,
                    container.alternativesRepository,
                    container.privateDnsRepository,
                    container.shieldController,
                )
            }
        }
    }
}
