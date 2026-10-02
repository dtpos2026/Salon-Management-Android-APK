package com.dtpos.salonmanager.services.account

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Local copy of the account, branding and app config so the salon app opens offline.
 * Stored in private preferences (not in the salon database, so backups do not carry it).
 */
class AccountCache(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Entry(val account: CloudAccount, val verifiedAtMillis: Long)

    /** Account that owns the salon data on this phone. */
    data class DeviceOwner(val uid: String, val email: String)

    fun account(uid: String): Entry? {
        if (prefs.getString(KEY_ACCOUNT_UID, null) != uid) return null
        val json = prefs.getString(KEY_ACCOUNT, null) ?: return null
        return try {
            Entry(AccountParser.account(uid, JSONObject(json).toMap()), prefs.getLong(KEY_VERIFIED_AT, 0L))
        } catch (e: Exception) {
            null
        }
    }

    fun putAccount(account: CloudAccount, verifiedAtMillis: Long) {
        prefs.edit()
            .putString(KEY_ACCOUNT_UID, account.uid)
            .putString(KEY_ACCOUNT, account.toJson().toString())
            .putLong(KEY_VERIFIED_AT, verifiedAtMillis)
            .remove(KEY_NO_PROFILE_UID)
            .apply()
    }

    /** Server confirmed that [uid] has no account document yet. */
    fun markNoProfile(uid: String) {
        prefs.edit().putString(KEY_NO_PROFILE_UID, uid).remove(KEY_ACCOUNT).remove(KEY_ACCOUNT_UID).apply()
    }

    fun hasNoProfile(uid: String): Boolean = prefs.getString(KEY_NO_PROFILE_UID, null) == uid

    fun clearAccount() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_ACCOUNT_UID).remove(KEY_VERIFIED_AT).remove(KEY_NO_PROFILE_UID).apply()
    }

    var deviceOwner: DeviceOwner?
        get() {
            val uid = prefs.getString(KEY_OWNER_UID, null) ?: return null
            return DeviceOwner(uid, prefs.getString(KEY_OWNER_EMAIL, null).orEmpty())
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_OWNER_UID).remove(KEY_OWNER_EMAIL)
                else putString(KEY_OWNER_UID, value.uid).putString(KEY_OWNER_EMAIL, value.email)
            }.apply()
        }

    var branding: Branding
        get() = prefs.getString(KEY_BRANDING, null)?.let { json ->
            try { AccountParser.branding(JSONObject(json).toMap()) } catch (e: Exception) { null }
        } ?: Branding()
        set(value) = prefs.edit().putString(KEY_BRANDING, value.toJson().toString()).apply()

    var appConfig: RemoteAppConfig
        get() = prefs.getString(KEY_CONFIG, null)?.let { json ->
            try { AccountParser.appConfig(JSONObject(json).toMap()) } catch (e: Exception) { null }
        } ?: RemoteAppConfig()
        set(value) = prefs.edit().putString(KEY_CONFIG, value.toJson().toString()).apply()

    var lastHeartbeatMillis: Long
        get() = prefs.getLong(KEY_HEARTBEAT, 0L)
        set(value) = prefs.edit().putLong(KEY_HEARTBEAT, value).apply()

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> opt(key).takeUnless { it == JSONObject.NULL } }

    private fun CloudAccount.toJson() = JSONObject().apply {
        put("email", email); put("displayName", displayName); put("salonName", salonName)
        put("ownerName", ownerName); put("phone", phone); put("city", city); put("address", address)
        put("status", status.name); put("customerId", customerId); put("businessId", businessId)
        put("licenseId", licenseId); put("plan", plan); put("expiresAt", expiresAtMillis)
        put("monthlyFee", monthlyFee); put("paymentStatus", paymentStatus)
        put("pendingAmount", pendingAmount); put("messageToUser", messageToUser)
        put("deviceId", deviceId); put("deviceModel", deviceModel)
        put("pendingDeviceId", pendingDeviceId); put("pendingDeviceModel", pendingDeviceModel)
    }

    private fun Branding.toJson() = JSONObject().apply {
        put("appName", appName); put("companyName", companyName); put("contactNumber", contactNumber)
        put("whatsapp", whatsapp); put("email", email); put("supportText", supportText); put("website", website)
    }

    private fun RemoteAppConfig.toJson() = JSONObject().apply {
        put("latestVersionCode", latestVersionCode); put("latestVersionName", latestVersionName)
        put("minVersionCode", minVersionCode); put("updateUrl", updateUrl); put("updateMessage", updateMessage)
        put("notice", notice); put("offlineGraceDays", offlineGraceDays)
    }

    private companion object {
        const val PREFS = "dt_account"
        const val KEY_ACCOUNT = "account.json"
        const val KEY_ACCOUNT_UID = "account.uid"
        const val KEY_VERIFIED_AT = "account.verifiedAt"
        const val KEY_NO_PROFILE_UID = "account.noProfileUid"
        const val KEY_OWNER_UID = "device.ownerUid"
        const val KEY_OWNER_EMAIL = "device.ownerEmail"
        const val KEY_BRANDING = "config.branding"
        const val KEY_CONFIG = "config.app"
        const val KEY_HEARTBEAT = "account.heartbeatAt"
    }
}
