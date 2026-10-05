package com.dtpos.salonmanager.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.CashSessionStatus
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.StaffPaymentType

@Entity(
    tableName = "expense_categories",
    indices = [Index(value = ["businessId", "type", "name"], unique = true)],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val name: String,
    val type: ExpenseType,
    /** Stable key for built-in categories (translated in the UI); null for custom ones. */
    val systemKey: String? = null,
    val budgetGroup: BudgetGroup? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

/**
 * Business and personal/household expenses share one table but are always filtered by [type]
 * so personal spending never reduces business profit.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["businessId", "type", "expenseDate"]),
        Index(value = ["categoryId"]),
    ],
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExpenseCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val type: ExpenseType,
    val categoryId: Long?,
    val categoryName: String,
    val amountMinor: Long,
    /** Epoch day. */
    val expenseDate: Long,
    val paymentMethod: PaymentMethod,
    /** True when the cash was taken from the shop's cash drawer (affects cash counter). */
    val paidFromCounter: Boolean,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "staff_payments",
    indices = [
        Index(value = ["staffId", "paymentDate"]),
        Index(value = ["businessId", "paymentDate"]),
    ],
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StaffEntity::class, parentColumns = ["id"], childColumns = ["staffId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class StaffPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val staffId: Long,
    val amountMinor: Long,
    val type: StaffPaymentType,
    /** Epoch day. */
    val paymentDate: Long,
    val paymentMethod: PaymentMethod,
    val paidFromCounter: Boolean,
    val note: String?,
    val createdAt: Long,
)

/** One cash-counter day: opening float and the owner's actual count at closing. */
@Entity(
    tableName = "cash_sessions",
    indices = [Index(value = ["businessId", "sessionDate"], unique = true)],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class CashSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    /** Epoch day. */
    val sessionDate: Long,
    val openingCashMinor: Long,
    val expectedClosingMinor: Long? = null,
    val actualClosingMinor: Long? = null,
    val differenceMinor: Long? = null,
    val status: CashSessionStatus,
    val note: String? = null,
    val openedAt: Long,
    val closedAt: Long? = null,
)

/**
 * Every movement of physical cash. Created automatically for cash sales, voids, cash expenses
 * and staff cash payments, and manually for cash in/out. [amountMinor] is signed.
 */
@Entity(
    tableName = "cash_transactions",
    indices = [
        Index(value = ["businessId", "txDate"]),
        Index(value = ["referenceType", "referenceId"]),
    ],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class CashTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val type: CashTxType,
    val amountMinor: Long,
    /** Epoch day. */
    val txDate: Long,
    /** "SALE", "EXPENSE", "STAFF_PAYMENT" or null for manual entries. */
    val referenceType: String?,
    val referenceId: Long?,
    val note: String?,
    val createdAt: Long,
) {
    companion object {
        const val REF_SALE = "SALE"
        const val REF_EXPENSE = "EXPENSE"
        const val REF_STAFF_PAYMENT = "STAFF_PAYMENT"
        /** Udhaar (pending bill) money received in cash. */
        const val REF_DUE = "DUE"
    }
}
