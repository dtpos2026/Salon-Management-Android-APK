package com.dtpos.salonmanager.domain.calc

import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffPaymentType

/**
 * Monthly staff settlement:
 *
 *   Earned      = Fixed salary (if salary type includes it) + Commission earned on net sales
 *   Settled     = Salary + Commission + Advance payments made for the month
 *   Outstanding = Earned - Settled   (never below zero; the excess is shown as "paid in advance")
 *
 * Bonus and "other" payments are extras on top of earnings and are not deducted.
 */
data class StaffSettlement(
    val fixedSalaryMinor: Long,
    val commissionEarnedMinor: Long,
    val settledMinor: Long,
    val extrasPaidMinor: Long,
) {
    val earnedMinor: Long get() = fixedSalaryMinor + commissionEarnedMinor
    val outstandingMinor: Long get() = (earnedMinor - settledMinor).coerceAtLeast(0L)
    val overpaidMinor: Long get() = (settledMinor - earnedMinor).coerceAtLeast(0L)
    val totalPaidMinor: Long get() = settledMinor + extrasPaidMinor
}

object StaffPayCalculator {

    fun settlement(
        salaryType: SalaryType,
        monthlyFixedSalaryMinor: Long,
        commissionEarnedMinor: Long,
        paymentsByType: Map<StaffPaymentType, Long>,
    ): StaffSettlement {
        val fixed = if (salaryType.hasFixed) monthlyFixedSalaryMinor.coerceAtLeast(0L) else 0L
        val commission = if (salaryType.hasCommission) commissionEarnedMinor.coerceAtLeast(0L) else 0L
        val settled = listOf(StaffPaymentType.SALARY, StaffPaymentType.COMMISSION, StaffPaymentType.ADVANCE)
            .sumOf { paymentsByType[it] ?: 0L }
        val extras = listOf(StaffPaymentType.BONUS, StaffPaymentType.OTHER).sumOf { paymentsByType[it] ?: 0L }
        return StaffSettlement(fixed, commission, settled, extras)
    }
}
