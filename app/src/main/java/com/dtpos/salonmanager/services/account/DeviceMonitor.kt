package com.dtpos.salonmanager.services.account

import android.annotation.SuppressLint
import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
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
 * it was last seen and the phone's location (the app opens only with location allowed, see
 * LocationGate). No salon data. Written to devices/{uid}__{deviceId} at once and then every
 * [INTERVAL_MS] while the app is open; Firestore sends it when online. The IP address is not
 * collected: the app has no server of its own that could see it.
 */
class DeviceMonitor(
    private val app: Context,
    private val accountManager: AccountManager,
    private val scope: CoroutineScope,
    private val versionName: String,
) {
    @Volatile private var started = false
    private val kicks = MutableStateFlow(0)

    /** Reports right away (e.g. just after location was allowed). */
    fun reportNow() {
        kicks.value = kicks.value + 1
    }

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
            combine(uid, kicks) { u, _ -> u }.collectLatest { u ->
                if (u == null) return@collectLatest
                while (true) {
                    try {
                        report(u)
                    } catch (e: Exception) {
                        // Best effort; never disturbs the salon.
                    }
                    delay(INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun report(uid: String) {
        val deviceId = accountManager.deviceId
        val permission = when {
            !LocationAccess.hasPermission(app) -> "denied"
            !LocationAccess.servicesOn(app) -> "off"
            else -> "granted"
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
        }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(docId(uid, deviceId)).set(data, SetOptions.merge())
    }

    /**
     * A fresh fix from the best enabled provider (network, fused, GPS), falling back to the most
     * recent known location. Real readings only; null when the phone cannot get any.
     */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val enabled = try { manager.getProviders(true) } catch (e: Exception) { emptyList() }
        val order = listOf(LocationManager.NETWORK_PROVIDER, FUSED, LocationManager.GPS_PROVIDER).filter { it in enabled }
        for (provider in order) {
            val fix = withTimeoutOrNull(if (provider == LocationManager.GPS_PROVIDER) GPS_TIMEOUT_MS else FIX_TIMEOUT_MS) { freshFix(manager, provider) }
            if (fix != null) return fix
        }
        return enabled.mapNotNull { p -> try { manager.getLastKnownLocation(p) } catch (e: Exception) { null } }.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, Executor { it.run() }) { location -> if (cont.isActive) cont.resume(location) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    companion object {
        const val COLLECTION = "devices"
        private const val INTERVAL_MS = 30 * 60 * 1000L
        private const val FIX_TIMEOUT_MS = 10_000L
        private const val GPS_TIMEOUT_MS = 20_000L
        private const val FUSED = "fused"

        fun docId(uid: String, deviceId: String) = "${uid}__$deviceId"
    }
}
