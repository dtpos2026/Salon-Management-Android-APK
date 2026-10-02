package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.DueEntity
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

    @Query("SELECT * FROM customer_dues WHERE businessId = :businessId AND settledAt IS NULL ORDER BY createdAt DESC")
    fun observeOpen(businessId: Long): Flow<List<DueEntity>>

    @Query("SELECT * FROM customer_dues WHERE businessId = :businessId AND settledAt IS NOT NULL ORDER BY settledAt DESC LIMIT :limit")
    fun observeSettled(businessId: Long, limit: Int): Flow<List<DueEntity>>

    @Query("SELECT COALESCE(SUM(amountMinor - paidMinor), 0) FROM customer_dues WHERE businessId = :businessId AND settledAt IS NULL")
    fun observeOpenTotal(businessId: Long): Flow<Long>
}
