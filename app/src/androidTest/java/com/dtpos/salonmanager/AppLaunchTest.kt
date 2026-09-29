package com.dtpos.salonmanager

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test run on real emulators (Android 8 and 14 in CI): the app must start without crashing. */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @Test
    fun appStartsWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }
}
