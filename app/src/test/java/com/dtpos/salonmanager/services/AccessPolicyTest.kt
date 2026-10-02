package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.core.util.PhoneNumbers
import com.dtpos.salonmanager.services.account.AccessPolicy
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountParser
import com.dtpos.salonmanager.services.account.AccountStatus
import com.dtpos.salonmanager.services.account.CloudAccount
import com.dtpos.salonmanager.services.account.RemoteAppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AccessPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L
    private val approved = CloudAccount(uid = "u1", status = AccountStatus.APPROVED, expiresAtMillis = now + 30 * day)

    @Test
    fun `approved account opens within the offline grace period`() {
        assertTrue(AccessPolicy.decide(approved, verifiedAtMillis = now - 5 * day, nowMillis = now, offlineGraceDays = 30, deviceId = PHONE) is AccessState.Allowed)
    }

    @Test
    fun `after the grace period the phone must go online once`() {
        val state = AccessPolicy.decide(approved, verifiedAtMillis = now - 31 * day, nowMillis = now, offlineGraceDays = 30, deviceId = PHONE)
        assertTrue(state is AccessState.NeedsVerification)
        assertEquals(31, (state as AccessState.NeedsVerification).daysSinceVerified)
    }

    @Test
    fun `moving the clock backwards needs online verification`() {
        val state = AccessPolicy.decide(approved, verifiedAtMillis = now, nowMillis = now - 3 * day, offlineGraceDays = 30, deviceId = PHONE)
        assertTrue(state is AccessState.NeedsVerification)
        // Small drift is tolerated.
        assertTrue(AccessPolicy.decide(approved, verifiedAtMillis = now, nowMillis = now - day, offlineGraceDays = 30, deviceId = PHONE) is AccessState.Allowed)
    }

    @Test
    fun `licence expiry turns an approved account into expired even offline`() {
        val expired = approved.copy(expiresAtMillis = now - 1)
        assertEquals(AccountStatus.EXPIRED, AccessPolicy.effectiveStatus(expired, now))
        val state = AccessPolicy.decide(expired, verifiedAtMillis = now - day, nowMillis = now, offlineGraceDays = 30, deviceId = PHONE)
        assertEquals(AccountStatus.EXPIRED, (state as AccessState.Restricted).status)
        // No expiry date means no expiry.
        assertEquals(AccountStatus.APPROVED, AccessPolicy.effectiveStatus(approved.copy(expiresAtMillis = null), now))
    }

    @Test
    fun `every non approved status keeps the app closed`() {
        listOf(
            AccountStatus.PENDING, AccountStatus.PAYMENT_PENDING, AccountStatus.SUSPENDED,
            AccountStatus.BLOCKED, AccountStatus.EXPIRED, AccountStatus.REJECTED,
        ).forEach { status ->
            val state = AccessPolicy.decide(approved.copy(status = status), now, now, 30, PHONE)
            assertEquals(status, (state as AccessState.Restricted).status)
        }
    }

    @Test
    fun `update rules`() {
        val config = RemoteAppConfig(latestVersionCode = 5, minVersionCode = 3, updateUrl = "https://example.com/app.apk")
        assertTrue(AccessPolicy.updateRequired(config, currentVersionCode = 2))
        assertFalse(AccessPolicy.updateRequired(config, currentVersionCode = 3))
        assertTrue(AccessPolicy.updateAvailable(config, currentVersionCode = 4))
        assertFalse(AccessPolicy.updateAvailable(config.copy(updateUrl = ""), currentVersionCode = 4))
        assertFalse(AccessPolicy.updateAvailable(config, currentVersionCode = 5))
    }

    @Test
    fun `parser reads admin fields and tolerates bad values`() {
        val account = AccountParser.account(
            "u9",
            mapOf(
                "email" to "salon@gmail.com", "salonName" to " Royal Cuts ", "status" to "payment_pending",
                "customerId" to "DTC-0007", "expiresAt" to 1_900_000_000_000L, "monthlyFee" to 1500.0,
                "pendingAmount" to "3000", "plan" to "YEARLY", "messageToUser" to "",
            ),
        )
        assertEquals("Royal Cuts", account.salonName)
        assertEquals(AccountStatus.PAYMENT_PENDING, account.status)
        assertEquals(1_900_000_000_000L, account.expiresAtMillis)
        assertEquals(1500L, account.monthlyFee)
        assertEquals(3000L, account.pendingAmount)
        assertNull(account.messageToUser)
        assertEquals(AccountStatus.PENDING, AccountStatus.parse("something-unknown"))
        assertEquals(365, AccountParser.appConfig(mapOf("offlineGraceDays" to 9999)).offlineGraceDays)
    }

    @Test
    fun `whatsapp numbers are normalised to international format`() {
        assertEquals("923001234567", PhoneNumbers.toWhatsApp("0300-1234567"))
        assertEquals("923001234567", PhoneNumbers.toWhatsApp("+92 300 1234567"))
        assertEquals("923001234567", PhoneNumbers.toWhatsApp("0092 300 1234567"))
        assertEquals("923001234567", PhoneNumbers.toWhatsApp("3001234567"))
        assertEquals("923001234567", PhoneNumbers.toWhatsApp("923001234567"))
        assertEquals("447700900123", PhoneNumbers.toWhatsApp("+44 7700 900123"))
        assertNull(PhoneNumbers.toWhatsApp("12345"))
        assertNull(PhoneNumbers.toWhatsApp(""))
        assertNull(PhoneNumbers.toWhatsApp(null))
    }

    @Test
    fun `an account approved for another phone waits for the admin`() {
        val bound = approved.copy(deviceId = "a-phone-one", deviceModel = "Samsung A15")
        assertTrue(AccessPolicy.decide(bound.copy(deviceId = PHONE), now, now, 30, PHONE) is AccessState.Allowed)
        val waiting = AccessPolicy.decide(bound, now, now, 30, PHONE) as AccessState.DeviceNotApproved
        assertEquals(false, waiting.requested)
        val requested = AccessPolicy.decide(bound.copy(pendingDeviceId = PHONE), now, now, 30, PHONE) as AccessState.DeviceNotApproved
        assertEquals(true, requested.requested)
        // Several phones approved by the admin.
        val two = bound.copy(deviceIds = listOf("a-phone-one", PHONE))
        assertTrue(AccessPolicy.decide(two, now, now, 30, PHONE) is AccessState.Allowed)
        assertTrue(AccessPolicy.decide(two.copy(deviceIds = listOf("none")), now, now, 30, PHONE) is AccessState.DeviceNotApproved)
        // Older accounts without a bound phone keep working.
        assertTrue(AccessPolicy.decide(approved.copy(deviceId = null), now, now, 30, PHONE) is AccessState.Allowed)
    }

    private companion object {
        const val PHONE = "a-this-phone-123"
    }
}
