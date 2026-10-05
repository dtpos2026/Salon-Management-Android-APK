package com.dtpos.salonmanager.services.account

import java.util.concurrent.TimeUnit

/** Whether this phone may open the salon app right now, and if not, why. */
sealed interface AccessState {
    /** Startup: Firebase / cached account still being read. */
    data object Loading : AccessState

    /** The build has no Firebase configuration (google-services.json was missing). */
    data object NotConfigured : AccessState

    data object SignedOut : AccessState

    /** Signed in but the salon has not registered yet. */
    data class NeedsProfile(val user: SignedInUser) : AccessState

    /** Account exists but may not use the app (pending, suspended, expired, ...). */
    data class Restricted(val account: CloudAccount, val status: AccountStatus) : AccessState

    /** Approved, but the phone must reach the server once (offline grace used up, or first sign-in offline). */
    data class NeedsVerification(val account: CloudAccount?, val daysSinceVerified: Int?) : AccessState

    /** The Super Admin blocked this phone (the salon data on it stays; unblocking opens it again). */
    data class DeviceBlocked(val account: CloudAccount) : AccessState

    /** The account is approved for another phone; [requested] = this phone already asked the admin. */
    data class DeviceNotApproved(val account: CloudAccount, val requested: Boolean) : AccessState

    /** Another account owns the salon data on this phone. */
    data class WrongDevice(val user: SignedInUser, val ownerEmail: String) : AccessState

    data class UpdateRequired(val config: RemoteAppConfig) : AccessState

    data class Allowed(val account: CloudAccount) : AccessState
}

/** Pure decision logic, unit tested in AccessPolicyTest. */
object AccessPolicy {

    /** Device clock may drift; a larger step backwards is treated as tampering. */
    private val CLOCK_TOLERANCE_MS = TimeUnit.DAYS.toMillis(2)

    fun effectiveStatus(account: CloudAccount, nowMillis: Long): AccountStatus {
        val expired = account.expiresAtMillis?.let { nowMillis > it } == true
        return if (account.status == AccountStatus.APPROVED && expired) AccountStatus.EXPIRED else account.status
    }

    fun daysSince(verifiedAtMillis: Long, nowMillis: Long): Int =
        TimeUnit.MILLISECONDS.toDays((nowMillis - verifiedAtMillis).coerceAtLeast(0)).toInt()

    /**
     * @param verifiedAtMillis when the account was last read from the server.
     * @param deviceId this phone (see [DeviceIds]).
     */
    fun decide(account: CloudAccount, verifiedAtMillis: Long, nowMillis: Long, offlineGraceDays: Int, deviceId: String): AccessState {
        if (account.blocks(deviceId)) return AccessState.DeviceBlocked(account)
        if (!account.approvedFor(deviceId)) {
            return AccessState.DeviceNotApproved(account, requested = account.pendingDeviceId == deviceId)
        }
        val status = effectiveStatus(account, nowMillis)
        if (status != AccountStatus.APPROVED) return AccessState.Restricted(account, status)
        val clockMovedBack = nowMillis < verifiedAtMillis - CLOCK_TOLERANCE_MS
        val graceUsedUp = nowMillis - verifiedAtMillis > TimeUnit.DAYS.toMillis(offlineGraceDays.toLong())
        return if (clockMovedBack || graceUsedUp) {
            AccessState.NeedsVerification(account, daysSince(verifiedAtMillis, nowMillis))
        } else {
            AccessState.Allowed(account)
        }
    }

    fun updateRequired(config: RemoteAppConfig, currentVersionCode: Int): Boolean =
        config.minVersionCode > currentVersionCode

    fun updateAvailable(config: RemoteAppConfig, currentVersionCode: Int): Boolean =
        config.latestVersionCode > currentVersionCode && config.updateUrl.isNotBlank()
}
