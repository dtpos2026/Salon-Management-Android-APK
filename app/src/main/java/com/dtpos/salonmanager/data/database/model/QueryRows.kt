package com.dtpos.salonmanager.data.database.model

import androidx.room.Embedded
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SaleStatus
import com.dtpos.salonmanager.domain.model.StaffPaymentType

/** Aggregate / projection rows returned by DAO queries. Column aliases must match field names. */

data class CustomerListRow(
    @Embedded val customer: CustomerEntity,
    val visitCount: Int,
    val lastVisitAt: Long?,
    val totalSpentMinor: Long,
)

data class CustomerStatsRow(
    val visitCount: Int,
    val totalSpentMinor: Long,
    val lastVisitAt: Long?,
    val firstVisitAt: Long?,
)

data class VisitRow(
    val saleId: Long,
    val visitAt: Long,
    val servicesSummary: String,
    val staffSummary: String?,
    val totalMinor: Long,
    val receiptNumber: String,
    val paymentMethod: PaymentMethod,
    val status: SaleStatus,
)

data class NamedTotalRow(
    val name: String,
    val quantity: Int,
    val totalMinor: Long,
)

data class DayTotalRow(
    val day: Long,
    val totalMinor: Long,
    val count: Int,
)

data class SalesSummaryRow(
    val totalMinor: Long,
    val saleCount: Int,
    /** Money received in cash (a cash sale's credit part is not included). */
    val cashMinor: Long,
    val discountMinor: Long,
    val serviceCount: Int,
    /** Udhaar: billed but not paid yet. */
    val creditMinor: Long = 0,
) {
    companion object {
        val EMPTY = SalesSummaryRow(0, 0, 0, 0, 0)
    }
}

/** Money received for sales per place: cash, a payment account, or a plain method. */
data class ReceivedTotalRow(
    val paymentMethod: PaymentMethod,
    val accountId: Long?,
    val accountName: String?,
    val totalMinor: Long,
    val saleCount: Int,
)

data class PaymentMethodTotalRow(
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
    val saleCount: Int,
)

data class StaffPerformanceRow(
    val staffId: Long?,
    val staffName: String?,
    val customerCount: Int,
    val serviceCount: Int,
    val salesMinor: Long,
    val commissionMinor: Long,
)

data class CategoryTotalRow(
    val name: String,
    val totalMinor: Long,
    val count: Int,
)

data class BudgetGroupTotalRow(
    val budgetGroup: BudgetGroup?,
    val totalMinor: Long,
)

data class CashTypeTotalRow(
    val type: CashTxType,
    val totalMinor: Long,
)

data class PaymentTypeTotalRow(
    val type: StaffPaymentType,
    val totalMinor: Long,
)

data class StaffPaymentTypeTotalRow(
    val staffId: Long,
    val type: StaffPaymentType,
    val totalMinor: Long,
)

data class StaffAmountRow(
    val staffId: Long,
    val amountMinor: Long,
)
