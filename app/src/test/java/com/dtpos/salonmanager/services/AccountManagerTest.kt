package com.dtpos.salonmanager.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountBackend
import com.dtpos.salonmanager.services.account.AccountCache
import com.dtpos.salonmanager.services.account.AccountManager
import com.dtpos.salonmanager.services.account.AccountRegistration
import com.dtpos.salonmanager.services.account.AccountStatus
import com.dtpos.salonmanager.services.account.AuthError
import com.dtpos.salonmanager.services.account.AuthException
import com.dtpos.salonmanager.services.account.Branding
import com.dtpos.salonmanager.services.account.CloudAccount
import com.dtpos.salonmanager.services.account.RefreshResult
import com.dtpos.salonmanager.services.account.RemoteAppConfig
import com.dtpos.salonmanager.services.account.SignInOutcome
import com.dtpos.salonmanager.services.account.SignedInUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

/** In-memory stand-in for Firebase: an "admin" can change accounts and the network can drop. */
class FakeAccountBackend : AccountBackend {
    override var isConfigured = true
    var user: SignedInUser? = null
    var online = true
    val accounts = mutableMapOf<String, CloudAccount>()
    var config = RemoteAppConfig()
    private var listener: ((CloudAccount?) -> Unit)? = null

    private fun network() {
        if (!online) throw IOException("offline")
    }

    override fun currentUser() = user

    /** Logins by email; the password is the uid (enough for tests). */
    val logins = mutableMapOf<String, String>()

    override suspend fun signInWithEmail(email: String, password: String): SignedInUser {
        network()
        val uid = logins[email] ?: throw AuthException(AuthError.WRONG_CREDENTIALS)
        if (uid != password) throw AuthException(AuthError.WRONG_CREDENTIALS)
        return SignedInUser(uid, email).also { user = it }
    }

    override suspend fun createLogin(email: String, password: String): SignedInUser {
        network()
        if (email in logins) throw AuthException(AuthError.EMAIL_IN_USE)
        logins[email] = password
        return SignedInUser(password, email).also { user = it }
    }

    override suspend fun sendPasswordReset(email: String) {
        network()
        if (email !in logins) throw AuthException(AuthError.WRONG_CREDENTIALS)
    }

    /** Test helper: a login that already exists (uid doubles as password). */
    fun existingLogin(uid: String) {
        logins["$uid@gmail.com"] = uid
    }

    override fun signOut() {
        user = null
    }

    override suspend fun fetchAccount(uid: String): CloudAccount? {
        network()
        return accounts[uid]
    }

    override fun listenAccount(uid: String, onChange: (CloudAccount?) -> Unit): AutoCloseable {
        listener = onChange
        return AutoCloseable { listener = null }
    }

    override suspend fun register(user: SignedInUser, registration: AccountRegistration, appVersion: String, deviceId: String, deviceModel: String) {
        network()
        accounts[user.uid] = CloudAccount(
            uid = user.uid, email = user.email, salonName = registration.salonName,
            ownerName = registration.ownerName, phone = registration.phone, status = AccountStatus.PENDING,
            deviceId = deviceId, deviceModel = deviceModel,
        )
    }

    override suspend fun requestDevice(uid: String, deviceId: String, deviceModel: String) {
        network()
        accounts[uid] = accounts.getValue(uid).copy(pendingDeviceId = deviceId, pendingDeviceModel = deviceModel)
    }

    override suspend fun heartbeat(uid: String, appVersion: String, deviceModel: String) = network()

    override suspend fun fetchBranding(): Branding {
        network()
        return Branding(appName = "DT Test", whatsapp = "03001234567")
    }

    override suspend fun fetchAppConfig(): RemoteAppConfig {
        network()
        return config
    }

    /** What the Super Admin panel does: change the document; the app hears it live. */
    fun adminUpdates(uid: String, change: (CloudAccount) -> CloudAccount) {
        val updated = change(accounts.getValue(uid))
        accounts[uid] = updated
        listener?.invoke(updated)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backend = FakeAccountBackend()
    private var now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("dt_account", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun manager(scope: CoroutineScope) = AccountManager(
        context = context,
        backend = backend,
        cache = AccountCache(context),
        scope = scope,
        versionCode = 2,
        versionName = "2.0.0",
        clock = { now },
        deviceId = THIS_PHONE,
    )

    private val registration = AccountRegistration("Royal Cuts", "Ali", "03001234567", "Burewala", "Main Bazar")

    @Test
    fun `new salon signs in, registers, waits, gets approved and opens offline`() = runTest {
        val accounts = manager(backgroundScope)
        accounts.recompute()
        assertEquals(AccessState.SignedOut, accounts.state.value)

        assertEquals(SignInOutcome.Success, accounts.createLogin("ali@gmail.com", "uid-1"))
        assertTrue(accounts.state.value is AccessState.NeedsProfile)
        assertEquals("DT Test", accounts.branding.value.appName)

        assertEquals(RefreshResult.Ok, accounts.register(registration))
        assertEquals(AccountStatus.PENDING, (accounts.state.value as AccessState.Restricted).status)

        // The admin approves for one month; the live listener opens the app without a refresh.
        backend.adminUpdates("uid-1") { it.copy(status = AccountStatus.APPROVED, customerId = "DTC-0001", expiresAtMillis = now + 30 * day) }
        val allowed = accounts.state.value as AccessState.Allowed
        assertEquals("DTC-0001", allowed.account.customerId)

        // Offline for 10 days: still opens from the cache.
        backend.online = false
        now += 10 * day
        assertTrue(accounts.refresh() is RefreshResult.Failed)
        assertTrue(accounts.state.value is AccessState.Allowed)

        // A new process (app restart) offline also opens.
        val restarted = manager(backgroundScope)
        restarted.recompute()
        assertTrue(restarted.state.value is AccessState.Allowed)

        // Licence end passes while offline: expired, data stays.
        now += 25 * day
        restarted.recompute()
        assertEquals(AccountStatus.EXPIRED, (restarted.state.value as AccessState.Restricted).status)
    }

    @Test
    fun `admin suspension and payment pending close the app`() = runTest {
        backend.accounts["u2"] = CloudAccount(uid = "u2", status = AccountStatus.APPROVED)
        val accounts = manager(backgroundScope)
        backend.existingLogin("u2")
        accounts.signIn("u2@gmail.com", "u2")
        assertTrue(accounts.state.value is AccessState.Allowed)

        backend.adminUpdates("u2") { it.copy(status = AccountStatus.PAYMENT_PENDING, pendingAmount = 4000) }
        val restricted = accounts.state.value as AccessState.Restricted
        assertEquals(AccountStatus.PAYMENT_PENDING, restricted.status)
        assertEquals(4000L, restricted.account.pendingAmount)

        backend.adminUpdates("u2") { it.copy(status = AccountStatus.SUSPENDED) }
        assertEquals(AccountStatus.SUSPENDED, (accounts.state.value as AccessState.Restricted).status)

        backend.adminUpdates("u2") { it.copy(status = AccountStatus.APPROVED) }
        assertTrue(accounts.state.value is AccessState.Allowed)
    }

    @Test
    fun `first sign in without internet asks to connect`() = runTest {
        val accounts = manager(backgroundScope)
        backend.user = SignedInUser("u3", "u3@gmail.com", null, null)
        backend.online = false
        accounts.refresh()
        val state = accounts.state.value
        assertTrue(state is AccessState.NeedsVerification && state.account == null)
    }

    @Test
    fun `the phone stays with the first account until its data is erased`() = runTest {
        backend.accounts["owner"] = CloudAccount(uid = "owner", status = AccountStatus.APPROVED)
        backend.accounts["other"] = CloudAccount(uid = "other", status = AccountStatus.APPROVED)
        val accounts = manager(backgroundScope)
        backend.existingLogin("owner")
        accounts.signIn("owner@gmail.com", "owner")
        assertTrue(accounts.state.value is AccessState.Allowed)

        accounts.signOut()
        assertEquals(AccessState.SignedOut, accounts.state.value)

        backend.existingLogin("other")
        accounts.signIn("other@gmail.com", "other")
        val wrong = accounts.state.value as AccessState.WrongDevice
        assertEquals("owner@gmail.com", wrong.ownerEmail)

        accounts.takeOverDevice()
        assertTrue(accounts.state.value is AccessState.Allowed)
    }

    @Test
    fun `minimum version from the admin blocks old apps`() = runTest {
        backend.accounts["u4"] = CloudAccount(uid = "u4", status = AccountStatus.APPROVED)
        backend.config = RemoteAppConfig(minVersionCode = 3, updateUrl = "https://example.com")
        val accounts = manager(backgroundScope)
        backend.existingLogin("u4")
        accounts.signIn("u4@gmail.com", "u4")
        assertTrue(accounts.state.value is AccessState.UpdateRequired)
    }

    @Test
    fun `wrong password and taken email are reported`() = runTest {
        backend.existingLogin("u5")
        val accounts = manager(backgroundScope)
        accounts.recompute()
        assertEquals(SignInOutcome.Failed(AuthError.WRONG_CREDENTIALS), accounts.signIn("u5@gmail.com", "nope"))
        assertEquals(SignInOutcome.Failed(AuthError.EMAIL_IN_USE), accounts.createLogin("u5@gmail.com", "secret1"))
        assertEquals(null, accounts.sendPasswordReset("u5@gmail.com"))
        assertEquals(AccessState.SignedOut, accounts.state.value)
    }

    @Test
    fun `a second phone asks the admin and opens once approved`() = runTest {
        backend.accounts["u6"] = CloudAccount(uid = "u6", status = AccountStatus.APPROVED, deviceId = "a-first-phone", deviceModel = "Samsung A15")
        backend.existingLogin("u6")
        val accounts = manager(backgroundScope)
        accounts.signIn("u6@gmail.com", "u6")
        val waiting = accounts.state.value as AccessState.DeviceNotApproved
        assertTrue(waiting.requested)
        assertEquals(THIS_PHONE, backend.accounts.getValue("u6").pendingDeviceId)

        // The admin approves this phone in the panel.
        backend.adminUpdates("u6") { it.copy(deviceId = THIS_PHONE, pendingDeviceId = null) }
        assertTrue(accounts.state.value is AccessState.Allowed)
    }

    @Test
    fun `registration binds the account to this phone`() = runTest {
        val accounts = manager(backgroundScope)
        accounts.createLogin("new@gmail.com", "u7")
        accounts.register(registration)
        assertEquals(THIS_PHONE, backend.accounts.getValue("u7").deviceId)
    }

    @Test
    fun `builds without firebase show setup missing`() = runTest {
        backend.isConfigured = false
        val accounts = manager(backgroundScope)
        assertEquals(RefreshResult.NotConfigured, accounts.refresh())
        assertEquals(AccessState.NotConfigured, accounts.state.value)
    }

    private companion object {
        const val THIS_PHONE = "a-test-phone-0001"
    }
}
