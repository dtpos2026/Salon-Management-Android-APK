package com.dtpos.salonmanager.services.printer

import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.model.ReceiptData

enum class PrintAlign { LEFT, CENTER, RIGHT }

/** Device-independent receipt content. Rendered to ESC/POS text, a bitmap, a PDF or the screen. */
sealed interface PrintLine {
    data class Text(
        val text: String,
        val align: PrintAlign = PrintAlign.LEFT,
        val bold: Boolean = false,
        val large: Boolean = false,
    ) : PrintLine

    /** Left text with a right-aligned value on the same row (wraps the left part if needed). */
    data class Columns(val left: String, val right: String, val bold: Boolean = false, val large: Boolean = false) : PrintLine

    data class Separator(val char: Char = '-') : PrintLine

    data class Feed(val lines: Int = 1) : PrintLine

    /**
     * Item table (e.g. SERVICE | QTY | RATE | AMOUNT): the first column takes the free width and
     * wraps, the others are right-aligned and as wide as their longest value. [boxed] draws it as
     * a grid of boxes on pictures; the printer's own font uses dashed lines instead.
     */
    data class Table(val header: List<String>, val rows: List<Row>, val boxed: Boolean = false) : PrintLine {
        /** One row; [note] is a full-width line under it (e.g. who did the service). */
        data class Row(val cells: List<String>, val bold: Boolean = false, val note: String? = null)
    }
}

/** Translated labels so the pure layout code never touches Android resources. */
data class ReceiptLabels(
    val receiptNo: String,
    val date: String,
    val time: String,
    val customer: String,
    val walkIn: String,
    val phone: String,
    val item: String,
    val amount: String,
    val by: String,
    val discount: String,
    val subtotal: String,
    val total: String,
    val payment: String,
    val tendered: String,
    val change: String,
    val voided: String,
    val paymentMethodName: String,
    val defaultFooter: String,
    /** Extra labels of the styled (image) receipt. */
    val receiptTitle: String = "RECEIPT",
    val servedBy: String = "Served by",
    val paidStamp: String = "PAID",
    /** e.g. "Powered by DT Salon Management"; blank hides the line. */
    val poweredBy: String = "",
    /** Credit (udhaar) sale: paid now, the balance still owed and the stamp. */
    val paidNow: String = "Paid",
    val balanceDue: String = "Balance due",
    val creditStamp: String = "CREDIT",
    /** Column titles of the item table. */
    val qty: String = "Qty",
    val rate: String = "Rate",
    /** Titles and summary words of the bill templates. */
    val itemsCount: String = "Items",
    val totalQty: String = "Total qty",
    val cashMemo: String = "CASH MEMO / RECEIPT",
    val invoice: String = "INVOICE",
    val salesInvoice: String = "SALES INVOICE",
    val grandTotal: String = "GRAND TOTAL",
)

/** How the items are laid out: a table with quantity and rate, or the simple list. */
enum class ItemLayout { TABLE, LIST }

object ReceiptLayout {

    /**
     * The staff member who did every service, when it is one person: printed once at the top
     * instead of under each item (shorter receipt). Null when several people worked on it.
     */
    fun singleStaff(receipt: ReceiptData): String? {
        if (!receipt.showStaff) return null
        val names = receipt.items.mapNotNull { it.staffName?.takeIf { n -> n.isNotBlank() } }.distinct()
        return names.singleOrNull()
    }

    fun build(
        receipt: ReceiptData,
        labels: ReceiptLabels,
        includeHeader: Boolean = true,
        items: ItemLayout = ItemLayout.LIST,
        boxed: Boolean = false,
    ): List<PrintLine> {
        val money = CurrencyFormatter(receipt.currency)
        val lines = mutableListOf<PrintLine>()
        val oneStaff = singleStaff(receipt)
        val staffPerItem = receipt.showStaff && oneStaff == null

        if (includeHeader) {
            lines += PrintLine.Text(receipt.businessName, PrintAlign.CENTER, bold = true, large = true)
            receipt.businessAddress?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Text(it, PrintAlign.CENTER) }
            receipt.businessPhone?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Text("${labels.phone}: $it", PrintAlign.CENTER) }
            receipt.headerNote?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Text(it, PrintAlign.CENTER) }
            lines += PrintLine.Separator('=')
        }

        if (receipt.isVoided) {
            lines += PrintLine.Text("*** ${labels.voided} ***", PrintAlign.CENTER, bold = true)
            receipt.voidReason?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Text(it, PrintAlign.CENTER) }
        }

        lines += PrintLine.Columns(labels.receiptNo, receipt.receiptNumber, bold = true)
        lines += PrintLine.Columns(labels.date, DateTimeUtils.formatDate(receipt.createdAtMillis))
        lines += PrintLine.Columns(labels.time, DateTimeUtils.formatTime(receipt.createdAtMillis))
        lines += PrintLine.Columns(labels.customer, receipt.customerName?.takeIf { it.isNotBlank() } ?: labels.walkIn)
        receipt.customerPhone?.takeIf { it.isNotBlank() }?.let { lines += PrintLine.Columns(labels.phone, it) }
        oneStaff?.let { lines += PrintLine.Columns(labels.servedBy, it) }

        if (items == ItemLayout.TABLE) {
            val rows = mutableListOf<PrintLine.Table.Row>()
            receipt.items.forEach { item ->
                val staff = item.staffName?.takeIf { staffPerItem && it.isNotBlank() }?.let { "  ${labels.by}: $it" }
                rows += PrintLine.Table.Row(
                    listOf(item.serviceName, item.quantity.toString(), money.plain(item.unitPriceMinor), money.plain(item.unitPriceMinor * item.quantity)),
                    bold = item.boldName || item.boldPrice,
                    note = staff,
                )
                if (item.lineDiscountMinor > 0) {
                    rows += PrintLine.Table.Row(listOf("  ${labels.discount}", "", "", "-" + money.plain(item.lineDiscountMinor)))
                }
            }
            if (!boxed) lines += PrintLine.Separator()
            lines += PrintLine.Table(listOf(labels.item, labels.qty, labels.rate, labels.amount), rows, boxed)
        } else {
            lines += PrintLine.Separator()
            lines += PrintLine.Columns(labels.item, labels.amount, bold = true)
            lines += PrintLine.Separator()

            receipt.items.forEach { item ->
                val name = if (item.quantity > 1) "${item.serviceName} x${item.quantity}" else item.serviceName
                lines += PrintLine.Columns(name, money.plain(item.unitPriceMinor * item.quantity), bold = item.boldName || item.boldPrice)
                if (item.quantity > 1) {
                    lines += PrintLine.Text("  ${item.quantity} x ${money.plain(item.unitPriceMinor)}")
                }
                if (staffPerItem && !item.staffName.isNullOrBlank()) {
                    lines += PrintLine.Text("  ${labels.by}: ${item.staffName}")
                }
                if (item.lineDiscountMinor > 0) {
                    lines += PrintLine.Columns("  ${labels.discount}", "-" + money.plain(item.lineDiscountMinor))
                }
            }
        }

        // A boxed table already ends with a line.
        if (!(items == ItemLayout.TABLE && boxed)) lines += PrintLine.Separator()
        lines += PrintLine.Columns(labels.subtotal, money.plain(receipt.subtotalMinor))
        if (receipt.totalDiscountMinor > 0) {
            lines += PrintLine.Columns(labels.discount, "-" + money.plain(receipt.totalDiscountMinor))
        }
        lines += PrintLine.Columns(labels.total, money.format(receipt.totalMinor), bold = true, large = true)
        lines += PrintLine.Columns(labels.payment, receipt.paymentAccountName ?: labels.paymentMethodName)
        receipt.amountTenderedMinor?.takeIf { it > 0 }?.let {
            lines += PrintLine.Columns(labels.tendered, money.plain(it))
            lines += PrintLine.Columns(labels.change, money.plain(receipt.changeMinor))
        }
        if (receipt.creditMinor > 0) {
            lines += PrintLine.Columns(labels.paidNow, money.format(receipt.paidMinor))
            lines += PrintLine.Columns(labels.balanceDue, money.format(receipt.creditMinor), bold = true)
        }
        lines += PrintLine.Separator('=')
        val footer = receipt.footer?.takeIf { it.isNotBlank() } ?: labels.defaultFooter
        footer.lines().filter { it.isNotBlank() }.forEach { lines += PrintLine.Text(it.trim(), PrintAlign.CENTER, bold = true) }
        if (labels.poweredBy.isNotBlank()) lines += PrintLine.Text(labels.poweredBy, PrintAlign.CENTER)
        return lines
    }
}
