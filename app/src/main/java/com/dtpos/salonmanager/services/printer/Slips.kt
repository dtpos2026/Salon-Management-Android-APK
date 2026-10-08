package com.dtpos.salonmanager.services.printer

import android.content.Context
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.entities.StaffPaymentEntity
import com.dtpos.salonmanager.data.repository.DayCloseReport
import com.dtpos.salonmanager.domain.calc.StaffSettlement
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.StaffPaymentType

/**
 * Small printed slips besides the receipt: the Close Day report and the staff payment slip.
 * Built as [PrintLine]s so they print on 58 / 80 mm (text or image mode) and share as text.
 * [res] gives the labels; pass an English context for text-mode printers (Latin font only).
 */
object Slips {

    fun dayClose(report: DayCloseReport, salon: String, res: Context, money: CurrencyFormatter): List<PrintLine> {
        val lines = mutableListOf<PrintLine>()
        lines += PrintLine.Text(salon.ifBlank { res.getString(R.string.app_name) }, PrintAlign.CENTER, bold = true, large = true)
        lines += PrintLine.Text(res.getString(R.string.close_slip_title), PrintAlign.CENTER, bold = true)
        lines += PrintLine.Text(DateTimeUtils.formatDate(report.day), PrintAlign.CENTER)
        lines += PrintLine.Separator('=')

        lines += PrintLine.Columns(res.getString(R.string.close_total_sales), money.plain(report.sales.totalMinor), bold = true)
        lines += PrintLine.Columns(res.getString(R.string.close_customers), report.sales.saleCount.toString())
        lines += PrintLine.Columns(res.getString(R.string.close_services), report.sales.serviceCount.toString())
        if (report.sales.discountMinor > 0) lines += PrintLine.Columns(res.getString(R.string.close_discounts), money.plain(report.sales.discountMinor))
        lines += PrintLine.Separator()

        lines += PrintLine.Text(res.getString(R.string.close_money_title), bold = true)
        val cash = report.received.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.totalMinor }
        lines += PrintLine.Columns(res.getString(R.string.payment_cash), money.plain(cash))
        report.received.filter { it.paymentMethod != PaymentMethod.CASH }.forEach {
            lines += PrintLine.Columns(it.accountName ?: res.getString(ReceiptPrinter.paymentMethodLabel(it.paymentMethod)), money.plain(it.totalMinor))
        }
        if (report.sales.creditMinor > 0) lines += PrintLine.Columns(res.getString(R.string.close_udhaar_given), money.plain(report.sales.creditMinor))
        report.duesCollected.forEach {
            val place = it.accountName ?: res.getString(ReceiptPrinter.paymentMethodLabel(it.paymentMethod))
            lines += PrintLine.Columns("${res.getString(R.string.close_udhaar_received)} · $place", money.plain(it.totalMinor))
        }
        lines += PrintLine.Separator()

        if (report.staff.isNotEmpty()) {
            lines += PrintLine.Text(res.getString(R.string.close_staff_title), bold = true)
            report.staff.forEach { s ->
                val name = s.name ?: res.getString(R.string.pos_no_staff)
                lines += PrintLine.Columns(if (s.isOwner) "$name (${res.getString(R.string.role_owner)})" else name, money.plain(s.salesMinor))
                val detail = if (s.isOwner) {
                    res.getString(R.string.close_staff_owner_line, s.customers)
                } else {
                    res.getString(R.string.close_staff_line, s.customers, money.plain(s.commissionMinor))
                }
                lines += PrintLine.Text("  $detail")
            }
            lines += PrintLine.Columns(res.getString(R.string.close_commission_total), money.plain(report.commissionMinor), bold = true)
            if (report.ownerWorkMinor > 0) lines += PrintLine.Columns(res.getString(R.string.close_owner_work), money.plain(report.ownerWorkMinor))
            lines += PrintLine.Separator()
        }

        lines += PrintLine.Columns(res.getString(R.string.close_expenses), money.plain(report.businessExpensesMinor))
        lines += PrintLine.Columns(res.getString(R.string.close_staff_paid), money.plain(report.staffPaidMinor))
        lines += PrintLine.Columns(res.getString(R.string.close_profit), money.plain(report.profit.businessProfitMinor), bold = true)
        lines += PrintLine.Separator()

        val c = report.cash
        lines += PrintLine.Text(res.getString(R.string.close_drawer_title), bold = true)
        lines += PrintLine.Columns(res.getString(R.string.cash_opening), money.plain(c.openingMinor))
        lines += PrintLine.Columns("+ " + res.getString(R.string.cash_sales), money.plain(c.cashSalesMinor))
        if (c.cashInMinor > 0) lines += PrintLine.Columns("+ " + res.getString(R.string.close_cash_in), money.plain(c.cashInMinor))
        if (c.voidRefundsMinor > 0) lines += PrintLine.Columns("- " + res.getString(R.string.close_refunds), money.plain(c.voidRefundsMinor))
        if (c.cashExpensesMinor > 0) lines += PrintLine.Columns("- " + res.getString(R.string.close_expenses), money.plain(c.cashExpensesMinor))
        if (c.staffPaymentsMinor > 0) lines += PrintLine.Columns("- " + res.getString(R.string.close_staff_paid), money.plain(c.staffPaymentsMinor))
        if (c.cashOutMinor > 0) lines += PrintLine.Columns("- " + res.getString(R.string.close_cash_out), money.plain(c.cashOutMinor))
        lines += PrintLine.Columns(res.getString(R.string.close_expected_cash), money.format(c.expectedClosingMinor), bold = true)
        report.session?.actualClosingMinor?.let { counted ->
            lines += PrintLine.Columns(res.getString(R.string.close_counted_cash), money.format(counted))
            val diff = report.session.differenceMinor ?: (counted - c.expectedClosingMinor)
            lines += PrintLine.Columns(res.getString(R.string.close_difference), (if (diff > 0) "+" else "") + money.plain(diff), bold = true)
        }
        lines += PrintLine.Separator('=')
        lines += PrintLine.Text(
            res.getString(if (report.isClosed) R.string.close_slip_closed else R.string.close_slip_open),
            PrintAlign.CENTER,
        )
        report.session?.closedAt?.let { lines += PrintLine.Text(DateTimeUtils.formatDateTime(it), PrintAlign.CENTER) }
        lines += PrintLine.Feed(1)
        lines += PrintLine.Text(res.getString(R.string.close_signature), PrintAlign.LEFT)
        return lines
    }

    fun staffPayment(
        payment: StaffPaymentEntity,
        staff: StaffEntity,
        settlement: StaffSettlement?,
        salon: String,
        res: Context,
        money: CurrencyFormatter,
    ): List<PrintLine> {
        val lines = mutableListOf<PrintLine>()
        lines += PrintLine.Text(salon.ifBlank { res.getString(R.string.app_name) }, PrintAlign.CENTER, bold = true, large = true)
        lines += PrintLine.Text(res.getString(R.string.staff_slip_title), PrintAlign.CENTER, bold = true)
        lines += PrintLine.Separator('=')
        lines += PrintLine.Columns(res.getString(R.string.staff_slip_name), staff.name, bold = true)
        lines += PrintLine.Columns(res.getString(R.string.receipt_date), DateTimeUtils.formatDate(java.time.LocalDate.ofEpochDay(payment.paymentDate)))
        lines += PrintLine.Columns(res.getString(R.string.staff_slip_type), res.getString(typeLabel(payment.type)))
        lines += PrintLine.Columns(res.getString(R.string.receipt_payment), res.getString(ReceiptPrinter.paymentMethodLabel(payment.paymentMethod)))
        lines += PrintLine.Separator()
        lines += PrintLine.Columns(res.getString(R.string.staff_slip_amount), money.format(payment.amountMinor), bold = true, large = true)
        payment.note?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Text(it) }
        settlement?.let { st ->
            lines += PrintLine.Separator()
            lines += PrintLine.Text(res.getString(R.string.staff_slip_month), bold = true)
            if (st.fixedSalaryMinor > 0) lines += PrintLine.Columns(res.getString(R.string.staff_slip_salary), money.plain(st.fixedSalaryMinor))
            lines += PrintLine.Columns(res.getString(R.string.staff_slip_commission), money.plain(st.commissionEarnedMinor))
            lines += PrintLine.Columns(res.getString(R.string.staff_slip_paid), money.plain(st.totalPaidMinor))
            lines += PrintLine.Columns(res.getString(R.string.staff_slip_balance), money.plain(st.outstandingMinor), bold = true)
        }
        lines += PrintLine.Separator('=')
        lines += PrintLine.Feed(1)
        lines += PrintLine.Text(res.getString(R.string.staff_slip_sign))
        return lines
    }

    /**
     * Receipt for an udhaar payment: customer, when and how it was paid, the amount received,
     * the bill, paid so far and what is still due (or FULLY PAID). [paidSoFarMinor] includes
     * this payment.
     */
    fun duePayment(
        due: com.dtpos.salonmanager.data.database.entities.DueEntity,
        payment: com.dtpos.salonmanager.data.database.entities.DuePaymentEntity,
        paidSoFarMinor: Long,
        salon: String,
        res: Context,
        money: CurrencyFormatter,
    ): List<PrintLine> {
        val lines = mutableListOf<PrintLine>()
        lines += PrintLine.Text(salon.ifBlank { res.getString(R.string.app_name) }, PrintAlign.CENTER, bold = true, large = true)
        lines += PrintLine.Text(res.getString(R.string.due_slip_title), PrintAlign.CENTER, bold = true)
        lines += PrintLine.Separator('=')
        lines += PrintLine.Columns(res.getString(R.string.receipt_customer), due.customerName, bold = true)
        due.customerPhone?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Columns(res.getString(R.string.receipt_phone), it) }
        lines += PrintLine.Columns(res.getString(R.string.receipt_date), DateTimeUtils.formatDateTime(payment.createdAt))
        due.note?.takeIf { it.isNotBlank() }?.let {
            lines += PrintLine.Columns(res.getString(if (due.saleId != null) R.string.share_receipt_label else R.string.share_note), it)
        }
        lines += PrintLine.Columns(
            res.getString(R.string.receipt_payment),
            payment.paymentAccountName ?: res.getString(ReceiptPrinter.paymentMethodLabel(payment.paymentMethod)),
        )
        lines += PrintLine.Separator()
        lines += PrintLine.Columns(res.getString(R.string.due_paid_received), money.format(payment.amountMinor), bold = true, large = true)
        lines += PrintLine.Separator()
        lines += PrintLine.Columns(res.getString(R.string.share_bill), money.plain(due.amountMinor))
        lines += PrintLine.Columns(res.getString(R.string.due_paid_total), money.plain(paidSoFarMinor))
        val balance = (due.amountMinor - paidSoFarMinor).coerceAtLeast(0)
        lines += if (balance == 0L) {
            PrintLine.Text(res.getString(R.string.due_slip_full), PrintAlign.CENTER, bold = true, large = true)
        } else {
            PrintLine.Columns(res.getString(R.string.due_paid_balance), money.plain(balance), bold = true)
        }
        lines += PrintLine.Separator('=')
        lines += PrintLine.Text(res.getString(R.string.due_paid_thanks), PrintAlign.CENTER)
        return lines
    }

    /** The same slip as plain text, for WhatsApp / SMS / share. */
    fun asText(lines: List<PrintLine>): String = buildString {
        lines.forEach { line ->
            when (line) {
                is PrintLine.Text -> appendLine(if (line.bold) "*${line.text.trim()}*" else line.text)
                is PrintLine.Columns -> appendLine(if (line.bold) "*${line.left}: ${line.right}*" else "${line.left}: ${line.right}")
                is PrintLine.Separator -> appendLine("----------------")
                is PrintLine.Feed -> appendLine()
            }
        }
    }.trimEnd()

    private fun typeLabel(type: StaffPaymentType): Int = when (type) {
        StaffPaymentType.SALARY -> R.string.staff_pay_salary
        StaffPaymentType.ADVANCE -> R.string.staff_pay_advance
        StaffPaymentType.COMMISSION -> R.string.staff_pay_commission
        StaffPaymentType.BONUS -> R.string.staff_pay_bonus
        StaffPaymentType.OTHER -> R.string.staff_pay_other
    }
}
