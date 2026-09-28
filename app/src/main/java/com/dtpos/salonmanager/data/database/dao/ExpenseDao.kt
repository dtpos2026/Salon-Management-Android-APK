package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.ExpenseCategoryEntity
import com.dtpos.salonmanager.data.database.entities.ExpenseEntity
import com.dtpos.salonmanager.data.database.model.BudgetGroupTotalRow
import com.dtpos.salonmanager.data.database.model.CategoryTotalRow
import com.dtpos.salonmanager.data.database.model.DayTotalRow
import com.dtpos.salonmanager.domain.model.ExpenseType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // ---- Categories ----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: ExpenseCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnoringDuplicates(categories: List<ExpenseCategoryEntity>)

    @Update
    suspend fun updateCategory(category: ExpenseCategoryEntity)

    @Delete
    suspend fun deleteCategory(category: ExpenseCategoryEntity)

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getCategory(id: Long): ExpenseCategoryEntity?

    @Query(
        """
        SELECT * FROM expense_categories
        WHERE businessId = :businessId AND type = :type
        ORDER BY isActive DESC, sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeCategories(businessId: Long, type: ExpenseType): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE businessId = :businessId AND type = :type AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCategory(businessId: Long, type: ExpenseType, name: String): ExpenseCategoryEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM expense_categories WHERE businessId = :businessId AND type = :type")
    suspend fun maxSortOrder(businessId: Long, type: ExpenseType): Int

    // ---- Expenses ------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    @Query(
        """
        SELECT * FROM expenses
        WHERE businessId = :businessId AND type = :type AND expenseDate BETWEEN :fromDay AND :toDay
            AND (categoryName LIKE :pattern ESCAPE '\' OR note LIKE :pattern ESCAPE '\')
        ORDER BY expenseDate DESC, createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeExpenses(businessId: Long, type: ExpenseType, fromDay: Long, toDay: Long, pattern: String, limit: Int): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM expenses
        WHERE businessId = :businessId AND type = :type AND expenseDate BETWEEN :fromDay AND :toDay
        """,
    )
    fun observeTotal(businessId: Long, type: ExpenseType, fromDay: Long, toDay: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM expenses
        WHERE businessId = :businessId AND type = :type AND expenseDate BETWEEN :fromDay AND :toDay
        """,
    )
    suspend fun total(businessId: Long, type: ExpenseType, fromDay: Long, toDay: Long): Long

    @Query(
        """
        SELECT categoryName AS name, COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS count
        FROM expenses
        WHERE businessId = :businessId AND type = :type AND expenseDate BETWEEN :fromDay AND :toDay
        GROUP BY categoryName
        ORDER BY totalMinor DESC
        """,
    )
    suspend fun totalsByCategory(businessId: Long, type: ExpenseType, fromDay: Long, toDay: Long): List<CategoryTotalRow>

    @Query(
        """
        SELECT c.budgetGroup AS budgetGroup, COALESCE(SUM(e.amountMinor), 0) AS totalMinor
        FROM expenses e
        LEFT JOIN expense_categories c ON c.id = e.categoryId
        WHERE e.businessId = :businessId AND e.type = :type AND e.expenseDate BETWEEN :fromDay AND :toDay
        GROUP BY c.budgetGroup
        """,
    )
    suspend fun totalsByBudgetGroup(businessId: Long, type: ExpenseType, fromDay: Long, toDay: Long): List<BudgetGroupTotalRow>

    @Query(
        """
        SELECT expenseDate AS day, COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS count
        FROM expenses
        WHERE businessId = :businessId AND type = :type AND expenseDate BETWEEN :fromDay AND :toDay
        GROUP BY expenseDate
        ORDER BY expenseDate
        """,
    )
    suspend fun dailyTotals(businessId: Long, type: ExpenseType, fromDay: Long, toDay: Long): List<DayTotalRow>

    @Query("SELECT * FROM expenses WHERE businessId = :businessId AND expenseDate BETWEEN :fromDay AND :toDay ORDER BY expenseDate, createdAt")
    suspend fun expensesBetween(businessId: Long, fromDay: Long, toDay: Long): List<ExpenseEntity>
}
