package com.dtpos.salonmanager.services.account

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

sealed interface RefreshResult {
    data object Ok : RefreshResult
    data object NotConfigured : RefreshResult
    data object SignedOut : RefreshResult
    /** Server not reachable (offline, timeout) or it refused the request. */
    data class Failed(val cause: Throwable?) : RefreshResult
}

sealed interface SignInOutcome {
    data object Success : SignInOutcome
    data class Failed(val error: AuthError) : SignInOutcome
}

/**
 * Decides whether this phone may open the salon app: email sign-in, admin approval of the account
 * and of this phone, licence expiry and offline grace. The decision is made instantly from the local cache (so the app
 * opens offline) and then refreshed from Firestore when the internet is available.
 */
class AccountManager(
    private val context: Context,
    private val backend: AccountBackend,
    private val cache: AccountCache,
    private val scope: CoroutineScope,
    private val versionCode: Int,
    private val versionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    /** This phone, see [DeviceIds]. */
    val deviceId: String = DeviceIds.forThisPhone(context),
) {
    private val _state = MutableStateFlow<AccessState>(AccessState.Loading)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    private val _branding = MutableStateFlow(cache.branding)
    val branding: StateFlow<Branding> = _branding.asStateFlow()

    private val _appConfig = MutableStateFlow(cache.appConfig)
    val appConfig: StateFlow<RemoteAppConfig> = _appConfig.asStateFlow()

    private val _checking = MutableStateFlow(false)
    /** True while the account is being verified online. */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val refreshLock = Mutex()
    private var listener: AutoCloseable? = null
    private var listenerUid: String? = null

    val isConfigured: Boolean get() = backend.isConfigured

    fun currentUser(): SignedInUser? = if (backend.isConfigured) backend.currentUser() else null

    fun cachedAccount(): AccountCache.Entry? = currentUser()?.let { cache.account(it.uid) }

    /** Startup: decide from the cache immediately, then verify online in the background. */
    fun start() {
        recompute()
        scope.launch { refresh() }
    }

    fun recompute() {
        val next = computeState()
        if (next is AccessState.Allowed && cache.deviceOwner == null) {
            currentUser()?.let { cache.deviceOwner = AccountCache.DeviceOwner(it.uid, it.email.orEmpty()) }
        }
        _state.value = next
    }

    private fun computeState(): AccessState {
        if (!backend.isConfigured) return AccessState.NotConfigured
        val user = backend.currentUser() ?: return AccessState.SignedOut
        val owner = cache.deviceOwner
        if (owner != null && owner.uid != user.uid) return AccessState.WrongDevice(user, owner.email)
        val config = _appConfig.value
        if (AccessPolicy.updateRequired(config, versionCode)) return AccessState.UpdateRequired(config)
        val entry = cache.account(user.uid)
            ?: return when {
                cache.hasNoProfile(user.uid) -> AccessState.NeedsProfile(user)
                _checking.value -> AccessState.Loading
                else -> AccessState.NeedsVerification(null, null)
            }
        return AccessPolicy.decide(entry.account, entry.verifiedAtMillis, clock(), config.offlineGraceDays, deviceId)
    }

    /** Re-reads the account, branding and app settings from the server. Safe to call any time. */
    suspend fun refresh(): RefreshResult = refreshLock.withLock {
        if (!backend.isConfigured) {
            recompute()
            return RefreshResult.NotConfigured
        }
        val user = backend.currentUser()
        if (user == null) {
            recompute()
            return RefreshResult.SignedOut
        }
        _checking.value = true
        recompute()
        try {
            withTimeout(SERVER_TIMEOUT_MS) {
                // Public settings first: they must not block the account check if they fail.
                optional { backend.fetchAppConfig() }?.let {
                    cache.appConfig = it
                    _appConfig.value = it
                }
                optional { backend.fetchBranding() }?.let {
                    cache.branding = it
                    _branding.value = it
                }
                val account = backend.fetchAccount(user.uid)
                if (account == null) cache.markNoProfile(user.uid) else cache.putAccount(requestDeviceIfNeeded(account), clock())
            }
            ensureListener(user.uid)
            heartbeatIfDue(user.uid)
            RefreshResult.Ok
        } catch (e: TimeoutCancellationException) {
            RefreshResult.Failed(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RefreshResult.Failed(e)
        } finally {
            _checking.value = false
            recompute()
        }
    }

    suspend fun signIn(email: String, password: String): SignInOutcome =
        authAction { backend.signInWithEmail(email, password) }

    /** Creates the login; the salon details form (registration) follows. */
    suspend fun createLogin(email: String, password: String): SignInOutcome =
        authAction { backend.createLogin(email, password) }

    /** Null on success, else why the reset email could not be sent. */
    suspend fun sendPasswordReset(email: String): AuthError? = try {
        backend.sendPasswordReset(email)
        null
    } catch (e: AuthException) {
        e.error
    }

    private suspend fun authAction(block: suspend () -> SignedInUser): SignInOutcome {
        if (!backend.isConfigured) return SignInOutcome.Failed(AuthError.NOT_CONFIGURED)
        try {
            block()
        } catch (e: AuthException) {
            return SignInOutcome.Failed(e.error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SignInOutcome.Failed(AuthError.FAILED)
        }
        refresh()
        return SignInOutcome.Success
    }

    /**
     * When the account is approved for another phone, asks the admin to approve this one (once).
     * Returns the account as it now is on the server.
     */
    private suspend fun requestDeviceIfNeeded(account: CloudAccount): CloudAccount {
        if (account.approvedFor(deviceId) || account.pendingDeviceId == deviceId) return account
        val sent = optional { backend.requestDevice(account.uid, deviceId, deviceModel()) } != null
        return if (sent) account.copy(pendingDeviceId = deviceId, pendingDeviceModel = deviceModel()) else account
    }

    suspend fun register(registration: AccountRegistration): RefreshResult {
        val user = currentUser() ?: return RefreshResult.SignedOut
        try {
            backend.register(user, registration, versionName, deviceId, deviceModel())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RefreshResult.Failed(e)
        }
        return refresh()
    }

    /** Signs out. Salon data stays on the phone for the same account to sign in again. */
    suspend fun signOut() {
        stopListener()
        backend.signOut()
        cache.clearAccount()
        recompute()
    }

    /**
     * The signed-in account takes over this phone. Call only after the previous owner's salon
     * data has been erased (a safety backup is made by the erase).
     */
    suspend fun takeOverDevice() {
        val user = currentUser() ?: return
        cache.deviceOwner = AccountCache.DeviceOwner(user.uid, user.email.orEmpty())
        recompute()
        refresh()
    }

    private fun ensureListener(uid: String) {
        if (listenerUid == uid && listener != null) return
        stopListener()
        listenerUid = uid
        listener = try {
            backend.listenAccount(uid) { account ->
                if (backend.currentUser()?.uid != uid) return@listenAccount
                if (account == null) cache.markNoProfile(uid) else cache.putAccount(account, clock())
                recompute()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun stopListener() {
        try {
            listener?.close()
        } catch (e: Exception) {
            // ignore
        }
        listener = null
        listenerUid = null
    }

    private suspend fun heartbeatIfDue(uid: String) {
        // Only the approved phone reports itself; another phone must not overwrite its details.
        if (cache.account(uid)?.account?.approvedFor(deviceId) != true) return
        val now = clock()
        if (now - cache.lastHeartbeatMillis < HEARTBEAT_INTERVAL_MS) return
        if (optional { backend.heartbeat(uid, versionName, deviceModel()) } != null) cache.lastHeartbeatMillis = now
    }

    private inline fun <T> optional(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private companion object {
        const val SERVER_TIMEOUT_MS = 15_000L
        const val HEARTBEAT_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
