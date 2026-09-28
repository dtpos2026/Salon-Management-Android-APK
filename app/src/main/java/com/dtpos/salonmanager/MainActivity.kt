package com.dtpos.salonmanager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.CompositionLocalProvider
import com.dtpos.salonmanager.presentation.SalonAppRoot
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.launch

/**
 * Single activity. Extends FragmentActivity (a ComponentActivity) because the AndroidX
 * BiometricPrompt needs it.
 */
class MainActivity : FragmentActivity() {

    private val container get() = (application as SalonApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !container.ready.value }
        enableEdgeToEdge()
        setContent {
            SalonTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    SalonAppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.securityManager.onAppForegrounded()
        lifecycleScope.launch {
            try {
                container.licenseManager.refresh()
            } catch (e: Exception) {
                // Licence refresh is best-effort and never blocks the UI.
            }
        }
    }

    override fun onStop() {
        container.securityManager.onAppBackgrounded()
        super.onStop()
    }
}
