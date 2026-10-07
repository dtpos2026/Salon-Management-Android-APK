package com.dtpos.salonmanager.services.account

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

/** Where the phone stands with location, which the app needs before it opens. */
enum class LocationStatus {
    /** Permission granted and location turned on: the app may open. */
    READY,
    /** The app has not been allowed to use location yet. */
    NEEDS_PERMISSION,
    /** Only "approximate" location was allowed (Android 12+): it can be kilometres off. */
    NEEDS_PRECISE,
    /** Allowed, but location is switched off in the phone's quick settings. */
    SERVICES_OFF,
}

object LocationAccess {
    /** Asked together; on Android 12+ the owner must pick "Precise" (approximate is not enough). */
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** Exact (GPS) location allowed, so the Super Admin map shows where the phone really is. */
    fun hasPrecise(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * True when location is switched on. Phones without any location provider (some tablets)
     * count as on, so they are never locked out by hardware they do not have.
     */
    fun servicesOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                manager.isLocationEnabled
            } else {
                val providers = manager.allProviders.filter { it != LocationManager.PASSIVE_PROVIDER }
                providers.isEmpty() || providers.any { manager.isProviderEnabled(it) }
            }
        } catch (e: Exception) {
            true
        }
    }

    fun status(context: Context): LocationStatus = when {
        !hasPermission(context) -> LocationStatus.NEEDS_PERMISSION
        !hasPrecise(context) -> LocationStatus.NEEDS_PRECISE
        !servicesOn(context) -> LocationStatus.SERVICES_OFF
        else -> LocationStatus.READY
    }
}
