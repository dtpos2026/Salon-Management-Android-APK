package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.entities.StaffPaymentEntity
import com.dtpos.salonmanager.data.database.model.PaymentTypeTotalRow
import com.dtpos.salonmanager.data.database.model.StaffAmountRow
import com.dtpos.salonmanager.data.database.model.StaffPaymentTypeTotalRow
import com.dtpos.salonmanager.data.database.model.StaffPerformanceRow
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(staff: StaffEntity): Long

    @Update
    suspend fun update(staff: StaffEntity)

    @Delete
    suspend fun delete(staff: StaffEntity)

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun get(id: Long): StaffEntity?

    @Query("SELECT * FROM staff WHERE id = :id")
    fun observe(id: Long): Flow<StaffEntity?>

    @Query(
        """
        SELECT * FROM staff
        WHERE businessId = :businessId AND (name LIKE :pattern ESCAPE '\' OR phone LIKE :pattern ESCAPE '\')
        ORDER BY isActive DESC, name COLLATE NOCASE
        """,
    )
    fun observeAll(businessId: Long, pattern: String): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE businessId = :businessId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeActive(businessId: Long): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE businessId = :businessId ORDER BY name COLLATE NOCASE")
    suspend fun getAll(businessId: Long): List<StaffEntity>

    @Query("SELECT * FROM staff WHERE businessId = :businessId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(businessId: Long, name: String): StaffEntity?

    @Query("SELECT COUNT(*) FROM sale_items WHERE staffId = :staffId")
    suspend fun countSaleItems(staffId: Long): Int

    @Query("SELECT COUNT(*) FROM staff_payments WHERE staffId = :staffId")
    suspend fun countPayments(staffId: Long): Int

    // ---- Payments ------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: StaffPaymentEntity): Long

    @Delete
    suspend fun deletePayment(payment: StaffPaymentEntity)

    @Query("SELECT * FROM staff_payments WHERE id = :id")
    suspend fun getPayment(id: Long): StaffPaymentEntity?

    @Query("SELECT * FROM staff_payments WHERE staffId = :staffId ORDER BY paymentDate DESC, createdAt DESC LIMIT :limit")
    fun observePayments(staffId: Long, limit: Int): Flow<List<StaffPaymentEntity>>

    @Query(
        """
        SELECT type, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM staff_payments
        WHERE staffId = :staffId AND paymentDate BETWEEN :fromDay AND :toDay
        GROUP BY type
        """,
    )
    fun observePaymentTotalsByType(staffId: Long, fromDay: Long, toDay: Long): Flow<List<PaymentTypeTotalRow>>

    @Query(
        """
        SELECT staffId, type, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM staff_payments
        WHERE businessId = :businessId AND paymentDate BETWEEN :fromDay AND :toDay
        GROUP BY staffId, type
        """,
    )
    fun observeAllPaymentTotals(businessId: Long, fromDay: Long, toDay: Long): Flow<List<StaffPaymentTypeTotalRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM staff_payments
        WHERE businessId = :businessId AND paymentDate BETWEEN :fromDay AND :toDay
        """,
    )
    fun observeTotalPaid(businessId: Long, fromDay: Long, toDay: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM staff_payments
        WHERE businessId = :businessId AND paymentDate BETWEEN :fromDay AND :toDay
        """,
    )
    suspend fun totalPaid(businessId: Long, fromDay: Long, toDay: Long): Long

    @Query(
        """
        SELECT * FROM staff_payments
        WHERE businessId = :businessId AND paymentDate BETWEEN :fromDay AND :toDay
        ORDER BY paymentDate, createdAt
        """,
    )
    suspend fun paymentsBetween(businessId: Long, fromDay: Long, toDay: Long): List<StaffPaymentEntity>

    // ---- Performance ---------------------------------------------------------------------

    @Query(
        """
        SELECT i.staffId AS staffId, MAX(i.staffName) AS staffName,
            COUNT(DISTINCT i.saleId) AS customerCount, SUM(i.quantity) AS serviceCount,
            SUM(i.netAmountMinor) AS salesMinor, SUM(i.commissionMinor) AS commissionMinor
        FROM sale_items i
        INNER JOIN sales s ON s.id = i.saleId
        WHERE s.businessId = :businessId AND s.status = 'COMPLETED'
            AND s.businessDate BETWEEN :fromDay AND :toDay
        GROUP BY i.staffId
        ORDER BY salesMinor DESC
        """,
    )
    fun observePerformance(businessId: Long, fromDay: Long, toDay: Long): Flow<List<StaffPerformanceRow>>

    @Query(
        """
        SELECT i.staffId AS staffId, MAX(i.staffName) AS staffName,
            COUNT(DISTINCT i.saleId) AS customerCount, COALESCE(SUM(i.quantity), 0) AS serviceCount,
            COALESCE(SUM(i.netAmountMinor), 0) AS salesMinor, COALESCE(SUM(i.commissionMinor), 0) AS commissionMinor
        FROM sale_items i
        INNER JOIN sales s ON s.id = i.saleId
        WHERE i.staffId = :staffId AND s.status = 'COMPLETED'
            AND s.businessDate BETWEEN :fromDay AND :toDay
        """,
    )
    fun observeStaffPerformance(staffId: Long, fromDay: Long, toDay: Long): Flow<StaffPerformanceRow>

    @Query(
        """
        SELECT i.staffId AS staffId, COALESCE(SUM(i.commissionMinor), 0) AS amountMinor
        FROM sale_items i
        INNER JOIN sales s ON s.id = i.saleId
        WHERE s.businessId = :businessId AND s.status = 'COMPLETED' AND i.staffId IS NOT NULL
            AND s.businessDate BETWEEN :fromDay AND :toDay
        GROUP BY i.staffId
        """,
    )
    fun observeCommissionByStaff(businessId: Long, fromDay: Long, toDay: Long): Flow<List<StaffAmountRow>>
}
