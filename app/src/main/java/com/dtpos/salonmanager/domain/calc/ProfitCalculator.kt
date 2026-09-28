package com.dtpos.salonmanager.domain.calc

import com.dtpos.salonmanager.core.util.Percent

/**
 * Transparent profit model (cash basis):
 *
 *   Business Net Profit = Gross Sales - Business Expenses - Staff Payments
 *   Remaining Cash      = Business Net Profit - Personal/Household Spending
 *
 * Personal/household spending is deliberately NOT part of business profit; it is only
 * shown in the optional combined cash-flow view.
 */
data class ProfitSummary(
    val grossSalesMinor: Long,
    val businessExpensesMinor: Long,
    val staffPaymentsMinor: Long,
    val personalExpensesMinor: Long,
) {
    val totalBusinessCostsMinor: Long get() = businessExpensesMinor + staffPaymentsMinor
    val businessProfitMinor: Long get() = grossSalesMinor - totalBusinessCostsMinor
    val remainingCashMinor: Long get() = businessProfitMinor - personalExpensesMinor

    /** Profit margin in percent with one decimal (0 when there are no sales). */
    val profitMarginPercent: Double get() = Percent.ratio(businessProfitMinor, grossSalesMinor)

    val isLoss: Boolean get() = businessProfitMinor < 0

    companion object {
        val ZERO = ProfitSummary(0, 0, 0, 0)
    }
}

object ProfitCalculator {
    fun summarize(
        grossSalesMinor: Long,
        businessExpensesMinor: Long,
        staffPaymentsMinor: Long,
        personalExpensesMinor: Long,
    ) = ProfitSummary(grossSalesMinor, businessExpensesMinor, staffPaymentsMinor, personalExpensesMinor)

    /** Percentage change from [previous] to [current]; null when there is no baseline. */
    fun changePercent(current: Long, previous: Long): Double? {
        if (previous == 0L) return null
        return Percent.ratio(current - previous, previous)
    }
}
