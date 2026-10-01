package com.dtpos.salonmanager

import android.app.Application
import android.content.Context
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.services.prefs.UiPreferences
import kotlinx.coroutines.launch

class SalonApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Strings used outside activities (receipts, exports) follow the chosen app language. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(UiPreferences.wrapWithLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.initialize() }
    }
}
