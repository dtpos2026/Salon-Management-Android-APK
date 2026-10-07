package com.dtpos.salonmanager.services

import android.Manifest
import android.app.Application
import android.location.LocationManager
import com.dtpos.salonmanager.services.account.LocationAccess
import com.dtpos.salonmanager.services.account.LocationStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The location check that guards the app, on the oldest supported Android (8, providers) and a
 * current one (14, the system location switch). "Approximate" location alone is not enough.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = Application::class)
class LocationAccessTest {

    private lateinit var app: Application
    private lateinit var manager: LocationManager

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        manager = app.getSystemService(LocationManager::class.java)!!
    }

    /** Switches location on or off the way the running Android version does it. */
    private fun setLocation(on: Boolean) {
        val shadow = shadowOf(manager)
        shadow.setLocationEnabled(on)
        manager.allProviders.filter { it != LocationManager.PASSIVE_PROVIDER }.forEach { shadow.setProviderEnabled(it, on) }
    }

    @Test
    fun `without permission the app asks for it`() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        setLocation(true)
        assertEquals(LocationStatus.NEEDS_PERMISSION, LocationAccess.status(app))
    }

    @Test
    fun `approximate location alone asks for precise location`() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        setLocation(true)
        assertEquals(LocationStatus.NEEDS_PRECISE, LocationAccess.status(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(LocationStatus.READY, LocationAccess.status(app))
    }

    @Test
    fun `allowed but switched off asks to turn location on`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        setLocation(false)
        assertEquals(LocationStatus.SERVICES_OFF, LocationAccess.status(app))
        setLocation(true)
        assertEquals(LocationStatus.READY, LocationAccess.status(app))
    }
}
