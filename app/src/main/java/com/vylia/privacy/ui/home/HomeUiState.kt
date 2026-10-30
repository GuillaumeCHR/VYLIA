package com.vylia.privacy.ui.home

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore

data class HomeUiState(
    val isLoading: Boolean = true,
    val selectedFilter: AppFilter = AppFilter.USER,
    /** Apps de l'onglet sélectionné, de la plus à risque à la moins à risque. */
    val apps: List<AuditedApp> = emptyList(),
    /** Score moyen de l'onglet sélectionné ; `null` tant qu'il n'y a aucune app. */
    val averageScore: PrivacyScore? = null,
    val userAppCount: Int = 0,
    val systemAppCount: Int = 0,
    val errorMessage: String? = null,
)
