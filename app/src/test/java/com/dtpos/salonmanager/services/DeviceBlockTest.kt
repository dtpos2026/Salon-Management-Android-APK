package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.services.account.AccessPolicy
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountParser
import com.dtpos.salonmanager.services.account.AccountStatus
import com.dtpos.salonmanager.services.account.CloudAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A phone blocked by the Super Admin cannot open the app; other phones of the account still can. */
class DeviceBlockTest {
    private val now = 1_800_000_000_000L
    private val account = CloudAccount(
        uid = "u1", status = AccountStatus.APPROVED, expiresAtMillis = now + 86_400_000L,
        deviceIds = listOf("a-phone-1", "a-phone-2"), blockedDeviceIds = listOf("a-phone-2"),
    )

    @Test
    fun `blocked phone is refused even with an approved account`() {
        assertTrue(AccessPolicy.decide(account, now, now, 7, "a-phone-2") is AccessState.DeviceBlocked)
        assertTrue(AccessPolicy.decide(account, now, now, 7, "a-phone-1") is AccessState.Allowed)
        // Unblocked by the admin: the phone opens again.
        assertTrue(AccessPolicy.decide(account.copy(blockedDeviceIds = emptyList()), now, now, 7, "a-phone-2") is AccessState.Allowed)
    }

    @Test
    fun `parser reads the blocked phones`() {
        val parsed = AccountParser.account("u1", mapOf("status" to "APPROVED", "blockedDeviceIds" to listOf("a-x", "", 5)))
        assertEquals(listOf("a-x"), parsed.blockedDeviceIds)
    }
}
