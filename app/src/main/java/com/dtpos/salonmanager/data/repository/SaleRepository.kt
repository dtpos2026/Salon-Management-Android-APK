package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.ReceiptSequenceEntity
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.database.entities.SaleItemEntity
import com.dtpos.salonmanager.data.database.entities.VisitEntity
import com.dtpos.salonmanager.data.database.model.DayTotalRow
import com.dtpos.salonmanager.data.database.model.SalesSummaryRow
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.calc.ReceiptNumbering
import com.dtpos.salonmanager.domain.calc.SaleCalculator
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.NewSaleRequest
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.domain.model.SaleStatus
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

    /**
     * Records a sale atomically: receipt number, sale, sale items, customer visit and the cash
     * drawer movement are written in ONE transaction - either everything is saved or nothing.
     *
     * @return the new sale id
     */
    suspend fun completeSale(request: NewSaleRequest, atMillis: Long = System.currentTimeMillis()): DataResult<Long> {
        if (isReadOnly()) return DataResult.Failure(DataError.READ_ONLY)
        val tendered = request.amountTenderedMinor?.takeIf { request.paymentMethod == PaymentMethod.CASH && it > 0 }
        if (SaleCalculator.validate(request.lines, request.discount, tendered).isNotEmpty()) {
            return DataResult.Failure(DataError.INVALID)
        }
        val totals = SaleCalculator.calculate(request.lines, request.discount)
        val businessDate = DateTimeUtils.toLocalDate(atMillis).toEpochDay()

        return safeWrite {
            db.withTransaction {
                val business = businessDao.get(businessId) ?: return@withTransaction DataResult.Failure(DataError.NOT_FOUND)
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
                    ),
                )

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

                if (request.paymentMethod == PaymentMethod.CASH && totals.totalMinor > 0) {
                    cashDao.insertTransaction(
                        CashTransactionEntity(
                            businessId = businessId,
                            type = CashTxType.SALE,
                            amountMinor = CashCalculator.signedAmount(CashTxType.SALE, totals.totalMinor),
                            txDate = businessDate,
                            referenceType = CashTransactionEntity.REF_SALE,
                            referenceId = saleId,
                            note = receiptNumber,
                            createdAt = atMillis,
                        ),
                    )
                }
                DataResult.Success(saleId)
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
            if (sale.paymentMethod == PaymentMethod.CASH && sale.totalMinor > 0) {
                cashDao.insertTransaction(
                    CashTransactionEntity(
                        businessId = businessId,
                        type = CashTxType.SALE_VOID,
                        amountMinor = CashCalculator.signedAmount(CashTxType.SALE_VOID, sale.totalMinor),
                        txDate = DateTimeUtils.toLocalDate(now).toEpochDay(),
                        referenceType = CashTransactionEntity.REF_SALE,
                        referenceId = saleId,
                        note = sale.receiptNumber,
                        createdAt = now,
                    ),
                )
            }
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
        )
    }

    fun observeSales(range: DateRange, query: String, limit: Int = 300): Flow<List<SaleEntity>> =
        saleDao.observeSales(businessId, range.startEpochDay, range.endEpochDay, likePattern(query), limit)

    fun observeRecent(limit: Int = 5): Flow<List<SaleEntity>> = saleDao.observeRecent(businessId, limit)

    fun observeSummary(range: DateRange): Flow<SalesSummaryRow> =
        saleDao.observeSummary(businessId, range.startEpochDay, range.endEpochDay)

    fun observeDailyTotals(range: DateRange): Flow<List<DayTotalRow>> =
        saleDao.observeDailyTotals(businessId, range.startEpochDay, range.endEpochDay)
}
