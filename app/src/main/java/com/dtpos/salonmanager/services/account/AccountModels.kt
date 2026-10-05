package com.dtpos.salonmanager.services.account

/**
 * Salon account kept in Firestore (accounts/{uid}) and managed from the DT Super Admin panel.
 * Only the account and its licence live online; all salon data stays in the local database.
 * Field names match firebase/firestore.rules and the admin panel.
 */
enum class AccountStatus {
    PENDING, APPROVED, PAYMENT_PENDING, SUSPENDED, BLOCKED, EXPIRED, REJECTED;

    companion object {
        fun parse(value: String?): AccountStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class CloudAccount(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val salonName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val city: String = "",
    val address: String = "",
    val status: AccountStatus = AccountStatus.PENDING,
    val customerId: String? = null,
    val businessId: String? = null,
    val licenseId: String? = null,
    val plan: String? = null,
    /** Licence end (epoch millis); null means no expiry. */
    val expiresAtMillis: Long? = null,
    val monthlyFee: Long? = null,
    val paymentStatus: String? = null,
    val pendingAmount: Long? = null,
    /** Message from the admin, shown on the status screen. */
    val messageToUser: String? = null,
    /** The phone the account registered from (see [DeviceIds]). Null = not bound (older accounts). */
    val deviceId: String? = null,
    val deviceModel: String? = null,
    /** Phones the admin approved; when set it replaces [deviceId]. Limited by maxDevices in the panel. */
    val deviceIds: List<String> = emptyList(),
    /** Another phone asking the admin for approval. */
    val pendingDeviceId: String? = null,
    val pendingDeviceModel: String? = null,
    /** Phones the Super Admin blocked. Checked on the server copy; salon data on the phone is kept. */
    val blockedDeviceIds: List<String> = emptyList(),
) {
    fun blocks(device: String): Boolean = device in blockedDeviceIds

    fun approvedFor(device: String): Boolean = when {
        deviceIds.isNotEmpty() -> device in deviceIds
        else -> deviceId == null || deviceId == device
    }
}

data class SignedInUser(
    val uid: String,
    val email: String?,
    val displayName: String? = null,
    val photoUrl: String? = null,
)

/** What a new salon owner enters before the account goes to the admin for approval. */
data class AccountRegistration(
    val salonName: String,
    val ownerName: String,
    val phone: String,
    val city: String,
    val address: String,
)

/** Public app branding (config/branding), editable from the admin panel. */
data class Branding(
    val appName: String = DEFAULT_APP_NAME,
    val companyName: String = DEFAULT_COMPANY,
    val contactNumber: String = "",
    val whatsapp: String = "",
    val email: String = "",
    val supportText: String = "",
    val website: String = "",
) {
    companion object {
        const val DEFAULT_APP_NAME = "DT Salon Management"
        const val DEFAULT_COMPANY = "Digital Target"
    }
}

/** App-wide settings from the admin panel (config/app). */
data class RemoteAppConfig(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minVersionCode: Int = 0,
    val updateUrl: String = "",
    val updateMessage: String = "",
    val notice: String = "",
    /** How long an approved phone may work without reaching the server. */
    val offlineGraceDays: Int = DEFAULT_OFFLINE_GRACE_DAYS,
) {
    companion object {
        /** The app re-checks the account with the server at least weekly. */
        const val DEFAULT_OFFLINE_GRACE_DAYS = 7
    }
}

/** Converts Firestore document data (timestamps already turned into epoch millis). */
object AccountParser {

    fun account(uid: String, data: Map<String, Any?>): CloudAccount = CloudAccount(
        uid = uid,
        email = data.str("email"),
        displayName = data.str("displayName"),
        salonName = data.str("salonName").orEmpty(),
        ownerName = data.str("ownerName").orEmpty(),
        phone = data.str("phone").orEmpty(),
        city = data.str("city").orEmpty(),
        address = data.str("address").orEmpty(),
        status = AccountStatus.parse(data.str("status")),
        customerId = data.str("customerId"),
        businessId = data.str("businessId"),
        licenseId = data.str("licenseId"),
        plan = data.str("plan"),
        expiresAtMillis = data.long("expiresAt"),
        monthlyFee = data.long("monthlyFee"),
        paymentStatus = data.str("paymentStatus"),
        pendingAmount = data.long("pendingAmount"),
        messageToUser = data.str("messageToUser"),
        deviceId = data.str("deviceId"),
        deviceModel = data.str("deviceModel"),
        deviceIds = (data["deviceIds"] as? Iterable<*>)?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.orEmpty(),
        pendingDeviceId = data.str("pendingDeviceId"),
        pendingDeviceModel = data.str("pendingDeviceModel"),
        blockedDeviceIds = (data["blockedDeviceIds"] as? Iterable<*>)?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.orEmpty(),
    )

    fun branding(data: Map<String, Any?>): Branding = Branding(
        appName = data.str("appName") ?: Branding.DEFAULT_APP_NAME,
        companyName = data.str("companyName") ?: Branding.DEFAULT_COMPANY,
        contactNumber = data.str("contactNumber").orEmpty(),
        whatsapp = data.str("whatsapp").orEmpty(),
        email = data.str("email").orEmpty(),
        supportText = data.str("supportText").orEmpty(),
        website = data.str("website").orEmpty(),
    )

    fun appConfig(data: Map<String, Any?>): RemoteAppConfig = RemoteAppConfig(
        latestVersionCode = data.long("latestVersionCode")?.toInt() ?: 0,
        latestVersionName = data.str("latestVersionName").orEmpty(),
        minVersionCode = data.long("minVersionCode")?.toInt() ?: 0,
        updateUrl = data.str("updateUrl").orEmpty(),
        updateMessage = data.str("updateMessage").orEmpty(),
        notice = data.str("notice").orEmpty(),
        offlineGraceDays = (data.long("offlineGraceDays")?.toInt() ?: RemoteAppConfig.DEFAULT_OFFLINE_GRACE_DAYS)
            .coerceIn(1, 365),
    )

    private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun Map<String, Any?>.long(key: String): Long? = when (val v = this[key]) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }
}
