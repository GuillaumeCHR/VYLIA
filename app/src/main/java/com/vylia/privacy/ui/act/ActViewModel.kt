package com.vylia.privacy.ui.act

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.data.alternatives.AlternativesRepository
import com.vylia.privacy.data.apps.UsageStatsReader
import com.vylia.privacy.domain.act.AlternativeCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Accès aux données d'utilisation et dernière utilisation de chaque app (`null` tant qu'il n'est pas accordé). */
data class UsageState(val hasAccess: Boolean = false, val lastUsed: Map<String, Long>? = null)

/** Données propres à l'onglet Agir ; les apps, les règles et les récompenses viennent de ScanViewModel. */
class ActViewModel(
    private val usageStatsReader: UsageStatsReader,
    private val alternatives: AlternativesRepository,
) : ViewModel() {

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
                ActViewModel(container.usageStatsReader, container.alternativesRepository)
            }
        }
    }
}
