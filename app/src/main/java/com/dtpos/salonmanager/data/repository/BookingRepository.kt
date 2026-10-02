package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.BookingEntity
import com.dtpos.salonmanager.domain.model.BookingStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Rush-hour tokens and advance bookings. Each day's tokens start at 1. */
class BookingRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.bookingDao()

    fun observeDay(date: LocalDate): Flow<List<BookingEntity>> = dao.observeDay(businessId, date.toEpochDay())

    fun observeUpcoming(after: LocalDate, limit: Int = 30): Flow<List<BookingEntity>> = dao.observeUpcoming(businessId, after.toEpochDay(), limit)

    /** Issues the next token of [date]; with [timeMinutes] it is an advance booking. Returns the saved booking. */
    suspend fun issue(
        date: LocalDate,
        customerName: String,
        customerPhone: String?,
        service: String?,
        timeMinutes: Int? = null,
        customerId: Long? = null,
        note: String? = null,
    ): DataResult<BookingEntity> = safeWrite {
        if (timeMinutes != null && timeMinutes !in 0 until 24 * 60) return@safeWrite DataResult.Failure(DataError.INVALID)
        val now = clock()
        var saved: BookingEntity? = null
        db.withTransaction {
            val token = dao.maxToken(businessId, date.toEpochDay()) + 1
            val booking = BookingEntity(
                businessId = businessId,
                dateEpochDay = date.toEpochDay(),
                tokenNumber = token,
                timeMinutes = timeMinutes,
                customerId = customerId,
                customerName = customerName.trim().ifEmpty { "#$token" },
                customerPhone = customerPhone?.trim()?.ifEmpty { null },
                service = service?.trim()?.ifEmpty { null },
                staffName = null,
                note = note?.trim()?.ifEmpty { null },
                status = BookingStatus.WAITING,
                createdAt = now,
                updatedAt = now,
            )
            saved = booking.copy(id = dao.insert(booking))
        }
        DataResult.Success(saved!!)
    }

    suspend fun setStatus(id: Long, status: BookingStatus): DataResult<Unit> = safeWrite {
        val booking = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.update(booking.copy(status = status, updatedAt = clock()))
        DataResult.Success(Unit)
    }

    /** Finishes whoever is being served and calls the next waiting token. Returns the called token. */
    suspend fun callNext(queue: List<BookingEntity>): BookingEntity? {
        queue.filter { it.status == BookingStatus.SERVING }.forEach { dao.update(it.copy(status = BookingStatus.DONE, updatedAt = clock())) }
        val next = queue.firstOrNull { it.status == BookingStatus.WAITING } ?: return null
        val serving = next.copy(status = BookingStatus.SERVING, updatedAt = clock())
        dao.update(serving)
        return serving
    }
}
