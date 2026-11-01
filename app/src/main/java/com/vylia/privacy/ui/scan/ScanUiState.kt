package com.vylia.privacy.ui.scan

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore

data class ScanUiState(
    val isLoading: Boolean = true,
    /** Mode Basique (`false`) : seules les apps utilisateur sont scannées et l'onglet Système est masqué. */
    val advancedMode: Boolean = false,
    val selectedFilter: AppFilter = AppFilter.USER,
    /** Apps de l'onglet sélectionné, de la plus à risque à la moins à risque. */
    val apps: List<AuditedApp> = emptyList(),
    /** Toutes les apps du scan (les deux onglets en mode Avancé) : écran Analyse et détail d'une app. */
    val scannedApps: List<AuditedApp> = emptyList(),
    /** Score moyen de l'onglet sélectionné ; `null` tant qu'il n'y a aucune app. */
    val averageScore: PrivacyScore? = null,
    /** Score moyen par type d'apps, quel que soit l'onglet (écran Analyse). */
    val userAverageScore: PrivacyScore? = null,
    val systemAverageScore: PrivacyScore? = null,
    val userAppCount: Int = 0,
    val systemAppCount: Int = 0,
    /** Analyse DEX des traqueurs encore en cours (les scores peuvent encore baisser). */
    val isAnalyzingTrackers: Boolean = false,
    /** Fin du dernier scan complet, conservée entre deux lancements ; `null` avant le premier. */
    val lastScanAt: Long? = null,
    val errorMessage: String? = null,
)
