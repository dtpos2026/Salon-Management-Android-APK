package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.data.database.entities.ReceiptSequenceEntity
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.database.entities.SaleItemEntity
import com.dtpos.salonmanager.data.database.entities.VisitEntity
import com.dtpos.salonmanager.data.database.model.DayTotalRow
import com.dtpos.salonmanager.data.database.model.ReceivedTotalRow
import com.dtpos.salonmanager.data.database.model.SalesSummaryRow
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.calc.ReceiptNumbering
import com.dtpos.salonmanager.domain.calc.SaleCalculator
import com.dtpos.salonmanager.domain.model.CashSessionStatus
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.NewSaleRequest
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.domain.model.SaleStatus
import com.dtpos.salonmanager.domain.model.SaleTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SaleWithItems(val sale: SaleEntity, val items: List<SaleItemEntity>)

class SaleRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
    /** Returns true while the licence only allows read access. */
    private val isReadOnly: () -> Boolean = { false },
) {
    private val saleDao = db.saleDao()
    private val cashDao = db.cashDao()
    private val businessDao = db.businessDao()
    private val dueDao = db.dueDao()

    /**
     * Records a sale atomically: receipt number, sale, sale items, customer visit and the cash
     * drawer movement are written in ONE transaction - either everything is saved or nothing.
     *
     * @return the new sale id
     */
    suspend fun completeSale(request: NewSaleRequest, atMillis: Long = System.currentTimeMillis()): DataResult<Long> {
        if (isReadOnly()) return DataResult.Failure(DataError.READ_ONLY)
        val tendered = request.amountTenderedMinor?.takeIf { request.paymentMethod == PaymentMethod.CASH && request.creditMinor == 0L && it > 0 }
        if (SaleCalculator.validate(request.lines, request.discount, tendered).isNotEmpty()) {
            return DataResult.Failure(DataError.INVALID)
        }
        val totals = SaleCalculator.calculate(request.lines, request.discount)
        if (!creditValid(request, totals.totalMinor)) return DataResult.Failure(DataError.INVALID)

        return safeWrite {
            db.withTransaction {
                val business = businessDao.get(businessId) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
                val businessDate = businessDayFor(atMillis)
                val sequence = nextReceiptSequence()
                val receiptNumber = ReceiptNumbering.format(business.receiptPrefix, sequence)
                check(saleDao.countReceiptNumber(businessId, receiptNumber) == 0) { "duplicate receipt number" }

                val saleId = saleDao.insertSale(
                    SaleEntity(
                        businessId = businessId,
                        receiptNumber = receiptNumber,
                        receiptSequence = sequence,
                        customerId = request.customerId,
                        customerName = request.customerName?.trim()?.ifEmpty { null },
                        customerPhone = request.customerPhone?.trim()?.ifEmpty { null },
                        subtotalMinor = totals.subtotalMinor,
                        itemDiscountMinor = totals.itemDiscountMinor,
                        saleDiscountType = request.discount.type,
                        saleDiscountValue = request.discount.value,
                        saleDiscountMinor = totals.saleDiscountMinor,
                        totalMinor = totals.totalMinor,
                        paymentMethod = request.paymentMethod,
                        amountTenderedMinor = tendered,
                        changeMinor = SaleCalculator.change(totals.totalMinor, tendered),
                        serviceCount = totals.serviceCount,
                        note = request.note?.trim()?.ifEmpty { null },
                        status = SaleStatus.COMPLETED,
                        businessDate = businessDate,
                        createdAt = atMillis,
                        paymentAccountId = request.paymentAccountId,
                        paymentAccountName = request.paymentAccountName?.trim()?.ifEmpty { null },
                        creditMinor = request.creditMinor,
                    ),
                )
                insertItemsAndVisit(saleId, request, totals, businessDate, atMillis)

                val paid = totals.totalMinor - request.creditMinor
                if (request.paymentMethod == PaymentMethod.CASH && paid > 0) {
                    insertCash(CashTxType.SALE, paid, businessDate, saleId, receiptNumber, atMillis)
                }
                if (request.creditMinor > 0) {
                    dueDao.insert(
                        DueEntity(
                            businessId = businessId,
                            customerId = request.customerId,
                            customerName = request.customerName!!.trim(),
                            customerPhone = request.customerPhone?.trim()?.ifEmpty { null },
                            amountMinor = request.creditMinor,
                            paidMinor = 0,
                            note = receiptNumber,
                            createdAt = atMillis,
                            settledAt = null,
                            lastReminderAt = null,
                            saleId = saleId,
                        ),
                    )
                }
                DataResult.Success(saleId)
            }
        }
    }

    /**
     * Corrects a completed receipt (wrong service, price, staff or payment punched). The receipt
     * number and date stay; items, totals, the cash drawer and the customer's udhaar follow the
     * new figures. When the receipt's day is already closed, the cash difference is added to the
     * current day instead, so a closed day's count never changes.
     */
    suspend fun updateSale(saleId: Long, request: NewSaleRequest, atMillis: Long = System.currentTimeMillis()): DataResult<Long> {
        if (isReadOnly()) return DataResult.Failure(DataError.READ_ONLY)
        val tendered = request.amountTenderedMinor?.takeIf { request.paymentMethod == PaymentMethod.CASH && request.creditMinor == 0L && it > 0 }
        if (SaleCalculator.validate(request.lines, request.discount, tendered).isNotEmpty()) {
            return DataResult.Failure(DataError.INVALID)
        }
        val totals = SaleCalculator.calculate(request.lines, request.discount)
        if (!creditValid(request, totals.totalMinor)) return DataResult.Failure(DataError.INVALID)

        return safeWrite {
            db.withTransaction {
                val old = saleDao.getSale(saleId) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
                if (old.businessId != businessId) return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
                if (old.status != SaleStatus.COMPLETED) return@withTransaction DataResult.Failure(DataError.ALREADY_CLOSED)
                val updated = old.copy(
                    customerId = request.customerId,
                    customerName = request.customerName?.trim()?.ifEmpty { null },
                    customerPhone = request.customerPhone?.trim()?.ifEmpty { null },
                    subtotalMinor = totals.subtotalMinor,
                    itemDiscountMinor = totals.itemDiscountMinor,
                    saleDiscountType = request.discount.type,
                    saleDiscountValue = request.discount.value,
                    saleDiscountMinor = totals.saleDiscountMinor,
                    totalMinor = totals.totalMinor,
                    paymentMethod = request.paymentMethod,
                    amountTenderedMinor = tendered,
                    changeMinor = SaleCalculator.change(totals.totalMinor, tendered),
                    serviceCount = totals.serviceCount,
                    note = request.note?.trim()?.ifEmpty { null },
                    paymentAccountId = request.paymentAccountId,
                    paymentAccountName = request.paymentAccountName?.trim()?.ifEmpty { null },
                    creditMinor = request.creditMinor,
                    editedAt = atMillis,
                )
                saleDao.updateSale(updated)
                saleDao.deleteItems(saleId)
                saleDao.deleteVisit(saleId)
                insertItemsAndVisit(saleId, request, totals, old.businessDate, old.createdAt)

                // Cash drawer: what this receipt put in the drawer before vs now.
                val oldCash = if (old.paymentMethod == PaymentMethod.CASH) old.paidMinor else 0L
                val newCash = if (updated.paymentMethod == PaymentMethod.CASH) updated.paidMinor else 0L
                if (oldCash != newCash) {
                    val dayClosed = cashDao.getSession(businessId, old.businessDate)?.status == CashSessionStatus.CLOSED
                    if (!dayClosed) {
                        cashDao.deleteByReference(CashTransactionEntity.REF_SALE, saleId)
                        if (newCash > 0) insertCash(CashTxType.SALE, newCash, old.businessDate, saleId, old.receiptNumber, old.createdAt)
                    } else {
                        val diff = newCash - oldCash
                        insertCash(
                            if (diff > 0) CashTxType.SALE else CashTxType.SALE_VOID,
                            kotlin.math.abs(diff), businessDayFor(atMillis), saleId, old.receiptNumber, atMillis,
                        )
                    }
                }
                syncDue(updated, atMillis)
                DataResult.Success(saleId)
            }
        }
    }

    private fun creditValid(request: NewSaleRequest, totalMinor: Long): Boolean =
        request.creditMinor in 0..totalMinor && (request.creditMinor == 0L || !request.customerName.isNullOrBlank())

    /**
     * The business day a sale at [atMillis] belongs to: its calendar day, or the next open day
     * when the owner has already closed that day (after closing, the new day starts from zero).
     */
    private suspend fun businessDayFor(atMillis: Long): Long {
        var day = DateTimeUtils.toLocalDate(atMillis).toEpochDay()
        repeat(MAX_CLOSED_SKIP) {
            if (cashDao.getSession(businessId, day)?.status != CashSessionStatus.CLOSED) return day
            day++
        }
        return day
    }

    private suspend fun insertItemsAndVisit(saleId: Long, request: NewSaleRequest, totals: SaleTotals, businessDate: Long, atMillis: Long) {
        val lineTotalsByKey = totals.lines.associateBy { it.key }
        saleDao.insertItems(
            request.lines.map { line ->
                val lt = lineTotalsByKey.getValue(line.key)
                SaleItemEntity(
                    saleId = saleId,
                    serviceId = line.serviceId,
                    staffId = line.staffId,
                    serviceName = line.serviceName,
                    staffName = line.staffName,
                    unitPriceMinor = line.unitPriceMinor,
                    quantity = line.quantity,
                    lineDiscountMinor = lt.lineDiscountMinor,
                    allocatedDiscountMinor = lt.allocatedSaleDiscountMinor,
                    netAmountMinor = lt.netMinor,
                    commissionBps = line.commissionBps,
                    commissionMinor = lt.commissionMinor,
                )
            },
        )
        saleDao.insertVisit(
            VisitEntity(
                businessId = businessId,
                saleId = saleId,
                customerId = request.customerId,
                visitDate = businessDate,
                visitAt = atMillis,
                servicesSummary = request.lines.joinToString(", ") {
                    if (it.quantity > 1) "${it.serviceName} x${it.quantity}" else it.serviceName
                },
                staffSummary = request.lines.mapNotNull { it.staffName }.distinct().joinToString(", ").ifEmpty { null },
                totalMinor = totals.totalMinor,
            ),
        )
    }

    private suspend fun insertCash(type: CashTxType, amountMinor: Long, day: Long, saleId: Long, receiptNumber: String, atMillis: Long) {
        cashDao.insertTransaction(
            CashTransactionEntity(
                businessId = businessId,
                type = type,
                amountMinor = CashCalculator.signedAmount(type, amountMinor),
                txDate = day,
                referenceType = CashTransactionEntity.REF_SALE,
                referenceId = saleId,
                note = receiptNumber,
                createdAt = atMillis,
            ),
        )
    }

    /** Keeps the customer's pending bill in step with the receipt's credit part. */
    private suspend fun syncDue(sale: SaleEntity, atMillis: Long) {
        val due = dueDao.getBySale(sale.id)
        val credit = if (sale.status == SaleStatus.COMPLETED) sale.creditMinor else 0L
        when {
            due == null && credit > 0 -> dueDao.insert(
                DueEntity(
                    businessId = businessId,
                    customerId = sale.customerId,
                    customerName = sale.customerName.orEmpty().ifBlank { sale.receiptNumber },
                    customerPhone = sale.customerPhone,
                    amountMinor = credit,
                    paidMinor = 0,
                    note = sale.receiptNumber,
                    createdAt = atMillis,
                    settledAt = null,
                    lastReminderAt = null,
                    saleId = sale.id,
                ),
            )
            due == null -> Unit
            credit == 0L && due.paidMinor == 0L -> dueDao.delete(due.id)
            else -> {
                // Never ask for less than what the customer already paid against this bill.
                val amount = maxOf(credit, due.paidMinor)
                dueDao.update(
                    due.copy(
                        customerId = sale.customerId,
                        customerName = sale.customerName ?: due.customerName,
                        customerPhone = sale.customerPhone,
                        amountMinor = amount,
                        settledAt = if (due.paidMinor >= amount) due.settledAt ?: atMillis else null,
                    ),
                )
            }
        }
    }

    /** Next receipt sequence; self-heals if the counter is ever behind existing receipts. */
    private suspend fun nextReceiptSequence(): Long {
        val counter = businessDao.getSequence(businessId)?.lastNumber ?: 0L
        val highestUsed = saleDao.maxReceiptSequence(businessId) ?: 0L
        val next = maxOf(counter, highestUsed) + 1
        businessDao.upsertSequence(ReceiptSequenceEntity(businessId, next))
        return next
    }

    /**
     * Voids (cancels) a sale. The receipt stays in history marked VOIDED and is excluded from
     * all totals. For cash sales a refund movement is added to today's cash drawer.
     */
    suspend fun voidSale(saleId: Long, reason: String?): DataResult<Unit> = safeWrite {
        if (isReadOnly()) return@safeWrite DataResult.Failure(DataError.READ_ONLY)
        db.withTransaction {
            val sale = saleDao.getSale(saleId) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
            val now = System.currentTimeMillis()
            if (saleDao.markVoided(saleId, now, reason?.trim()?.ifEmpty { null }) == 0) {
                return@withTransaction DataResult.Failure(DataError.ALREADY_CLOSED)
            }
            if (sale.paymentMethod == PaymentMethod.CASH && sale.paidMinor > 0) {
                insertCash(CashTxType.SALE_VOID, sale.paidMinor, businessDayFor(now), saleId, sale.receiptNumber, now)
            }
            syncDue(sale.copy(status = SaleStatus.VOIDED), now)
            DataResult.Success(Unit)
        }
    }

    fun observeSale(saleId: Long): Flow<SaleWithItems?> =
        combine(saleDao.observeSale(saleId), saleDao.observeItems(saleId)) { sale, items ->
            sale?.let { SaleWithItems(it, items) }
        }

    suspend fun buildReceipt(saleId: Long): ReceiptData? {
        val sale = saleDao.getSale(saleId) ?: return null
        val items = saleDao.getItems(saleId)
        val styles = db.serviceDao().getByIds(items.mapNotNull { it.serviceId }.distinct()).associateBy { it.id }
        val business = businessDao.get(sale.businessId)?.toProfile() ?: return null
        return ReceiptData(
            saleId = sale.id,
            businessName = business.name,
            businessPhone = business.phone,
            businessAddress = business.address,
            logoPath = business.logoPath,
            headerNote = business.receiptHeaderNote,
            footer = business.receiptFooter,
            currency = business.currency,
            showStaff = business.showStaffOnReceipt,
            showLogo = business.showLogoOnReceipt,
            receiptNumber = sale.receiptNumber,
            createdAtMillis = sale.createdAt,
            customerName = sale.customerName,
            customerPhone = sale.customerPhone,
            items = items.map {
                ReceiptItem(
                    serviceName = it.serviceName,
                    staffName = it.staffName,
                    quantity = it.quantity,
                    unitPriceMinor = it.unitPriceMinor,
                    lineDiscountMinor = it.lineDiscountMinor,
                    lineTotalMinor = it.unitPriceMinor * it.quantity - it.lineDiscountMinor,
                    boldName = styles[it.serviceId]?.boldName == true,
                    boldPrice = styles[it.serviceId]?.boldPrice == true,
                )
            },
            subtotalMinor = sale.subtotalMinor,
            itemDiscountMinor = sale.itemDiscountMinor,
            saleDiscountMinor = sale.saleDiscountMinor,
            totalMinor = sale.totalMinor,
            paymentMethod = sale.paymentMethod,
            amountTenderedMinor = sale.amountTenderedMinor,
            changeMinor = sale.changeMinor,
            isVoided = sale.status == SaleStatus.VOIDED,
            voidReason = sale.voidReason,
            paymentAccountName = sale.paymentAccountName,
            creditMinor = sale.creditMinor,
        )
    }

    fun observeSales(range: DateRange, query: String, limit: Int = 300): Flow<List<SaleEntity>> =
        saleDao.observeSales(businessId, range.startEpochDay, range.endEpochDay, likePattern(query), limit)

    fun observeRecent(limit: Int = 5): Flow<List<SaleEntity>> = saleDao.observeRecent(businessId, limit)

    fun observeReceived(range: DateRange): Flow<List<ReceivedTotalRow>> =
        saleDao.observeReceivedBreakdown(businessId, range.startEpochDay, range.endEpochDay)

    suspend fun saleWithItems(saleId: Long): SaleWithItems? =
        saleDao.getSale(saleId)?.let { SaleWithItems(it, saleDao.getItems(saleId)) }

    fun observeSummary(range: DateRange): Flow<SalesSummaryRow> =
        saleDao.observeSummary(businessId, range.startEpochDay, range.endEpochDay)

    fun observeDailyTotals(range: DateRange): Flow<List<DayTotalRow>> =
        saleDao.observeDailyTotals(businessId, range.startEpochDay, range.endEpochDay)

    private companion object {
        /** At most this many closed days are skipped when finding the open business day. */
        const val MAX_CLOSED_SKIP = 7
    }
}
