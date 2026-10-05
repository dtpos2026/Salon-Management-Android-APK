package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashSessionEntity
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.domain.calc.CashBreakdown
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.model.CashSessionStatus
import com.dtpos.salonmanager.domain.model.CashTxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class CashDayState(
    val date: LocalDate,
    val session: CashSessionEntity?,
    val breakdown: CashBreakdown,
    val transactions: List<CashTransactionEntity>,
) {
    val isClosed: Boolean get() = session?.status == CashSessionStatus.CLOSED
}

class CashRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.cashDao()

    fun observeDay(date: LocalDate): Flow<CashDayState> {
        val day = date.toEpochDay()
        return combine(
            dao.observeSession(businessId, day),
            dao.observeTotalsByType(businessId, day),
            dao.observeTransactions(businessId, day),
        ) { session, totals, transactions ->
            CashDayState(
                date = date,
                session = session,
                breakdown = CashCalculator.breakdown(
                    openingMinor = session?.openingCashMinor ?: 0L,
                    signedTotalsByType = totals.associate { it.type to it.totalMinor },
                ),
                transactions = transactions,
            )
        }
    }

    /**
     * The day new sales count for: [today], or the next day once the owner has closed today.
     * After "Close day" the dashboard starts again from zero.
     */
    fun observeBusinessDay(today: LocalDate): Flow<LocalDate> =
        dao.observeSession(businessId, today.toEpochDay()).map { if (it?.status == CashSessionStatus.CLOSED) today.plusDays(1) else today }

    suspend fun businessDay(today: LocalDate): LocalDate =
        if (dao.getSession(businessId, today.toEpochDay())?.status == CashSessionStatus.CLOSED) today.plusDays(1) else today

    fun observeHistory(limit: Int = 60): Flow<List<CashSessionEntity>> = dao.observeRecentSessions(businessId, limit)

    /** Yesterday's counted closing cash is the natural opening float for today. */
    suspend fun suggestedOpening(date: LocalDate): Long? =
        dao.lastClosedBefore(businessId, date.toEpochDay())?.actualClosingMinor

    suspend fun setOpeningCash(date: LocalDate, openingMinor: Long): DataResult<Unit> = safeWrite {
        if (openingMinor < 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        db.withTransaction {
            val day = date.toEpochDay()
            val existing = dao.getSession(businessId, day)
            when {
                existing == null -> dao.insertSession(
                    CashSessionEntity(
                        businessId = businessId,
                        sessionDate = day,
                        openingCashMinor = openingMinor,
                        status = CashSessionStatus.OPEN,
                        openedAt = System.currentTimeMillis(),
                    ),
                )
                existing.status == CashSessionStatus.CLOSED -> return@withTransaction DataResult.Failure(DataError.ALREADY_CLOSED)
                else -> dao.updateSession(existing.copy(openingCashMinor = openingMinor))
            }
            DataResult.Success(Unit)
        }
    }

    /** Closes the day with the counted cash and stores expected cash and the difference. */
    suspend fun closeDay(date: LocalDate, actualClosingMinor: Long, note: String?): DataResult<Long> = safeWrite {
        if (actualClosingMinor < 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        db.withTransaction {
            val day = date.toEpochDay()
            val now = System.currentTimeMillis()
            val session = dao.getSession(businessId, day) ?: CashSessionEntity(
                businessId = businessId,
                sessionDate = day,
                openingCashMinor = 0L,
                status = CashSessionStatus.OPEN,
                openedAt = now,
            ).let { it.copy(id = dao.insertSession(it)) }
            val breakdown = CashCalculator.breakdown(
                session.openingCashMinor,
                dao.totalsByType(businessId, day).associate { it.type to it.totalMinor },
            )
            val difference = breakdown.differenceFor(actualClosingMinor)
            dao.updateSession(
                session.copy(
                    expectedClosingMinor = breakdown.expectedClosingMinor,
                    actualClosingMinor = actualClosingMinor,
                    differenceMinor = difference,
                    status = CashSessionStatus.CLOSED,
                    note = note?.trim()?.ifEmpty { null },
                    closedAt = now,
                ),
            )
            DataResult.Success(difference)
        }
    }

    suspend fun reopenDay(date: LocalDate): DataResult<Unit> = safeWrite {
        val session = dao.getSession(businessId, date.toEpochDay()) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.updateSession(session.copy(status = CashSessionStatus.OPEN, closedAt = null))
        DataResult.Success(Unit)
    }

    /** Manual drawer movement, e.g. owner adds change (CASH_IN) or takes cash home (CASH_OUT). */
    suspend fun addManualMovement(date: LocalDate, type: CashTxType, amountMinor: Long, note: String?): DataResult<Long> = safeWrite {
        if (type != CashTxType.CASH_IN && type != CashTxType.CASH_OUT) return@safeWrite DataResult.Failure(DataError.INVALID)
        if (amountMinor <= 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        val session = dao.getSession(businessId, date.toEpochDay())
        if (session?.status == CashSessionStatus.CLOSED) return@safeWrite DataResult.Failure(DataError.ALREADY_CLOSED)
        DataResult.Success(
            dao.insertTransaction(
                CashTransactionEntity(
                    businessId = businessId,
                    type = type,
                    amountMinor = CashCalculator.signedAmount(type, amountMinor),
                    txDate = date.toEpochDay(),
                    referenceType = null,
                    referenceId = null,
                    note = note?.trim()?.ifEmpty { null },
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    suspend fun deleteManualMovement(id: Long): DataResult<Unit> = safeWrite {
        if (dao.deleteManualTransaction(id) == 0) DataResult.Failure(DataError.NOT_FOUND) else DataResult.Success(Unit)
    }
}
