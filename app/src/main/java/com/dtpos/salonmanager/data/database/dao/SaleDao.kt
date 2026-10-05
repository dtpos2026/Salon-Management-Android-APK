package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.database.entities.SaleItemEntity
import com.dtpos.salonmanager.data.database.entities.VisitEntity
import com.dtpos.salonmanager.data.database.model.DayTotalRow
import com.dtpos.salonmanager.data.database.model.NamedTotalRow
import com.dtpos.salonmanager.data.database.model.PaymentMethodTotalRow
import com.dtpos.salonmanager.data.database.model.ReceivedTotalRow
import com.dtpos.salonmanager.data.database.model.SalesSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVisit(visit: VisitEntity): Long

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: Long): SaleEntity?

    @Query("SELECT * FROM sales WHERE id = :id")
    fun observeSale(id: Long): Flow<SaleEntity?>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    suspend fun getItems(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    fun observeItems(saleId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM visits WHERE saleId = :saleId")
    suspend fun getVisitForSale(saleId: Long): VisitEntity?

    @Query("SELECT COUNT(*) FROM sales WHERE businessId = :businessId AND receiptNumber = :receiptNumber")
    suspend fun countReceiptNumber(businessId: Long, receiptNumber: String): Int

    @Query("SELECT MAX(receiptSequence) FROM sales WHERE businessId = :businessId")
    suspend fun maxReceiptSequence(businessId: Long): Long?

    @Query(
        """
        UPDATE sales SET status = 'VOIDED', voidedAt = :voidedAt, voidReason = :reason
        WHERE id = :saleId AND status = 'COMPLETED'
        """,
    )
    suspend fun markVoided(saleId: Long, voidedAt: Long, reason: String?): Int

    // ---- Lists -----------------------------------------------------------------------------

    @Query(
        """
        SELECT * FROM sales
        WHERE businessId = :businessId AND businessDate BETWEEN :fromDay AND :toDay
            AND (receiptNumber LIKE :pattern ESCAPE '\' OR customerName LIKE :pattern ESCAPE '\' OR customerPhone LIKE :pattern ESCAPE '\')
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeSales(businessId: Long, fromDay: Long, toDay: Long, pattern: String, limit: Int): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE businessId = :businessId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(businessId: Long, limit: Int): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE businessId = :businessId AND businessDate BETWEEN :fromDay AND :toDay ORDER BY createdAt")
    suspend fun salesBetween(businessId: Long, fromDay: Long, toDay: Long): List<SaleEntity>

    @Query(
        """
        SELECT i.* FROM sale_items i INNER JOIN sales s ON s.id = i.saleId
        WHERE s.businessId = :businessId AND s.businessDate BETWEEN :fromDay AND :toDay
        ORDER BY i.saleId, i.id
        """,
    )
    suspend fun itemsBetween(businessId: Long, fromDay: Long, toDay: Long): List<SaleItemEntity>

    // ---- Aggregates (completed sales only) ------------------------------------------------

    @Query(
        """
        SELECT COALESCE(SUM(totalMinor), 0) AS totalMinor,
            COUNT(*) AS saleCount,
            COALESCE(SUM(CASE WHEN paymentMethod = 'CASH' THEN totalMinor - creditMinor ELSE 0 END), 0) AS cashMinor,
            COALESCE(SUM(itemDiscountMinor + saleDiscountMinor), 0) AS discountMinor,
            COALESCE(SUM(serviceCount), 0) AS serviceCount,
            COALESCE(SUM(creditMinor), 0) AS creditMinor
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        """,
    )
    fun observeSummary(businessId: Long, fromDay: Long, toDay: Long): Flow<SalesSummaryRow>

    @Query(
        """
        SELECT COALESCE(SUM(totalMinor), 0) AS totalMinor,
            COUNT(*) AS saleCount,
            COALESCE(SUM(CASE WHEN paymentMethod = 'CASH' THEN totalMinor - creditMinor ELSE 0 END), 0) AS cashMinor,
            COALESCE(SUM(itemDiscountMinor + saleDiscountMinor), 0) AS discountMinor,
            COALESCE(SUM(serviceCount), 0) AS serviceCount,
            COALESCE(SUM(creditMinor), 0) AS creditMinor
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        """,
    )
    suspend fun summary(businessId: Long, fromDay: Long, toDay: Long): SalesSummaryRow

    @Query(
        """
        SELECT businessDate AS day, COALESCE(SUM(totalMinor), 0) AS totalMinor, COUNT(*) AS count
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY businessDate
        ORDER BY businessDate
        """,
    )
    fun observeDailyTotals(businessId: Long, fromDay: Long, toDay: Long): Flow<List<DayTotalRow>>

    @Query(
        """
        SELECT businessDate AS day, COALESCE(SUM(totalMinor), 0) AS totalMinor, COUNT(*) AS count
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY businessDate
        ORDER BY businessDate
        """,
    )
    suspend fun dailyTotals(businessId: Long, fromDay: Long, toDay: Long): List<DayTotalRow>

    @Query(
        """
        SELECT paymentMethod, COALESCE(SUM(totalMinor - creditMinor), 0) AS totalMinor, COUNT(*) AS saleCount
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY paymentMethod
        ORDER BY totalMinor DESC
        """,
    )
    suspend fun paymentBreakdown(businessId: Long, fromDay: Long, toDay: Long): List<PaymentMethodTotalRow>

    @Query(
        """
        SELECT i.serviceName AS name, SUM(i.quantity) AS quantity, SUM(i.netAmountMinor) AS totalMinor
        FROM sale_items i
        INNER JOIN sales s ON s.id = i.saleId
        WHERE s.businessId = :businessId AND s.status = 'COMPLETED' AND s.businessDate BETWEEN :fromDay AND :toDay
        GROUP BY i.serviceName
        ORDER BY totalMinor DESC
        LIMIT :limit
        """,
    )
    suspend fun topServices(businessId: Long, fromDay: Long, toDay: Long, limit: Int): List<NamedTotalRow>

    @Query(
        """
        SELECT COUNT(DISTINCT customerId) FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND customerId IS NOT NULL
            AND businessDate BETWEEN :fromDay AND :toDay
        """,
    )
    suspend fun distinctCustomers(businessId: Long, fromDay: Long, toDay: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT customerId FROM sales
            WHERE businessId = :businessId AND status = 'COMPLETED' AND customerId IS NOT NULL
                AND businessDate BETWEEN :fromDay AND :toDay
            GROUP BY customerId
            HAVING COUNT(*) > 1
        )
        """,
    )
    suspend fun returningCustomers(businessId: Long, fromDay: Long, toDay: Long): Int

    /** Money received per place (cash, each account, or the plain method) - credit not included. */
    @Query(
        """
        SELECT paymentMethod, paymentAccountId AS accountId, MAX(paymentAccountName) AS accountName,
            COALESCE(SUM(totalMinor - creditMinor), 0) AS totalMinor, COUNT(*) AS saleCount
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY paymentMethod, paymentAccountId
        ORDER BY totalMinor DESC
        """,
    )
    fun observeReceivedBreakdown(businessId: Long, fromDay: Long, toDay: Long): Flow<List<ReceivedTotalRow>>

    @Query(
        """
        SELECT paymentMethod, paymentAccountId AS accountId, MAX(paymentAccountName) AS accountName,
            COALESCE(SUM(totalMinor - creditMinor), 0) AS totalMinor, COUNT(*) AS saleCount
        FROM sales
        WHERE businessId = :businessId AND status = 'COMPLETED' AND businessDate BETWEEN :fromDay AND :toDay
        GROUP BY paymentMethod, paymentAccountId
        ORDER BY totalMinor DESC
        """,
    )
    suspend fun receivedBreakdown(businessId: Long, fromDay: Long, toDay: Long): List<ReceivedTotalRow>

    @androidx.room.Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteItems(saleId: Long)

    @Query("DELETE FROM visits WHERE saleId = :saleId")
    suspend fun deleteVisit(saleId: Long)
}
