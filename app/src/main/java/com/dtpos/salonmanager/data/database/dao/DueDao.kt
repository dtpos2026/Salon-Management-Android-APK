package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.data.database.entities.DuePaymentEntity
import com.dtpos.salonmanager.data.database.model.ReceivedTotalRow
import kotlinx.coroutines.flow.Flow

@Dao
interface DueDao {
    @Insert
    suspend fun insert(due: DueEntity): Long

    @Update
    suspend fun update(due: DueEntity)

    @Query("DELETE FROM customer_dues WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM customer_dues WHERE id = :id")
    suspend fun get(id: Long): DueEntity?

    @Query("SELECT * FROM customer_dues WHERE saleId = :saleId LIMIT 1")
    suspend fun getBySale(saleId: Long): DueEntity?

    @Query("SELECT * FROM customer_dues WHERE businessId = :businessId AND settledAt IS NULL ORDER BY createdAt DESC")
    fun observeOpen(businessId: Long): Flow<List<DueEntity>>

    @Query("SELECT * FROM customer_dues WHERE businessId = :businessId AND settledAt IS NOT NULL ORDER BY settledAt DESC LIMIT :limit")
    fun observeSettled(businessId: Long, limit: Int): Flow<List<DueEntity>>

    @Query("SELECT COALESCE(SUM(amountMinor - paidMinor), 0) FROM customer_dues WHERE businessId = :businessId AND settledAt IS NULL")
    fun observeOpenTotal(businessId: Long): Flow<Long>

    @Insert
    suspend fun insertPayment(payment: DuePaymentEntity): Long

    @Query("SELECT * FROM due_payments WHERE dueId = :dueId ORDER BY createdAt DESC")
    fun observePayments(dueId: Long): Flow<List<DuePaymentEntity>>

    @Query("SELECT * FROM due_payments WHERE dueId = :dueId ORDER BY createdAt DESC")
    suspend fun payments(dueId: Long): List<DuePaymentEntity>

    /** Udhaar collected per place (cash, each account) for the given business days. */
    @Query(
        """
        SELECT paymentMethod, paymentAccountId AS accountId, MAX(paymentAccountName) AS accountName,
            COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS saleCount
        FROM due_payments
        WHERE businessId = :businessId AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY paymentMethod, paymentAccountId
        ORDER BY totalMinor DESC
        """,
    )
    fun observeCollected(businessId: Long, fromDay: Long, toDay: Long): Flow<List<ReceivedTotalRow>>

    @Query(
        """
        SELECT paymentMethod, paymentAccountId AS accountId, MAX(paymentAccountName) AS accountName,
            COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS saleCount
        FROM due_payments
        WHERE businessId = :businessId AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY paymentMethod, paymentAccountId
        ORDER BY totalMinor DESC
        """,
    )
    suspend fun collected(businessId: Long, fromDay: Long, toDay: Long): List<ReceivedTotalRow>
}
