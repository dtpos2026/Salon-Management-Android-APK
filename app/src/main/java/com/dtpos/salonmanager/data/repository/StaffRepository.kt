package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.entities.StaffPaymentEntity
import com.dtpos.salonmanager.data.database.model.StaffPerformanceRow
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.calc.StaffPayCalculator
import com.dtpos.salonmanager.domain.calc.StaffSettlement
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import com.dtpos.salonmanager.domain.model.StaffRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class StaffInput(
    val name: String,
    val phone: String?,
    val role: StaffRole,
    val salaryType: SalaryType,
    val commissionBps: Int,
    val fixedSalaryMinor: Long,
    val isActive: Boolean,
    val joinedOnEpochDay: Long?,
)

data class StaffPaymentInput(
    val staffId: Long,
    val amountMinor: Long,
    val type: StaffPaymentType,
    val dateEpochDay: Long,
    val paymentMethod: PaymentMethod,
    val paidFromCounter: Boolean,
    val note: String?,
)

/** Staff member with the settlement of the selected month. */
data class StaffSettlementRow(val staff: StaffEntity, val settlement: StaffSettlement)

@OptIn(ExperimentalCoroutinesApi::class)
class StaffRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.staffDao()
    private val cashDao = db.cashDao()

    fun observeAll(query: String = ""): Flow<List<StaffEntity>> = dao.observeAll(businessId, likePattern(query))

    fun observeActive(): Flow<List<StaffEntity>> = dao.observeActive(businessId)

    fun observe(id: Long): Flow<StaffEntity?> = dao.observe(id)

    suspend fun get(id: Long): StaffEntity? = dao.get(id)

    suspend fun save(input: StaffInput, id: Long? = null): DataResult<Long> = safeWrite {
        val duplicate = dao.findByName(businessId, input.name)
        if (duplicate != null && duplicate.id != id) return@safeWrite DataResult.Failure(DataError.DUPLICATE_NAME)
        val now = System.currentTimeMillis()
        if (id == null) {
            DataResult.Success(
                dao.insert(
                    StaffEntity(
                        businessId = businessId,
                        name = input.name,
                        phone = input.phone,
                        role = input.role,
                        salaryType = input.salaryType,
                        commissionBps = input.commissionBps,
                        fixedSalaryMinor = input.fixedSalaryMinor,
                        isActive = input.isActive,
                        joinedOn = input.joinedOnEpochDay,
                        createdAt = now,
                        updatedAt = now,
                    ),
                ),
            )
        } else {
            val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
            dao.update(
                current.copy(
                    name = input.name,
                    phone = input.phone,
                    role = input.role,
                    salaryType = input.salaryType,
                    commissionBps = input.commissionBps,
                    fixedSalaryMinor = input.fixedSalaryMinor,
                    isActive = input.isActive,
                    joinedOn = input.joinedOnEpochDay,
                    updatedAt = now,
                ),
            )
            DataResult.Success(id)
        }
    }

    suspend fun setActive(id: Long, active: Boolean): DataResult<Unit> = safeWrite {
        val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.update(current.copy(isActive = active, updatedAt = System.currentTimeMillis()))
        DataResult.Success(Unit)
    }

    /** Staff with sales or payment history cannot be deleted (would lose records); deactivate instead. */
    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        if (dao.countSaleItems(id) > 0 || dao.countPayments(id) > 0) return@safeWrite DataResult.Failure(DataError.IN_USE)
        dao.delete(current)
        DataResult.Success(Unit)
    }

    // ---- Payments ------------------------------------------------------------------------

    fun observePayments(staffId: Long, limit: Int = 200): Flow<List<StaffPaymentEntity>> = dao.observePayments(staffId, limit)

    /** Records a payment and, when paid in cash from the drawer, the matching cash movement. */
    suspend fun recordPayment(input: StaffPaymentInput): DataResult<Long> = safeWrite {
        if (input.amountMinor <= 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        db.withTransaction {
            dao.get(input.staffId) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
            val now = System.currentTimeMillis()
            val fromCounter = input.paidFromCounter && input.paymentMethod == PaymentMethod.CASH
            val id = dao.insertPayment(
                StaffPaymentEntity(
                    businessId = businessId,
                    staffId = input.staffId,
                    amountMinor = input.amountMinor,
                    type = input.type,
                    paymentDate = input.dateEpochDay,
                    paymentMethod = input.paymentMethod,
                    paidFromCounter = fromCounter,
                    note = input.note,
                    createdAt = now,
                ),
            )
            if (fromCounter) {
                cashDao.insertTransaction(
                    CashTransactionEntity(
                        businessId = businessId,
                        type = CashTxType.STAFF_PAYMENT,
                        amountMinor = CashCalculator.signedAmount(CashTxType.STAFF_PAYMENT, input.amountMinor),
                        txDate = input.dateEpochDay,
                        referenceType = CashTransactionEntity.REF_STAFF_PAYMENT,
                        referenceId = id,
                        note = input.note,
                        createdAt = now,
                    ),
                )
            }
            DataResult.Success(id)
        }
    }

    suspend fun deletePayment(paymentId: Long): DataResult<Unit> = safeWrite {
        db.withTransaction {
            val payment = dao.getPayment(paymentId) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
            cashDao.deleteByReference(CashTransactionEntity.REF_STAFF_PAYMENT, payment.id)
            dao.deletePayment(payment)
            DataResult.Success(Unit)
        }
    }

    // ---- Performance & settlement -----------------------------------------------------------

    fun observePerformance(staffId: Long, range: DateRange): Flow<StaffPerformanceRow> =
        dao.observeStaffPerformance(staffId, range.startEpochDay, range.endEpochDay)

    fun observeTeamPerformance(range: DateRange): Flow<List<StaffPerformanceRow>> =
        dao.observePerformance(businessId, range.startEpochDay, range.endEpochDay)

    fun observeSettlement(staffId: Long, month: DateRange): Flow<StaffSettlement?> =
        dao.observe(staffId).flatMapLatest { staff ->
            if (staff == null) {
                flowOf(null)
            } else {
                combine(
                    dao.observeStaffPerformance(staffId, month.startEpochDay, month.endEpochDay),
                    dao.observePaymentTotalsByType(staffId, month.startEpochDay, month.endEpochDay),
                ) { performance, payments ->
                    StaffPayCalculator.settlement(
                        salaryType = staff.salaryType,
                        monthlyFixedSalaryMinor = staff.fixedSalaryMinor,
                        commissionEarnedMinor = performance.commissionMinor,
                        paymentsByType = payments.associate { it.type to it.totalMinor },
                    )
                }
            }
        }

    /** Settlement for every active staff member in [month]. */
    fun observeSettlements(month: DateRange): Flow<List<StaffSettlementRow>> = combine(
        dao.observeActive(businessId),
        dao.observeCommissionByStaff(businessId, month.startEpochDay, month.endEpochDay),
        dao.observeAllPaymentTotals(businessId, month.startEpochDay, month.endEpochDay),
    ) { staff, commissions, payments ->
        val commissionByStaff = commissions.associate { it.staffId to it.amountMinor }
        val paymentsByStaff = payments.groupBy { it.staffId }
        staff.map { member ->
            StaffSettlementRow(
                staff = member,
                settlement = StaffPayCalculator.settlement(
                    salaryType = member.salaryType,
                    monthlyFixedSalaryMinor = member.fixedSalaryMinor,
                    commissionEarnedMinor = commissionByStaff[member.id] ?: 0L,
                    paymentsByType = paymentsByStaff[member.id].orEmpty().associate { it.type to it.totalMinor },
                ),
            )
        }
    }

    fun observeTotalPaid(range: DateRange): Flow<Long> = dao.observeTotalPaid(businessId, range.startEpochDay, range.endEpochDay)

    suspend fun getAll(): List<StaffEntity> = dao.getAll(businessId)

    suspend fun paymentsBetween(range: DateRange): List<StaffPaymentEntity> =
        dao.paymentsBetween(businessId, range.startEpochDay, range.endEpochDay)
}
