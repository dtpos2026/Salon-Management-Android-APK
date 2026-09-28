package com.dtpos.salonmanager.services.license

import android.content.Context
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.dao.LicenseDao
import com.dtpos.salonmanager.data.database.entities.LicenseEntity
import com.dtpos.salonmanager.domain.license.LicenseCodec
import com.dtpos.salonmanager.domain.license.LicenseEvaluator
import com.dtpos.salonmanager.domain.license.LicensePayload
import com.dtpos.salonmanager.domain.license.LicenseState
import com.dtpos.salonmanager.domain.license.LicenseStatus
import com.dtpos.salonmanager.domain.license.LicenseVerification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.time.LocalDate

data class LicenseConfig(
    val enforced: Boolean,
    val trialDays: Int,
    val publicKeyBase64: String,
)

enum class ActivationError { MALFORMED, BAD_SIGNATURE, WRONG_DEVICE, EXPIRED, NOT_CONFIGURED }

sealed interface ActivationResult {
    data class Activated(val state: LicenseState) : ActivationResult
    data class Rejected(val error: ActivationError) : ActivationResult
}

/**
 * Offline licence management. The installation id and first-run date live in private
 * SharedPreferences (not in the database) so restoring a backup on another phone does not
 * carry over a device-bound licence. Designed so that a future online verifier can simply
 * refresh/revoke the stored key.
 */
class LicenseManager(
    context: Context,
    private val dao: LicenseDao,
    private val config: LicenseConfig,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val publicKey by lazy { config.publicKeyBase64.takeIf { it.isNotBlank() }?.let(LicenseCodec::publicKeyFromBase64) }

    private val _state = MutableStateFlow(
        if (config.enforced) LicenseState(LicenseStatus.TRIAL, true, null, null, null) else LicenseState.NOT_ENFORCED,
    )
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    val isEnforced: Boolean get() = config.enforced
    val canActivate: Boolean get() = publicKey != null

    val installationId: String
        get() = prefs.getString(KEY_INSTALLATION_ID, null) ?: newInstallationId().also {
            prefs.edit().putString(KEY_INSTALLATION_ID, it).apply()
        }

    private val firstRunDate: LocalDate
        get() = prefs.getString(KEY_FIRST_RUN, null)?.let(DateTimeUtils::parseIso) ?: DateTimeUtils.today().also {
            prefs.edit().putString(KEY_FIRST_RUN, DateTimeUtils.formatIso(it)).apply()
        }

    /** Re-evaluates the licence (call on app start and when the app returns to foreground). */
    suspend fun refresh(): LicenseState {
        val today = DateTimeUtils.today()
        val lastSeen = prefs.getString(KEY_LAST_SEEN, null)?.let(DateTimeUtils::parseIso)
        val payload = storedPayload()
        val state = LicenseEvaluator.evaluate(
            enforced = config.enforced,
            today = today,
            firstRunDate = firstRunDate,
            trialDays = config.trialDays,
            payload = payload,
            installationId = installationId,
            lastSeenDate = lastSeen,
        )
        if (lastSeen == null || today.isAfter(lastSeen)) {
            prefs.edit().putString(KEY_LAST_SEEN, DateTimeUtils.formatIso(today)).apply()
        }
        _state.value = state
        return state
    }

    suspend fun activate(licenseKey: String): ActivationResult {
        val key = publicKey ?: return ActivationResult.Rejected(ActivationError.NOT_CONFIGURED)
        val payload = when (val result = LicenseCodec.verify(licenseKey, key)) {
            is LicenseVerification.Valid -> result.payload
            is LicenseVerification.Invalid -> return ActivationResult.Rejected(
                if (result.reason == LicenseVerification.Reason.BAD_SIGNATURE) ActivationError.BAD_SIGNATURE else ActivationError.MALFORMED,
            )
        }
        if (!payload.isBoundTo(installationId)) return ActivationResult.Rejected(ActivationError.WRONG_DEVICE)
        val expires = payload.expiresOn
        if (expires != null && DateTimeUtils.today().isAfter(expires)) return ActivationResult.Rejected(ActivationError.EXPIRED)

        val now = System.currentTimeMillis()
        dao.put(
            LicenseEntity(
                licenseKey = licenseKey.filterNot { it.isWhitespace() },
                licenseId = payload.licenseId,
                businessName = payload.businessName,
                plan = payload.plan.name,
                activatedAt = now,
                expiresOn = expires?.toString(),
                status = LicenseStatus.ACTIVE.name,
                lastVerifiedAt = now,
                updatedAt = now,
            ),
        )
        return ActivationResult.Activated(refresh())
    }

    suspend fun removeLicense() {
        dao.put(LicenseEntity(licenseKey = null, licenseId = null, businessName = null, plan = null, activatedAt = null,
            expiresOn = null, status = LicenseStatus.NOT_ENFORCED.name, lastVerifiedAt = null, updatedAt = System.currentTimeMillis()))
        refresh()
    }

    suspend fun activatedAt(): Long? = dao.get()?.activatedAt

    private suspend fun storedPayload(): LicensePayload? {
        val stored = dao.get()?.licenseKey ?: return null
        return (LicenseCodec.verify(stored, publicKey) as? LicenseVerification.Valid)?.payload
    }

    private fun newInstallationId(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (0 until 3).joinToString("-") { (0 until 4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
    }

    companion object {
        private const val PREFS = "installation"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_FIRST_RUN = "first_run_date"
        private const val KEY_LAST_SEEN = "last_seen_date"
    }
}
