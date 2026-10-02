package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.DueEntity
import kotlinx.coroutines.flow.Flow

/** Pending bills (udhaar): what customers still owe, payments against it, reminders sent. */
class DueRepository(
    db: SalonDatabase,
    private val businessId: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dueDao()

    fun observeOpen(): Flow<List<DueEntity>> = dao.observeOpen(businessId)

    fun observeSettled(limit: Int = 50): Flow<List<DueEntity>> = dao.observeSettled(businessId, limit)

    fun observeOpenTotal(): Flow<Long> = dao.observeOpenTotal(businessId)

    suspend fun add(customerId: Long?, customerName: String, customerPhone: String?, amountMinor: Long, note: String?): DataResult<Long> = safeWrite {
        val name = customerName.trim()
        if (name.isEmpty() || amountMinor <= 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        DataResult.Success(
            dao.insert(
                DueEntity(
                    businessId = businessId,
                    customerId = customerId,
                    customerName = name,
                    customerPhone = customerPhone?.trim()?.ifEmpty { null },
                    amountMinor = amountMinor,
                    paidMinor = 0,
                    note = note?.trim()?.ifEmpty { null },
                    createdAt = clock(),
                    settledAt = null,
                    lastReminderAt = null,
                ),
            ),
        )
    }

    /** Records a payment; the due is settled once fully paid. Returns the remaining balance. */
    suspend fun recordPayment(id: Long, amountMinor: Long): DataResult<Long> = safeWrite {
        val due = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        if (amountMinor <= 0 || due.settledAt != null) return@safeWrite DataResult.Failure(DataError.INVALID)
        val paid = (due.paidMinor + amountMinor).coerceAtMost(due.amountMinor)
        val updated = due.copy(paidMinor = paid, settledAt = if (paid >= due.amountMinor) clock() else null)
        dao.update(updated)
        DataResult.Success(updated.balanceMinor)
    }

    suspend fun markReminded(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(lastReminderAt = clock())) }
    }

    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        dao.delete(id)
        DataResult.Success(Unit)
    }
}
