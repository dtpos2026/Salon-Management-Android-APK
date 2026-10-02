package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.BookingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Insert
    suspend fun insert(booking: BookingEntity): Long

    @Update
    suspend fun update(booking: BookingEntity)

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun get(id: Long): BookingEntity?

    @Query("SELECT COALESCE(MAX(tokenNumber), 0) FROM bookings WHERE businessId = :businessId AND dateEpochDay = :day")
    suspend fun maxToken(businessId: Long, day: Long): Int

    /** Waiting and serving first (booked times in order, then walk-ins by token), finished last. */
    @Query(
        """SELECT * FROM bookings WHERE businessId = :businessId AND dateEpochDay = :day
        ORDER BY CASE status WHEN 'SERVING' THEN 0 WHEN 'WAITING' THEN 1 WHEN 'DONE' THEN 2 ELSE 3 END,
        COALESCE(timeMinutes, 100000), tokenNumber""",
    )
    fun observeDay(businessId: Long, day: Long): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE businessId = :businessId AND dateEpochDay > :day AND status = 'WAITING' ORDER BY dateEpochDay, timeMinutes LIMIT :limit")
    fun observeUpcoming(businessId: Long, day: Long, limit: Int): Flow<List<BookingEntity>>
}
