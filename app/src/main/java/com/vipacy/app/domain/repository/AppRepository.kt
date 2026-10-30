package com.vipacy.app.domain.repository

import com.vipacy.app.domain.model.AppFilter
import com.vipacy.app.domain.model.InstalledApp

/** Source des applications installées. Implémentée par [com.vipacy.app.data.AppScanner]. */
interface AppRepository {
    suspend fun getInstalledApps(filter: AppFilter = AppFilter.ALL): List<InstalledApp>
}
