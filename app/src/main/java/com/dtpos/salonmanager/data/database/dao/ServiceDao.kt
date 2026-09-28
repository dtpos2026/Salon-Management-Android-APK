package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(service: ServiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringDuplicates(services: List<ServiceEntity>)

    @Update
    suspend fun update(service: ServiceEntity)

    @Delete
    suspend fun delete(service: ServiceEntity)

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun get(id: Long): ServiceEntity?

    @Query(
        """
        SELECT * FROM services
        WHERE businessId = :businessId AND (name LIKE :pattern ESCAPE '\' OR category LIKE :pattern ESCAPE '\')
        ORDER BY isActive DESC, category COLLATE NOCASE, sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeAll(businessId: Long, pattern: String): Flow<List<ServiceEntity>>

    @Query(
        """
        SELECT * FROM services
        WHERE businessId = :businessId AND isActive = 1
        ORDER BY category COLLATE NOCASE, sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeActive(businessId: Long): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE businessId = :businessId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(businessId: Long, name: String): ServiceEntity?

    @Query("SELECT DISTINCT category FROM services WHERE businessId = :businessId ORDER BY category COLLATE NOCASE")
    fun observeCategories(businessId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM sale_items WHERE serviceId = :serviceId")
    suspend fun countUsage(serviceId: Long): Int

    @Query("SELECT COUNT(*) FROM services WHERE businessId = :businessId")
    suspend fun count(businessId: Long): Int
}
