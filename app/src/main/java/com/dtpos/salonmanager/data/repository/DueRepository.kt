package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.model.CashSessionStatus
import com.dtpos.salonmanager.domain.model.CashTxType
import kotlinx.coroutines.flow.Flow

/** Pending bills (udhaar): what customers still owe, payments against it, reminders sent. */
class DueRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dueDao()
    private val cashDao = db.cashDao()

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

    /**
     * Records a payment; the due is settled once fully paid. Returns the remaining balance.
     * With [intoCashDrawer] the money is added to today's cash counter (Close Day shows it).
     */
    suspend fun recordPayment(id: Long, amountMinor: Long, intoCashDrawer: Boolean = false): DataResult<Long> = safeWrite {
        db.withTransaction {
            val due = dao.get(id) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
            if (amountMinor <= 0 || due.settledAt != null) return@withTransaction DataResult.Failure(DataError.INVALID)
            val paid = (due.paidMinor + amountMinor).coerceAtMost(due.amountMinor)
            val received = paid - due.paidMinor
            val updated = due.copy(paidMinor = paid, settledAt = if (paid >= due.amountMinor) clock() else null)
            dao.update(updated)
            if (intoCashDrawer && received > 0) {
                val now = clock()
                var day = DateTimeUtils.toLocalDate(now).toEpochDay()
                if (cashDao.getSession(businessId, day)?.status == CashSessionStatus.CLOSED) day++
                cashDao.insertTransaction(
                    CashTransactionEntity(
                        businessId = businessId,
                        type = CashTxType.CASH_IN,
                        amountMinor = CashCalculator.signedAmount(CashTxType.CASH_IN, received),
                        txDate = day,
                        referenceType = CashTransactionEntity.REF_DUE,
                        referenceId = due.id,
                        note = due.customerName,
                        createdAt = now,
                    ),
                )
            }
            DataResult.Success(updated.balanceMinor)
        }
    }

    suspend fun markReminded(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(lastReminderAt = clock())) }
    }

    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        dao.delete(id)
        DataResult.Success(Unit)
    }
}
