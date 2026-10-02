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
) {
    val balanceMinor: Long get() = (amountMinor - paidMinor).coerceAtLeast(0)
}
