package com.vylia.privacy.domain.repository

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.InstalledApp

/** Source des applications installées. Implémentée par [com.vylia.privacy.data.AppScanner]. */
interface AppRepository {
    /** @param only si non nul, seuls ces paquets sont chargés (notification d'installation). */
    suspend fun getInstalledApps(filter: AppFilter = AppFilter.ALL, only: Set<String>? = null): List<InstalledApp>
}
