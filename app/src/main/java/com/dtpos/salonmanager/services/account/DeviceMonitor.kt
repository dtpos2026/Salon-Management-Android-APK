package com.dtpos.salonmanager.services.account

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Tells the Super Admin which phones use an account: model, Android version, app version, when
 * it was last seen, and (only when the owner turned on "Share this phone's location") the
 * phone's approximate location. No salon data. Written to devices/{uid}__{deviceId} every
 * [INTERVAL_MS] while the app is open; Firestore sends it when online. The IP address is not
 * collected: the app has no server of its own that could see it.
 */
class DeviceMonitor(
    private val app: Context,
    private val accountManager: AccountManager,
    private val shareLocation: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val versionName: String,
) {
    @Volatile private var started = false

    private val configured: Boolean
        get() = try { FirebaseApp.getApps(app).isNotEmpty() } catch (e: Exception) { false }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started || !configured) return
        started = true
        val uid = accountManager.state.map { state ->
            when (state) {
                is AccessState.Allowed, is AccessState.DeviceBlocked, is AccessState.DeviceNotApproved,
                is AccessState.Restricted, is AccessState.NeedsVerification -> accountManager.currentUser()?.uid
                else -> null
            }
        }.distinctUntilChanged()
        scope.launch {
            combine(uid, shareLocation.distinctUntilChanged()) { u, share -> u to share }.collectLatest { (u, share) ->
                if (u == null) return@collectLatest
                while (true) {
                    try {
                        report(u, share)
                    } catch (e: Exception) {
                        // Best effort; never disturbs the salon.
                    }
                    delay(INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun report(uid: String, share: Boolean) {
        val deviceId = accountManager.deviceId
        val permission = when {
            !share -> "off"
            hasPermission() -> "granted"
            else -> "denied"
        }
        val location = if (permission == "granted") currentLocation() else null
        val data = mutableMapOf<String, Any?>(
            "uid" to uid,
            "deviceId" to deviceId,
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120),
            "platform" to "android",
            "osVersion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})".take(40),
            "appVersion" to versionName.take(40),
            "locationPermission" to permission,
            "lastSeenAt" to FieldValue.serverTimestamp(),
        )
        if (location != null) {
            data["lat"] = location.latitude
            data["lng"] = location.longitude
            data["accuracyM"] = location.accuracy.toDouble()
            data["locationAt"] = FieldValue.serverTimestamp()
        } else if (!share) {
            // Sharing turned off: remove the last location from the admin's map.
            data["lat"] = FieldValue.delete()
            data["lng"] = FieldValue.delete()
            data["accuracyM"] = FieldValue.delete()
            data["locationAt"] = FieldValue.delete()
        }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(docId(uid, deviceId)).set(data, SetOptions.merge())
    }

    private fun hasPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh approximate fix (network first), falling back to the last known one. Real readings only. */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = try { manager.getProviders(true) } catch (e: Exception) { emptyList() }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { it in providers }
        val fresh = provider?.let { p ->
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val main = Executor { it.run() }
                            manager.getCurrentLocation(p, null, main) { location -> if (cont.isActive) cont.resume(location) }
                        } else {
                            val listener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    if (cont.isActive) cont.resume(location)
                                }
                            }
                            @Suppress("DEPRECATION")
                            manager.requestSingleUpdate(p, listener, Looper.getMainLooper())
                            cont.invokeOnCancellation { manager.removeUpdates(listener) }
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        if (fresh != null) return fresh
        return providers.mapNotNull { p -> try { manager.getLastKnownLocation(p) } catch (e: Exception) { null } }.maxByOrNull { it.time }
    }

    companion object {
        const val COLLECTION = "devices"
        private const val INTERVAL_MS = 30 * 60 * 1000L
        private const val FIX_TIMEOUT_MS = 15_000L

        fun docId(uid: String, deviceId: String) = "${uid}__$deviceId"
    }
}
