package com.dtpos.salonmanager.services.security

import com.dtpos.salonmanager.core.security.PasswordHasher
import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

enum class LockType { NONE, PIN, PASSWORD }

enum class ProtectedArea { APP, REPORTS, EXPENSES, SETTINGS, CLOSE_DAY, RECEIPT_EDIT, STAFF }

/**
 * Who is using the app right now, from the PIN that unlocked it. The owner may open everything.
 * A manager runs the shop (sales, reports, expenses, close day, staff) but not settings, the
 * account / plan or phones. An assistant only makes sales, tokens and customers.
 */
enum class StaffAccess {
    OWNER, MANAGER, ASSISTANT;

    fun allows(area: ProtectedArea): Boolean = when (this) {
        OWNER -> true
        MANAGER -> area != ProtectedArea.SETTINGS
        ASSISTANT -> area == ProtectedArea.APP
    }
}

data class SecurityConfig(
    val lockType: LockType = LockType.NONE,
    val biometricEnabled: Boolean = false,
    val lockOnStart: Boolean = true,
    val protectReports: Boolean = true,
    val protectExpenses: Boolean = true,
    val protectSettings: Boolean = true,
    val hasManagerPin: Boolean = false,
    val hasAssistantPin: Boolean = false,
) {
    val isEnabled: Boolean get() = lockType != LockType.NONE

    /** With staff PINs the app always starts locked, so it knows who is using it. */
    val hasStaffPins: Boolean get() = isEnabled && (hasManagerPin || hasAssistantPin)

    fun protects(area: ProtectedArea): Boolean = isEnabled && when (area) {
        ProtectedArea.APP -> lockOnStart || hasStaffPins
        ProtectedArea.REPORTS -> protectReports
        ProtectedArea.EXPENSES -> protectExpenses
        ProtectedArea.SETTINGS -> protectSettings
        ProtectedArea.CLOSE_DAY, ProtectedArea.RECEIPT_EDIT, ProtectedArea.STAFF -> hasStaffPins
    }
}

sealed interface VerifyResult {
    data object Success : VerifyResult
    data class Wrong(val attemptsLeft: Int) : VerifyResult
    data class LockedOut(val untilMillis: Long) : VerifyResult
}

/**
 * Optional owner lock (PIN or password + biometrics). Secrets are stored only as salted
 * PBKDF2 hashes. Repeated wrong attempts trigger an escalating lockout. The session unlocks
 * once and re-locks after the app has been in the background for [RELOCK_AFTER_MS].
 */
class SecurityManager(
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    /** Null until loaded from the database (UI shows a loader instead of flashing content). */
    val config: StateFlow<SecurityConfig?> = settings.observeAll()
        .map { parse(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _access = MutableStateFlow(StaffAccess.OWNER)
    /** The role of whoever unlocked the app (owner when there is no lock). */
    val access: StateFlow<StaffAccess> = _access.asStateFlow()

    /** True when the current user may open [area] (independent of whether it is locked). */
    fun allows(area: ProtectedArea): Boolean = config.value?.isEnabled != true || _access.value.allows(area)

    @Volatile private var backgroundedAt = 0L

    fun needsUnlock(area: ProtectedArea): Boolean {
        val current = config.value ?: return false
        return current.protects(area) && !_unlocked.value
    }

    fun onAppBackgrounded(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    fun onAppForegrounded(now: Long = System.currentTimeMillis()) {
        if (backgroundedAt > 0 && now - backgroundedAt > RELOCK_AFTER_MS) _unlocked.value = false
        backgroundedAt = 0
    }

    fun lockNow() {
        _unlocked.value = false
        _access.value = StaffAccess.OWNER
    }

    /** Called after a successful biometric prompt (offered only when no staff PINs exist). */
    fun unlockWithBiometric() {
        _access.value = StaffAccess.OWNER
        _unlocked.value = true
    }

    /**
     * Checks a PIN / password. The owner's secret gives full access; a staff PIN gives that
     * role's access. With [ownerOnly] (an area the current role may not open) only the owner's
     * secret is accepted.
     */
    suspend fun verify(secret: String, ownerOnly: Boolean = false): VerifyResult = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val lockoutUntil = settings.getLong(SettingKeys.SEC_LOCKOUT_UNTIL)
        if (lockoutUntil > now) return@withContext VerifyResult.LockedOut(lockoutUntil)

        val hash = settings.getString(SettingKeys.SEC_SECRET_HASH)
        if (hash == null) {
            _access.value = StaffAccess.OWNER
            _unlocked.value = true
            return@withContext VerifyResult.Success
        }
        val role = when {
            PasswordHasher.verify(secret.toCharArray(), hash) -> StaffAccess.OWNER
            ownerOnly -> null
            settings.getString(SettingKeys.SEC_MANAGER_HASH)?.let { PasswordHasher.verify(secret.toCharArray(), it) } == true -> StaffAccess.MANAGER
            settings.getString(SettingKeys.SEC_ASSISTANT_HASH)?.let { PasswordHasher.verify(secret.toCharArray(), it) } == true -> StaffAccess.ASSISTANT
            else -> null
        }
        if (role != null) {
            settings.putInt(SettingKeys.SEC_FAILED_ATTEMPTS, 0)
            settings.putLong(SettingKeys.SEC_LOCKOUT_UNTIL, 0)
            _access.value = role
            _unlocked.value = true
            return@withContext VerifyResult.Success
        }
        val attempts = settings.getInt(SettingKeys.SEC_FAILED_ATTEMPTS) + 1
        settings.putInt(SettingKeys.SEC_FAILED_ATTEMPTS, attempts)
        if (attempts % MAX_ATTEMPTS == 0) {
            val round = attempts / MAX_ATTEMPTS
            val duration = (BASE_LOCKOUT_MS shl (round - 1).coerceAtMost(5)).coerceAtMost(MAX_LOCKOUT_MS)
            val until = now + duration
            settings.putLong(SettingKeys.SEC_LOCKOUT_UNTIL, until)
            VerifyResult.LockedOut(until)
        } else {
            VerifyResult.Wrong(MAX_ATTEMPTS - attempts % MAX_ATTEMPTS)
        }
    }

    /** Sets a new PIN / password. Returns the one-time recovery code to show to the owner. */
    suspend fun setCredential(type: LockType, secret: String): String = withContext(Dispatchers.Default) {
        require(type != LockType.NONE)
        val recovery = PasswordHasher.newRecoveryCode()
        settings.putString(SettingKeys.SEC_SECRET_HASH, PasswordHasher.hash(secret.toCharArray()))
        settings.putString(
            SettingKeys.SEC_RECOVERY_HASH,
            PasswordHasher.hash(PasswordHasher.normalizeRecoveryCode(recovery).toCharArray()),
        )
        settings.putString(SettingKeys.SEC_LOCK_TYPE, type.name)
        settings.putInt(SettingKeys.SEC_FAILED_ATTEMPTS, 0)
        settings.putLong(SettingKeys.SEC_LOCKOUT_UNTIL, 0)
        _access.value = StaffAccess.OWNER
        _unlocked.value = true
        recovery
    }

    /**
     * Sets (or with a blank [pin], removes) the PIN of a staff role. Refused when it equals the
     * owner's or the other role's PIN, so a PIN always means exactly one role.
     */
    suspend fun setStaffPin(role: StaffAccess, pin: String): Boolean = withContext(Dispatchers.Default) {
        require(role != StaffAccess.OWNER)
        val key = if (role == StaffAccess.MANAGER) SettingKeys.SEC_MANAGER_HASH else SettingKeys.SEC_ASSISTANT_HASH
        if (pin.isBlank()) {
            settings.putString(key, null)
            return@withContext true
        }
        val other = if (role == StaffAccess.MANAGER) SettingKeys.SEC_ASSISTANT_HASH else SettingKeys.SEC_MANAGER_HASH
        val taken = listOfNotNull(settings.getString(SettingKeys.SEC_SECRET_HASH), settings.getString(other))
            .any { PasswordHasher.verify(pin.toCharArray(), it) }
        if (taken || settings.getString(SettingKeys.SEC_SECRET_HASH) == null) return@withContext false
        settings.putString(key, PasswordHasher.hash(pin.toCharArray()))
        true
    }

    suspend fun disable() {
        settings.putString(SettingKeys.SEC_LOCK_TYPE, LockType.NONE.name)
        settings.putString(SettingKeys.SEC_SECRET_HASH, null)
        settings.putString(SettingKeys.SEC_RECOVERY_HASH, null)
        settings.putString(SettingKeys.SEC_MANAGER_HASH, null)
        settings.putString(SettingKeys.SEC_ASSISTANT_HASH, null)
        _access.value = StaffAccess.OWNER
        settings.putBoolean(SettingKeys.SEC_BIOMETRIC, false)
        settings.putInt(SettingKeys.SEC_FAILED_ATTEMPTS, 0)
        settings.putLong(SettingKeys.SEC_LOCKOUT_UNTIL, 0)
        _unlocked.value = true
    }

    /** Forgotten PIN: the recovery code removes the lock so a new PIN can be set. */
    suspend fun resetWithRecoveryCode(code: String): Boolean = withContext(Dispatchers.Default) {
        val hash = settings.getString(SettingKeys.SEC_RECOVERY_HASH) ?: return@withContext false
        if (!PasswordHasher.verify(PasswordHasher.normalizeRecoveryCode(code).toCharArray(), hash)) return@withContext false
        disable()
        true
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = settings.putBoolean(SettingKeys.SEC_BIOMETRIC, enabled)

    suspend fun setProtection(area: ProtectedArea, enabled: Boolean) {
        val key = when (area) {
            ProtectedArea.APP -> SettingKeys.SEC_LOCK_ON_START
            ProtectedArea.REPORTS -> SettingKeys.SEC_PROTECT_REPORTS
            ProtectedArea.EXPENSES -> SettingKeys.SEC_PROTECT_EXPENSES
            ProtectedArea.SETTINGS -> SettingKeys.SEC_PROTECT_SETTINGS
            // Always protected while staff PINs exist.
            ProtectedArea.CLOSE_DAY, ProtectedArea.RECEIPT_EDIT, ProtectedArea.STAFF -> return
        }
        settings.putBoolean(key, enabled)
    }

    private fun parse(map: Map<String, String>): SecurityConfig {
        val type = map[SettingKeys.SEC_LOCK_TYPE]?.let { v -> LockType.entries.firstOrNull { it.name == v } } ?: LockType.NONE
        val hasSecret = !map[SettingKeys.SEC_SECRET_HASH].isNullOrBlank()
        return SecurityConfig(
            lockType = if (hasSecret) type else LockType.NONE,
            biometricEnabled = map[SettingKeys.SEC_BIOMETRIC]?.toBooleanStrictOrNull() ?: false,
            lockOnStart = map[SettingKeys.SEC_LOCK_ON_START]?.toBooleanStrictOrNull() ?: true,
            protectReports = map[SettingKeys.SEC_PROTECT_REPORTS]?.toBooleanStrictOrNull() ?: true,
            protectExpenses = map[SettingKeys.SEC_PROTECT_EXPENSES]?.toBooleanStrictOrNull() ?: true,
            protectSettings = map[SettingKeys.SEC_PROTECT_SETTINGS]?.toBooleanStrictOrNull() ?: true,
            hasManagerPin = !map[SettingKeys.SEC_MANAGER_HASH].isNullOrBlank(),
            hasAssistantPin = !map[SettingKeys.SEC_ASSISTANT_HASH].isNullOrBlank(),
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 15 * 60_000L
        const val RELOCK_AFTER_MS = 2 * 60_000L
    }
}
