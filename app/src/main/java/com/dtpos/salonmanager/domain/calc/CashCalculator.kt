package com.dtpos.salonmanager.domain.calc

import com.dtpos.salonmanager.domain.model.CashTxType
import kotlin.math.abs

/**
 * Cash drawer reconciliation for one business day:
 *
 *   Expected Closing = Opening + Cash Sales - Refunds(voids) - Cash Expenses
 *                      - Staff Cash Payments + Cash In - Cash Out
 *   Difference       = Actual Closing - Expected Closing   (negative = short, positive = excess)
 */
data class CashBreakdown(
    val openingMinor: Long,
    val cashSalesMinor: Long,
    val voidRefundsMinor: Long,
    val cashExpensesMinor: Long,
    val staffPaymentsMinor: Long,
    val cashInMinor: Long,
    val cashOutMinor: Long,
) {
    val expectedClosingMinor: Long
        get() = openingMinor + cashSalesMinor - voidRefundsMinor - cashExpensesMinor -
            staffPaymentsMinor + cashInMinor - cashOutMinor

    fun differenceFor(actualClosingMinor: Long): Long = actualClosingMinor - expectedClosingMinor

    companion object {
        val EMPTY = CashBreakdown(0, 0, 0, 0, 0, 0, 0)
    }
}

enum class CashDifferenceStatus { BALANCED, SHORT, EXCESS }

object CashCalculator {

    /** Builds the breakdown from signed per-type totals (as stored in cash_transactions). */
    fun breakdown(openingMinor: Long, signedTotalsByType: Map<CashTxType, Long>): CashBreakdown {
        fun total(type: CashTxType) = abs(signedTotalsByType[type] ?: 0L)
        return CashBreakdown(
            openingMinor = openingMinor,
            cashSalesMinor = total(CashTxType.SALE),
            voidRefundsMinor = total(CashTxType.SALE_VOID),
            cashExpensesMinor = total(CashTxType.EXPENSE),
            staffPaymentsMinor = total(CashTxType.STAFF_PAYMENT),
            cashInMinor = total(CashTxType.CASH_IN),
            cashOutMinor = total(CashTxType.CASH_OUT),
        )
    }

    fun status(differenceMinor: Long): CashDifferenceStatus = when {
        differenceMinor == 0L -> CashDifferenceStatus.BALANCED
        differenceMinor < 0L -> CashDifferenceStatus.SHORT
        else -> CashDifferenceStatus.EXCESS
    }

    /** Sign convention for the drawer: money in is positive, money out is negative. */
    fun signedAmount(type: CashTxType, amountMinor: Long): Long {
        val magnitude = abs(amountMinor)
        return when (type) {
            CashTxType.SALE, CashTxType.CASH_IN -> magnitude
            CashTxType.SALE_VOID, CashTxType.EXPENSE, CashTxType.STAFF_PAYMENT, CashTxType.CASH_OUT -> -magnitude
        }
    }
}
