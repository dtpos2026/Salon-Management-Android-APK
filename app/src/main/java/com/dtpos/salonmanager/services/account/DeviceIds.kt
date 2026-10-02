package com.dtpos.salonmanager.services.account

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Identifies this phone for device approval. Uses ANDROID_ID (stable for this app on this phone,
 * also after reinstalling an APK signed with the same key), else a random id kept in preferences.
 */
object DeviceIds {
    private const val PREFS = "dt_device"
    private const val KEY = "device.id"

    @SuppressLint("HardwareIds")
    fun forThisPhone(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
        if (!androidId.isNullOrBlank() && androidId.length >= 8 && androidId != BROKEN_ANDROID_ID) return "a-$androidId"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val id = "r-" + UUID.randomUUID().toString()
        prefs.edit().putString(KEY, id).apply()
        return id
    }

    /** Value some old emulators and builds returned for every device. */
    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"
}
