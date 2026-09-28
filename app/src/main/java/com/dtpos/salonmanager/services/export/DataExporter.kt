package com.dtpos.salonmanager.services.export

import android.content.Context
import android.net.Uri
import com.dtpos.salonmanager.core.util.CsvWriter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.data.repository.CustomerRepository
import com.dtpos.salonmanager.data.repository.ExpenseRepository
import com.dtpos.salonmanager.data.repository.StaffRepository
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.domain.model.DateRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports raw data as CSV files (one per table) inside a zip, ready for Excel / Google Sheets.
 * Amounts are written in currency units with two decimals.
 */
class DataExporter(
    private val context: Context,
    private val db: SalonDatabase,
    private val businessId: Long,
    private val customers: CustomerRepository,
    private val staff: StaffRepository,
    private val expenses: ExpenseRepository,
) {

    suspend fun exportCsvZip(uri: Uri, range: DateRange): Boolean = withContext(Dispatchers.IO) {
        try {
            val saleDao = db.saleDao()
            val sales = saleDao.salesBetween(businessId, range.startEpochDay, range.endEpochDay)
            val items = saleDao.itemsBetween(businessId, range.startEpochDay, range.endEpochDay)
            val receiptBySale = sales.associate { it.id to it.receiptNumber }
            val expenseRows = expenses.expensesBetween(range)
            val payments = staff.paymentsBetween(range)
            val staffList = staff.getAll()
            val staffNames = staffList.associate { it.id to it.name }
            val customerList = customers.getAll()

            context.contentResolver.openOutputStream(uri, "wt")?.use { raw ->
                ZipOutputStream(raw).use { zip ->
                    zip.csv("sales.csv") { w ->
                        w.row("receipt", "date", "time", "customer", "phone", "subtotal", "discount", "total", "payment", "status", "void_reason")
                        sales.forEach {
                            w.row(
                                it.receiptNumber, date(it.businessDate), DateTimeUtils.formatTime(it.createdAt), it.customerName,
                                it.customerPhone, units(it.subtotalMinor), units(it.itemDiscountMinor + it.saleDiscountMinor),
                                units(it.totalMinor), it.paymentMethod.name, it.status.name, it.voidReason,
                            )
                        }
                    }
                    zip.csv("sale_items.csv") { w ->
                        w.row("receipt", "service", "staff", "quantity", "unit_price", "line_discount", "net_amount", "commission_percent", "commission")
                        items.forEach {
                            w.row(
                                receiptBySale[it.saleId], it.serviceName, it.staffName, it.quantity, units(it.unitPriceMinor),
                                units(it.lineDiscountMinor + it.allocatedDiscountMinor), units(it.netAmountMinor),
                                Percent.formatBps(it.commissionBps), units(it.commissionMinor),
                            )
                        }
                    }
                    zip.csv("expenses.csv") { w ->
                        w.row("date", "type", "category", "amount", "payment", "from_cash_counter", "note")
                        expenseRows.forEach {
                            w.row(date(it.expenseDate), it.type.name, it.categoryName, units(it.amountMinor), it.paymentMethod.name, it.paidFromCounter, it.note)
                        }
                    }
                    zip.csv("staff_payments.csv") { w ->
                        w.row("date", "staff", "type", "amount", "payment", "note")
                        payments.forEach {
                            w.row(date(it.paymentDate), staffNames[it.staffId], it.type.name, units(it.amountMinor), it.paymentMethod.name, it.note)
                        }
                    }
                    zip.csv("customers.csv") { w ->
                        w.row("name", "phone", "gender", "date_of_birth", "address", "notes", "created")
                        customerList.forEach {
                            w.row(it.name, it.phone, it.gender.name, it.dateOfBirth?.let(::date), it.address, it.notes, DateTimeUtils.formatDate(it.createdAt))
                        }
                    }
                    zip.csv("staff.csv") { w ->
                        w.row("name", "phone", "role", "salary_type", "commission_percent", "fixed_salary", "active")
                        staffList.forEach {
                            w.row(it.name, it.phone, it.role.name, it.salaryType.name, Percent.formatBps(it.commissionBps), units(it.fixedSalaryMinor), it.isActive)
                        }
                    }
                }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun ZipOutputStream.csv(name: String, block: (CsvWriter) -> Unit) {
        putNextEntry(ZipEntry(name))
        val writer = OutputStreamWriter(this, Charsets.UTF_8)
        writer.write(CsvWriter.BOM)
        block(CsvWriter(writer))
        writer.flush()
        closeEntry()
    }

    companion object {
        fun units(minor: Long): String {
            val sign = if (minor < 0) "-" else ""
            val abs = kotlin.math.abs(minor)
            return "$sign${abs / Money.MINOR_PER_UNIT}.${(abs % Money.MINOR_PER_UNIT).toString().padStart(2, '0')}"
        }

        private fun date(epochDay: Long): String = DateTimeUtils.formatIso(LocalDate.ofEpochDay(epochDay))

        fun suggestedFileName(range: DateRange): String =
            "SalonData_${DateTimeUtils.formatIso(range.start)}_to_${DateTimeUtils.formatIso(range.end)}.zip"
    }
}
