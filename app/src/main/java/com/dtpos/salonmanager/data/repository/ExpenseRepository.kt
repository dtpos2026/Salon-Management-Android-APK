package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.ExpenseCategoryEntity
import com.dtpos.salonmanager.data.database.entities.ExpenseEntity
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

data class ExpenseInput(
    val type: ExpenseType,
    val categoryId: Long,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val paymentMethod: PaymentMethod,
    val paidFromCounter: Boolean,
    val note: String?,
)

class ExpenseRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.expenseDao()
    private val cashDao = db.cashDao()

    // ---- Categories ----------------------------------------------------------------------

    fun observeCategories(type: ExpenseType): Flow<List<ExpenseCategoryEntity>> = dao.observeCategories(businessId, type)

    suspend fun saveCategory(name: String, type: ExpenseType, budgetGroup: BudgetGroup?, id: Long? = null): DataResult<Long> = safeWrite {
        val duplicate = dao.findCategory(businessId, type, name)
        if (duplicate != null && duplicate.id != id) return@safeWrite DataResult.Failure(DataError.DUPLICATE_NAME)
        if (id == null) {
            DataResult.Success(
                dao.insertCategory(
                    ExpenseCategoryEntity(
                        businessId = businessId,
                        name = name,
                        type = type,
                        budgetGroup = budgetGroup,
                        sortOrder = dao.maxSortOrder(businessId, type) + 1,
                        createdAt = System.currentTimeMillis(),
                    ),
                ),
            )
        } else {
            val current = dao.getCategory(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
            // Renaming a built-in category turns it into a custom one (the translation no longer applies).
            val systemKey = if (current.name == name) current.systemKey else null
            dao.updateCategory(current.copy(name = name, budgetGroup = budgetGroup, systemKey = systemKey))
            DataResult.Success(id)
        }
    }

    suspend fun setCategoryActive(id: Long, active: Boolean): DataResult<Unit> = safeWrite {
        val current = dao.getCategory(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.updateCategory(current.copy(isActive = active))
        DataResult.Success(Unit)
    }

    /** Only unused categories can be deleted; used ones should be hidden (deactivated). */
    suspend fun deleteCategory(id: Long): DataResult<Unit> = safeWrite {
        val current = dao.getCategory(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        if (dao.countInCategory(id) > 0) return@safeWrite DataResult.Failure(DataError.IN_USE)
        dao.deleteCategory(current)
        DataResult.Success(Unit)
    }

    // ---- Expenses ------------------------------------------------------------------------

    fun observeExpenses(type: ExpenseType, range: DateRange, query: String, limit: Int = 300): Flow<List<ExpenseEntity>> =
        dao.observeExpenses(businessId, type, range.startEpochDay, range.endEpochDay, likePattern(query), limit)

    fun observeTotal(type: ExpenseType, range: DateRange): Flow<Long> =
        dao.observeTotal(businessId, type, range.startEpochDay, range.endEpochDay)

    suspend fun get(id: Long): ExpenseEntity? = dao.get(id)

    /**
     * Saves an expense and keeps its cash-drawer movement in sync (inside one transaction).
     * Only cash expenses paid from the counter reduce expected cash.
     */
    suspend fun save(input: ExpenseInput, id: Long? = null): DataResult<Long> = safeWrite {
        if (input.amountMinor <= 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        db.withTransaction {
            val category = dao.getCategory(input.categoryId)
            if (category == null || category.type != input.type) return@withTransaction DataResult.Failure(DataError.INVALID)
            val now = System.currentTimeMillis()
            val fromCounter = input.paidFromCounter && input.paymentMethod == PaymentMethod.CASH
            val expenseId = if (id == null) {
                dao.insert(
                    ExpenseEntity(
                        businessId = businessId,
                        type = input.type,
                        categoryId = category.id,
                        categoryName = category.name,
                        amountMinor = input.amountMinor,
                        expenseDate = input.dateEpochDay,
                        paymentMethod = input.paymentMethod,
                        paidFromCounter = fromCounter,
                        note = input.note,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                val current = dao.get(id) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
                dao.update(
                    current.copy(
                        type = input.type,
                        categoryId = category.id,
                        categoryName = category.name,
                        amountMinor = input.amountMinor,
                        expenseDate = input.dateEpochDay,
                        paymentMethod = input.paymentMethod,
                        paidFromCounter = fromCounter,
                        note = input.note,
                        updatedAt = now,
                    ),
                )
                cashDao.deleteByReference(CashTransactionEntity.REF_EXPENSE, id)
                id
            }
            if (fromCounter) {
                cashDao.insertTransaction(
                    CashTransactionEntity(
                        businessId = businessId,
                        type = CashTxType.EXPENSE,
                        amountMinor = CashCalculator.signedAmount(CashTxType.EXPENSE, input.amountMinor),
                        txDate = input.dateEpochDay,
                        referenceType = CashTransactionEntity.REF_EXPENSE,
                        referenceId = expenseId,
                        note = category.name,
                        createdAt = now,
                    ),
                )
            }
            DataResult.Success(expenseId)
        }
    }

    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        db.withTransaction {
            val current = dao.get(id) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
            cashDao.deleteByReference(CashTransactionEntity.REF_EXPENSE, id)
            dao.delete(current)
            DataResult.Success(Unit)
        }
    }

    suspend fun expensesBetween(range: DateRange): List<ExpenseEntity> =
        dao.expensesBetween(businessId, range.startEpochDay, range.endEpochDay)
}
