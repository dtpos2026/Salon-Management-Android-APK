package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.data.database.model.CustomerStatsRow
import com.dtpos.salonmanager.data.database.model.NamedTotalRow
import com.dtpos.salonmanager.data.database.model.VisitRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE businessId = :businessId AND phoneNormalized = :phoneNormalized LIMIT 1")
    suspend fun findByPhone(businessId: Long, phoneNormalized: String): CustomerEntity?

    /**
     * Name / phone search with per-customer stats. [namePattern] and [phonePattern] are LIKE
     * patterns built by the repository ([phonePattern] is null when the query has no digits).
     */
    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM sales s WHERE s.customerId = c.id AND s.status = 'COMPLETED') AS visitCount,
            (SELECT MAX(s.createdAt) FROM sales s WHERE s.customerId = c.id AND s.status = 'COMPLETED') AS lastVisitAt,
            (SELECT COALESCE(SUM(s.totalMinor), 0) FROM sales s WHERE s.customerId = c.id AND s.status = 'COMPLETED') AS totalSpentMinor
        FROM customers c
        WHERE c.businessId = :businessId
            AND (c.name LIKE :namePattern ESCAPE '\' OR c.phoneNormalized LIKE :phonePattern)
        ORDER BY c.name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    fun search(businessId: Long, namePattern: String, phonePattern: String?, limit: Int): Flow<List<CustomerListRow>>

    @Query("SELECT COUNT(*) FROM customers WHERE businessId = :businessId")
    fun observeCount(businessId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM customers WHERE businessId = :businessId AND createdAt BETWEEN :fromMillis AND :toMillis")
    suspend fun countCreatedBetween(businessId: Long, fromMillis: Long, toMillis: Long): Int

    @Query(
        """
        SELECT COUNT(*) AS visitCount,
            COALESCE(SUM(totalMinor), 0) AS totalSpentMinor,
            MAX(createdAt) AS lastVisitAt,
            MIN(createdAt) AS firstVisitAt
        FROM sales
        WHERE customerId = :customerId AND status = 'COMPLETED'
        """,
    )
    fun observeStats(customerId: Long): Flow<CustomerStatsRow>

    @Query(
        """
        SELECT i.serviceName AS name, SUM(i.quantity) AS quantity, SUM(i.netAmountMinor) AS totalMinor
        FROM sale_items i
        INNER JOIN sales s ON s.id = i.saleId
        WHERE s.customerId = :customerId AND s.status = 'COMPLETED'
        GROUP BY i.serviceName
        ORDER BY quantity DESC, totalMinor DESC
        LIMIT :limit
        """,
    )
    fun observeFavoriteServices(customerId: Long, limit: Int): Flow<List<NamedTotalRow>>

    @Query(
        """
        SELECT v.saleId AS saleId, v.visitAt AS visitAt, v.servicesSummary AS servicesSummary,
            v.staffSummary AS staffSummary, v.totalMinor AS totalMinor, s.receiptNumber AS receiptNumber,
            s.paymentMethod AS paymentMethod, s.status AS status
        FROM visits v
        INNER JOIN sales s ON s.id = v.saleId
        WHERE v.customerId = :customerId
        ORDER BY v.visitAt DESC
        LIMIT :limit
        """,
    )
    fun observeVisits(customerId: Long, limit: Int): Flow<List<VisitRow>>

    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name COLLATE NOCASE")
    suspend fun getAll(businessId: Long): List<CustomerEntity>
}
