package com.dtpos.salonmanager.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Money a customer still owes (udhaar / pending bill). Kept apart from sales so sales, cash and
 * profit figures stay exactly as they are; a due is settled when it is paid.
 */
@Entity(
    tableName = "customer_dues",
    indices = [Index(value = ["businessId", "settledAt"]), Index(value = ["customerId"])],
)
data class DueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val customerId: Long?,
    val customerName: String,
    val customerPhone: String?,
    val amountMinor: Long,
    val paidMinor: Long,
    val note: String?,
    val createdAt: Long,
    val settledAt: Long?,
    val lastReminderAt: Long?,
    /** The credit sale this due came from (null for a due added by hand). */
    val saleId: Long? = null,
) {
    val balanceMinor: Long get() = (amountMinor - paidMinor).coerceAtLeast(0)
}

/**
 * One payment received against a pending bill: how much, how (cash or an online account) and
 * on which business day. Cash payments also add a cash-drawer entry; online payments do not.
 * [DueEntity.paidMinor] is always the sum of a bill's payments.
 */
@Entity(
    tableName = "due_payments",
    indices = [Index(value = ["dueId"]), Index(value = ["businessId", "businessDate"])],
)
data class DuePaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val dueId: Long,
    val amountMinor: Long,
    val paymentMethod: com.dtpos.salonmanager.domain.model.PaymentMethod,
    /** JazzCash / EasyPaisa / bank account it was paid into (online payments). */
    val paymentAccountId: Long?,
    val paymentAccountName: String?,
    /** Business day it counts for (the next day when today is already closed). */
    val businessDate: Long,
    val createdAt: Long,
)

/**
 * A queue token: a walk-in (timeMinutes = null) or an advance booking for a date and time.
 * Token numbers start at 1 every day; walk-ins and bookings share the day's numbers.
 */
@Entity(
    tableName = "bookings",
    indices = [Index(value = ["businessId", "dateEpochDay", "tokenNumber"], unique = true)],
)
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val dateEpochDay: Long,
    val tokenNumber: Int,
    /** Minutes after midnight for an advance booking; null for a walk-in token. */
    val timeMinutes: Int?,
    val customerId: Long?,
    val customerName: String,
    val customerPhone: String?,
    val service: String?,
    val staffName: String?,
    val note: String?,
    val status: com.dtpos.salonmanager.domain.model.BookingStatus,
    val createdAt: Long,
    val updatedAt: Long,
)
