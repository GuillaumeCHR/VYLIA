package com.vylia.privacy

import android.app.Application
import com.vylia.privacy.di.AppContainer

class VyliaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
