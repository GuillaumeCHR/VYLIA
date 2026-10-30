package com.vipacy.app

import android.app.Application
import com.vipacy.app.di.AppContainer

class VipacyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
