package com.dtpos.salonmanager.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.repository.SettingsRepository
import com.dtpos.salonmanager.domain.license.LicenseCodec
import com.dtpos.salonmanager.domain.license.LicensePayload
import com.dtpos.salonmanager.domain.license.LicensePlan
import com.dtpos.salonmanager.domain.license.LicenseStatus
import com.dtpos.salonmanager.services.license.ActivationError
import com.dtpos.salonmanager.services.license.ActivationResult
import com.dtpos.salonmanager.services.license.LicenseConfig
import com.dtpos.salonmanager.services.license.LicenseManager
import com.dtpos.salonmanager.services.security.LockType
import com.dtpos.salonmanager.services.security.ProtectedArea
import com.dtpos.salonmanager.services.security.SecurityManager
import com.dtpos.salonmanager.services.security.StaffAccess
import com.dtpos.salonmanager.services.security.VerifyResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LicenseAndSecurityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = SalonDatabase.inMemory(context)
    private val keys = LicenseCodec.generateKeyPair()

    @After
    fun tearDown() = db.close()

    private fun licenseManager(enforced: Boolean = true) = LicenseManager(
        context,
        db.licenseDao(),
        LicenseConfig(enforced = enforced, trialDays = 30, publicKeyBase64 = LicenseCodec.publicKeyToBase64(keys.public)),
    )

    private fun key(deviceId: String, expires: java.time.LocalDate?) = LicenseCodec.sign(
        LicensePayload("LIC-1", "Royal Barber Shop", LicensePlan.YEAR_1, DateTimeUtils.today(), expires, deviceId),
        keys.private,
    )

    @Test
    fun `trial then activation with a device bound key`() = runTest {
        val manager = licenseManager()
        assertEquals(LicenseStatus.TRIAL, manager.refresh().status)
        val result = manager.activate(key(manager.installationId, DateTimeUtils.today().plusYears(1)))
        assertTrue(result is ActivationResult.Activated)
        assertEquals(LicenseStatus.ACTIVE, manager.state.value.status)
        assertFalse(manager.state.value.isReadOnly)
    }

    @Test
    fun `keys for other phones or already expired are rejected`() = runTest {
        val manager = licenseManager()
        assertEquals(
            ActivationResult.Rejected(ActivationError.WRONG_DEVICE),
            manager.activate(key("OTHER-PHONE-ID", DateTimeUtils.today().plusYears(1))),
        )
        assertEquals(
            ActivationResult.Rejected(ActivationError.EXPIRED),
            manager.activate(key("*", DateTimeUtils.today().minusDays(1))),
        )
        assertEquals(ActivationResult.Rejected(ActivationError.MALFORMED), manager.activate("garbage"))
    }

    @Test
    fun `owner edition is never read only`() = runTest {
        val manager = licenseManager(enforced = false)
        assertEquals(LicenseStatus.NOT_ENFORCED, manager.refresh().status)
        assertFalse(manager.state.value.isReadOnly)
    }

    @Test
    fun `pin lock verification lockout and recovery`() = runTest {
        val security = SecurityManager(SettingsRepository(db.settingsDao()), backgroundScope)
        val recovery = security.setCredential(LockType.PIN, "2468")
        val config = security.config.filterNotNull().first { it.isEnabled }
        assertTrue(config.protects(ProtectedArea.APP))

        security.lockNow()
        assertTrue(security.verify("1111") is VerifyResult.Wrong)
        assertEquals(VerifyResult.Success, security.verify("2468"))
        assertTrue(security.unlocked.value)

        repeat(4) { security.verify("0000") }
        assertTrue(security.verify("0000") is VerifyResult.LockedOut)
        // Even the right PIN is refused during the lockout.
        assertTrue(security.verify("2468") is VerifyResult.LockedOut)

        assertFalse(security.resetWithRecoveryCode("AAAA-BBBB-CCCC"))
        assertTrue(security.resetWithRecoveryCode(recovery.lowercase()))
        assertFalse(security.config.filterNotNull().first { !it.isEnabled }.isEnabled)
    }

    @Test
    fun `staff pins give manager and assistant only their role`() = runTest {
        val security = SecurityManager(SettingsRepository(db.settingsDao()), backgroundScope)
        // Staff PINs need the owner lock first.
        assertFalse(security.setStaffPin(StaffAccess.MANAGER, "5555"))
        security.setCredential(LockType.PIN, "2468")
        assertTrue(security.setStaffPin(StaffAccess.MANAGER, "5555"))
        assertTrue(security.setStaffPin(StaffAccess.ASSISTANT, "7777"))
        // A PIN always means one role.
        assertFalse(security.setStaffPin(StaffAccess.ASSISTANT, "2468"))
        assertFalse(security.setStaffPin(StaffAccess.ASSISTANT, "5555"))
        val config = security.config.filterNotNull().first { it.hasManagerPin && it.hasAssistantPin }
        assertTrue(config.protects(ProtectedArea.APP))
        assertTrue(config.protects(ProtectedArea.CLOSE_DAY))

        security.lockNow()
        assertEquals(VerifyResult.Success, security.verify("7777"))
        assertEquals(StaffAccess.ASSISTANT, security.access.value)
        assertFalse(security.allows(ProtectedArea.REPORTS))
        assertFalse(security.allows(ProtectedArea.CLOSE_DAY))
        assertTrue(security.allows(ProtectedArea.APP))
        // Where the owner is needed, a staff PIN is refused.
        assertTrue(security.verify("5555", ownerOnly = true) is VerifyResult.Wrong)
        assertEquals(VerifyResult.Success, security.verify("2468", ownerOnly = true))
        assertEquals(StaffAccess.OWNER, security.access.value)

        security.lockNow()
        assertEquals(VerifyResult.Success, security.verify("5555"))
        assertEquals(StaffAccess.MANAGER, security.access.value)
        assertTrue(security.allows(ProtectedArea.CLOSE_DAY))
        assertTrue(security.allows(ProtectedArea.REPORTS))
        assertFalse(security.allows(ProtectedArea.SETTINGS))

        // Turning the lock off removes the staff PINs too.
        security.disable()
        val off = security.config.filterNotNull().first { !it.isEnabled }
        assertFalse(off.hasManagerPin || off.hasAssistantPin)
        assertTrue(security.allows(ProtectedArea.SETTINGS))
    }
}

