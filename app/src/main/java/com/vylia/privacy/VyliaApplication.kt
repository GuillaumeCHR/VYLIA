package com.vylia.privacy

import android.app.Application
import com.vylia.privacy.data.shield.ShieldController
import com.vylia.privacy.data.shield.ShieldHost
import com.vylia.privacy.di.AppContainer

class VyliaApplication : Application(), ShieldHost {
    lateinit var container: AppContainer
        private set

    /** Pour le service VPN et la tuile du module :shield. */
    override val shieldController: ShieldController get() = container.shieldController

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.automationScheduler.sync()
    }
}
