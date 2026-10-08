package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.data.database.entities.DuePaymentEntity
import com.dtpos.salonmanager.domain.model.PaymentMethod
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
    private val accountDao = db.paymentAccountDao()

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
     * Older form: [intoCashDrawer] = cash, otherwise an unspecified (non-cash) payment.
     */
    suspend fun recordPayment(id: Long, amountMinor: Long, intoCashDrawer: Boolean = false): DataResult<Long> =
        receivePayment(id, amountMinor, if (intoCashDrawer) PaymentMethod.CASH else PaymentMethod.OTHER, accountId = null)

    /**
     * Records money received against a pending bill and returns the remaining balance; the bill
     * is settled once fully paid. Only the part up to the balance is recorded (never more than
     * owed). Cash goes into the cash drawer; an online payment ([accountId] = JazzCash / bank
     * account, or a non-cash [method] without an account) is recorded against that account
     * and never touches the cash drawer. Everything happens in one transaction.
     */
    suspend fun receivePayment(id: Long, amountMinor: Long, method: PaymentMethod, accountId: Long?): DataResult<Long> = safeWrite {
        db.withTransaction {
            val due = dao.get(id) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
            if (amountMinor <= 0 || due.settledAt != null) return@withTransaction DataResult.Failure(DataError.INVALID)
            val account = accountId?.let { accountDao.get(it) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND) }
            val payMethod = account?.let { PaymentAccountRepository.methodFor(it.kind) } ?: method
            val paid = (due.paidMinor + amountMinor).coerceAtMost(due.amountMinor)
            val received = paid - due.paidMinor
            if (received <= 0) return@withTransaction DataResult.Failure(DataError.INVALID)
            val now = clock()
            val day = businessDay(now)
            val updated = due.copy(paidMinor = paid, settledAt = if (paid >= due.amountMinor) now else null)
            dao.update(updated)
            dao.insertPayment(
                DuePaymentEntity(
                    businessId = businessId,
                    dueId = due.id,
                    amountMinor = received,
                    paymentMethod = payMethod,
                    paymentAccountId = account?.id,
                    paymentAccountName = account?.name,
                    businessDate = day,
                    createdAt = now,
                ),
            )
            if (payMethod == PaymentMethod.CASH) {
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

    /** Payment history of one bill, newest first. */
    fun observePayments(dueId: Long): Flow<List<DuePaymentEntity>> = dao.observePayments(dueId)

    suspend fun get(id: Long): DueEntity? = dao.get(id)

    /**
     * A payment with its bill as it stood right after that payment (for its receipt): the bill and
     * how much had been paid by then, this payment included.
     */
    suspend fun receiptOf(dueId: Long, paymentId: Long?): DuePaymentReceipt? {
        val due = dao.get(dueId) ?: return null
        val payments = dao.payments(dueId)
        val payment = (if (paymentId == null) payments.maxByOrNull { it.id } else payments.firstOrNull { it.id == paymentId }) ?: return null
        val paidLater = payments.filter { it.id > payment.id }.sumOf { it.amountMinor }
        return DuePaymentReceipt(due, payment, (due.paidMinor - paidLater).coerceIn(0, due.amountMinor))
    }

    /** Today, or tomorrow once today is closed (same rule as sales). */
    private suspend fun businessDay(now: Long): Long {
        var day = DateTimeUtils.toLocalDate(now).toEpochDay()
        if (cashDao.getSession(businessId, day)?.status == CashSessionStatus.CLOSED) day++
        return day
    }

    suspend fun markReminded(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(lastReminderAt = clock())) }
    }

    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        dao.delete(id)
        DataResult.Success(Unit)
    }
}

/** One udhaar payment for its receipt; [paidSoFarMinor] includes this payment. */
data class DuePaymentReceipt(val due: DueEntity, val payment: DuePaymentEntity, val paidSoFarMinor: Long) {
    val balanceAfterMinor: Long get() = (due.amountMinor - paidSoFarMinor).coerceAtLeast(0)
}
