package com.dtpos.salonmanager

import android.app.Application
import com.dtpos.salonmanager.core.di.AppContainer
import kotlinx.coroutines.launch

class SalonApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.initialize() }
    }
}
