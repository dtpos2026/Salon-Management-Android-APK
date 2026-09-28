package com.dtpos.salonmanager.domain.license

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class LicensePlan(val months: Long?) {
    TRIAL(null),
    MONTH_1(1),
    MONTH_3(3),
    MONTH_6(6),
    YEAR_1(12),
    CUSTOM(null),
    LIFETIME(null),
    ;

    /** Default expiry for a plan starting on [start]; null for lifetime / custom (explicit date). */
    fun defaultExpiry(start: LocalDate): LocalDate? = months?.let { start.plusMonths(it) }
}

/** Signed content of a licence key. */
data class LicensePayload(
    val licenseId: String,
    val businessName: String,
    val plan: LicensePlan,
    val issuedOn: LocalDate,
    /** Last valid day (inclusive). Null = never expires. */
    val expiresOn: LocalDate?,
    /** Installation id this key is bound to, or [ANY_DEVICE]. */
    val deviceId: String,
    val maxBusinesses: Int = 1,
    val features: Set<String> = emptySet(),
) {
    fun isBoundTo(installationId: String): Boolean =
        deviceId == ANY_DEVICE || deviceId.equals(installationId, ignoreCase = true)

    companion object {
        const val ANY_DEVICE = "*"
    }
}

enum class LicenseStatus {
    /** Licensing is switched off for this build (owner edition). */
    NOT_ENFORCED,
    TRIAL,
    TRIAL_EXPIRED,
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    INVALID,
    CLOCK_TAMPERED,
}

data class LicenseState(
    val status: LicenseStatus,
    val enforced: Boolean,
    val payload: LicensePayload?,
    /** Days left in the licence or trial, including today. Null for lifetime / not enforced. */
    val daysRemaining: Long?,
    val trialEndsOn: LocalDate?,
) {
    /** When true, new sales and edits are blocked; viewing, reports and backups keep working. */
    val isReadOnly: Boolean
        get() = enforced && status in setOf(
            LicenseStatus.TRIAL_EXPIRED,
            LicenseStatus.EXPIRED,
            LicenseStatus.INVALID,
            LicenseStatus.CLOCK_TAMPERED,
        )

    companion object {
        val NOT_ENFORCED = LicenseState(LicenseStatus.NOT_ENFORCED, false, null, null, null)
    }
}

/**
 * Pure licence evaluation. Keeps the rules in one tested place so an online verifier added
 * later only has to supply a payload (and optionally a revocation flag).
 */
object LicenseEvaluator {
    const val EXPIRING_SOON_DAYS = 7L
    /** Tolerate small clock corrections (time zone changes etc.) before flagging tampering. */
    const val CLOCK_TOLERANCE_DAYS = 2L

    fun evaluate(
        enforced: Boolean,
        today: LocalDate,
        firstRunDate: LocalDate,
        trialDays: Int,
        payload: LicensePayload?,
        installationId: String,
        lastSeenDate: LocalDate?,
    ): LicenseState {
        if (enforced && lastSeenDate != null && today.isBefore(lastSeenDate.minusDays(CLOCK_TOLERANCE_DAYS))) {
            return LicenseState(LicenseStatus.CLOCK_TAMPERED, true, payload, null, null)
        }

        if (payload != null) {
            if (!payload.isBoundTo(installationId)) {
                return if (enforced) LicenseState(LicenseStatus.INVALID, true, payload, null, null)
                else LicenseState.NOT_ENFORCED
            }
            val expires = payload.expiresOn
            val status = when {
                expires == null -> LicenseStatus.ACTIVE
                today.isAfter(expires) -> LicenseStatus.EXPIRED
                ChronoUnit.DAYS.between(today, expires) < EXPIRING_SOON_DAYS -> LicenseStatus.EXPIRING_SOON
                else -> LicenseStatus.ACTIVE
            }
            val days = expires?.let { (ChronoUnit.DAYS.between(today, it) + 1).coerceAtLeast(0) }
            return LicenseState(status, enforced, payload, days, null)
        }

        if (!enforced) return LicenseState.NOT_ENFORCED

        val trialEnd = firstRunDate.plusDays(trialDays.toLong() - 1)
        return if (today.isAfter(trialEnd)) {
            LicenseState(LicenseStatus.TRIAL_EXPIRED, true, null, 0, trialEnd)
        } else {
            LicenseState(LicenseStatus.TRIAL, true, null, ChronoUnit.DAYS.between(today, trialEnd) + 1, trialEnd)
        }
    }
}
