package com.dtpos.salonmanager

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dtpos.salonmanager.presentation.SalonAppRoot
import com.dtpos.salonmanager.presentation.account.AccessGate
import com.dtpos.salonmanager.presentation.account.SystemBarIcons
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.prefs.ThemeMode
import com.dtpos.salonmanager.services.prefs.UiPreferences
import kotlinx.coroutines.launch

/**
 * Single activity. Extends FragmentActivity (a ComponentActivity) because the AndroidX
 * BiometricPrompt needs it.
 */
class MainActivity : FragmentActivity() {

    private val container get() = (application as SalonApplication).container

    private var lastAccountCheck = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPreferences.wrapWithLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !container.ready.value }
        enableEdgeToEdge()
        setContent {
            val themeMode by container.uiPreferences.themeMode.collectAsStateWithLifecycle()
            val colorTheme by container.uiPreferences.colorTheme.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SalonTheme(darkTheme = dark, colorTheme = colorTheme) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AccessGate {
                        SystemBarIcons(lightBackground = !dark)
                        SalonAppRoot()
                    }
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
        // Re-check the account when the app returns to the foreground (at most every 10 minutes).
        val now = System.currentTimeMillis()
        if (container.ready.value && now - lastAccountCheck > ACCOUNT_CHECK_INTERVAL_MS) {
            lastAccountCheck = now
            lifecycleScope.launch {
                try {
                    container.accountManager.refresh()
                } catch (e: Exception) {
                    // Offline is normal; the cached decision stays in place.
                }
            }
        }
    }

    override fun onStop() {
        container.securityManager.onAppBackgrounded()
        super.onStop()
    }

    private companion object {
        const val ACCOUNT_CHECK_INTERVAL_MS = 10 * 60 * 1000L
    }
}
