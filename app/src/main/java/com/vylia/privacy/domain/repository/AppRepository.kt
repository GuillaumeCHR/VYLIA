package com.vylia.privacy.domain.repository

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.InstalledApp

/** Source des applications installées. Implémentée par [com.vylia.privacy.data.AppScanner]. */
interface AppRepository {
    suspend fun getInstalledApps(filter: AppFilter = AppFilter.ALL): List<InstalledApp>
}
