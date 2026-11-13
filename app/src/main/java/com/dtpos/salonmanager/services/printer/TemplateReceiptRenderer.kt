package com.dtpos.salonmanager.services.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem

/**
 * The bill templates: cash memo, invoice, ticket, compact, big boxes, royal, sales invoice and
 * grand total. Grids with a line around every cell, black title bars and big totals, drawn for
 * a 384 dot wide 58 mm roll (everything scales with [widthPx]). On paper ([forPrinter]) all
 * ink is solid black and regular text is medium weight, so it prints dark and sharp.
 *
 * Salons have no table or order type, so the boxes hold the receipt number, customer, the
 * person who did the work and the date instead.
 */
internal class TemplateReceiptRenderer(
    private val widthPx: Int,
    private val forPrinter: Boolean,
    private val rtl: Boolean,
    accentColor: Int,
    private val style: ReceiptStyle,
) {
    private val s = widthPx / 384f
    private val scale = if (forPrinter) 1.08f else 1f
    /** Royal keeps its text clear of the double frame. */
    private val pad = (if (style == ReceiptStyle.ROYAL) 18f else 12f) * s
    private val left = pad
    private val right = widthPx - pad
    private val content = right - left
    private val serif = style == ReceiptStyle.ROYAL
    private val base = if (style == ReceiptStyle.COMPACT) 12.8f else 14f

    private val ink = Color.BLACK
    private val muted = if (forPrinter) Color.BLACK else Color.rgb(0x55, 0x55, 0x5E)
    private val accent = if (forPrinter) Color.BLACK else accentColor

    private fun font(size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = ink, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * s * scale
            this.color = color
            val family = when {
                serif -> "serif"
                forPrinter && !bold -> "sans-serif-medium"
                else -> "sans-serif"
            }
            val weight = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            typeface = Typeface.create(family, weight)
            letterSpacing = spacing
            // Thin serif strokes fade on thermal paper.
            if (forPrinter && serif) isFakeBoldText = true
        }

    private val name = font(base + 9f, bold = true)
    private val headSub = font(base - 0.5f)
    private val label = font(base)
    private val labelBold = font(base, bold = true)
    private val value = font(base)
    private val valueBold = font(base, bold = true)
    private val caps = font(base - 2.5f, bold = true, color = muted, spacing = 0.08f)
    private val small = font(base - 2.5f, color = muted)
    private val itemName = font(base + 0.5f, bold = true)
    private val itemSub = font(base - 1f, color = muted)
    private val note = font(base - 2f, italic = true, color = muted)
    private val title = font(base + 1f, bold = true, spacing = 0.12f)
    private val titleWhite = font(base + 1f, bold = true, color = Color.WHITE, spacing = 0.12f)
    private val headWhite = font(base, bold = true, color = Color.WHITE)
    private val bigValue = font(base + 8f, bold = true)
    private val totalLabel = font(base + 3f, bold = true)
    private val totalValue = font(base + 5f, bold = true)
    private val words = font(base - 1.5f, italic = true)
    private val thanks = font(base + 0.5f, bold = true)
    private val stampText = font(base + 3f, bold = true, color = accent, spacing = 0.2f)

    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ink
        strokeWidth = (if (forPrinter) 2f else 1.4f) * s
    }
    private val thick = Paint(thin).apply { strokeWidth = (if (forPrinter) 3f else 2.4f) * s }
    private val dash = Paint(thin).apply { pathEffect = DashPathEffect(floatArrayOf(7f * s, 5f * s), 0f) }
    private val dots = Paint(thin).apply {
        strokeWidth = 1.6f * s
        pathEffect = DashPathEffect(floatArrayOf(1.6f * s, 3.2f * s), 0f)
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }

    /** Everything a template needs from the sale, worked out once. */
    private inner class Bill(val r: ReceiptData, val l: ReceiptLabels) {
        val money = CurrencyFormatter(r.currency)
        val customer = r.customerName?.takeIf { it.isNotBlank() } ?: l.walkIn
        val phone = r.customerPhone?.takeIf { it.isNotBlank() }
        val date = DateTimeUtils.formatDate(r.createdAtMillis)
        val time = DateTimeUtils.formatTime(r.createdAtMillis)
        val staffNames = if (r.showStaff) r.items.mapNotNull { it.staffName?.takeIf { n -> n.isNotBlank() } }.distinct() else emptyList()
        val staffPerItem = staffNames.size > 1
        val qtyTotal = r.items.sumOf { it.quantity }
        val amountWords = AmountWords.rupees(r.totalMinor, r.currency)
        val method = r.paymentAccountName ?: l.paymentMethodName

        fun amount(item: ReceiptItem) = money.plain(item.unitPriceMinor * item.quantity)

        /** Lines under an item: who did it (when several people worked) and its discount. */
        fun notes(item: ReceiptItem): List<String> = listOfNotNull(
            item.staffName?.takeIf { staffPerItem && it.isNotBlank() }?.let { "${l.by}: $it" },
            if (item.lineDiscountMinor > 0) "${l.discount} -${money.plain(item.lineDiscountMinor)}" else null,
        )

        fun details(withReceipt: Boolean = true, withCustomer: Boolean = true, withTime: Boolean = true): List<Pair<String, String>> = buildList {
            if (withReceipt) add(l.receiptNo to r.receiptNumber)
            add(l.date to if (withTime) "$date  $time" else date)
            if (withCustomer) add(l.customer to customer)
            phone?.let { add(l.phone to it) }
            if (staffNames.isNotEmpty()) add(l.servedBy to staffNames.joinToString(", "))
        }

        fun totals(): List<Row> = buildList {
            add(Row(l.subtotal, money.plain(r.subtotalMinor)))
            if (r.totalDiscountMinor > 0) add(Row(l.discount, "-" + money.plain(r.totalDiscountMinor)))
        }

        fun payments(): List<Row> = buildList {
            add(Row(l.payment, method))
            r.amountTenderedMinor?.takeIf { it > 0 }?.let {
                add(Row(l.tendered, money.plain(it)))
                add(Row(l.change, money.plain(r.changeMinor)))
            }
            if (r.creditMinor > 0) {
                add(Row(l.paidNow, money.format(r.paidMinor)))
                add(Row(l.balanceDue, money.format(r.creditMinor), strong = true))
            }
        }

        val stamp: String get() = when {
            r.isVoided -> l.voided.uppercase()
            r.creditMinor > 0 -> l.creditStamp.uppercase()
            else -> l.paidStamp.uppercase()
        }
    }

    private class Row(val label: String, val value: String, val strong: Boolean = false)

    /** Lays the bill out; draws when [c] is set. Returns the height. */
    fun draw(c: Canvas?, r: ReceiptData, labels: ReceiptLabels, logo: Bitmap?): Float {
        val b = Bill(r, labels)
        return when (style) {
            ReceiptStyle.CASH_MEMO -> gridBill(c, b, logo, boxedHeader = true, titleText = labels.cashMemo, titleFilled = false, numbered = false, filledHead = true)
            ReceiptStyle.INVOICE -> gridBill(c, b, logo, boxedHeader = false, titleText = labels.invoice, titleFilled = false, numbered = true, filledHead = true)
            ReceiptStyle.SALES_INVOICE -> gridBill(c, b, logo, boxedHeader = false, titleText = labels.salesInvoice, titleFilled = true, numbered = true, filledHead = false)
            ReceiptStyle.TICKET -> ticket(c, b, logo)
            ReceiptStyle.COMPACT -> compact(c, b, logo)
            ReceiptStyle.BOXES -> boxes(c, b, logo)
            ReceiptStyle.ROYAL -> royal(c, b, logo)
            else -> grandTotal(c, b, logo)
        }
    }

    // ---- Templates ------------------------------------------------------------------------

    /** Cash memo, invoice and sales invoice: grids with a line around every cell. */
    private fun gridBill(
        c: Canvas?,
        b: Bill,
        logo: Bitmap?,
        boxedHeader: Boolean,
        titleText: String,
        titleFilled: Boolean,
        numbered: Boolean,
        filledHead: Boolean,
    ): Float {
        var y = 12f * s
        val headerTop = y
        if (boxedHeader) y += 8f * s
        y += header(c, b, logo, y)
        if (boxedHeader) {
            y += 8f * s
            c?.drawRect(left, headerTop, right, y, thick)
        } else {
            y += 10f * s
        }
        // Title: attached box (cash memo), outlined box (invoice) or black bar (sales invoice).
        val titleH = measure(titleText, title, content - 16f * s) + 14f * s
        val inset = if (boxedHeader || titleFilled) 0f else 6f * s
        if (titleFilled) c?.drawRect(left, y, right, y + titleH, solid)
        else c?.drawRect(left + inset, y, right - inset, y + titleH, thick)
        text(c, titleText, if (titleFilled) titleWhite else title, left + 8f * s, content - 16f * s, Layout.Alignment.ALIGN_CENTER, y + 7f * s)
        y += titleH + 10f * s
        y += voidBanner(c, b, y)
        y += keyValueGrid(c, b.details(), y) + 10f * s
        y += itemGrid(c, b, y, numbered, filledHead) + 4f * s
        y += row(c, "${b.l.itemsCount}: ${b.r.items.size}", "${b.l.totalQty}: ${b.qtyTotal}", y, small, small) + 6f * s
        val rows = b.totals() + Row(b.l.total.uppercase(), b.money.format(b.r.totalMinor), strong = true) + b.payments()
        y += totalsGrid(c, rows, y) + 10f * s
        y += amountWords(c, b, y)
        return footer(c, b, y)
    }

    /** Ticket: zigzag edges, dashed receipt / time boxes, qty first, PAID stamp. */
    private fun ticket(c: Canvas?, b: Bill, logo: Bitmap?): Float {
        val zig = 9f * s
        var y = zig + 12f * s
        y += header(c, b, logo, y) + 10f * s
        // Two dashed boxes side by side.
        val gap = 6f * s
        val w1 = (content - gap) * 0.62f
        val w2 = content - gap - w1
        val h1 = labelledBox(null, b.l.receiptNo.uppercase(), b.r.receiptNumber, left, w1, y, dashed = true)
        val h2 = labelledBox(null, b.l.time.uppercase(), b.time, left + w1 + gap, w2, y, dashed = true)
        val boxH = maxOf(h1, h2)
        labelledBox(c, b.l.receiptNo.uppercase(), b.r.receiptNumber, left, w1, y, dashed = true, height = boxH)
        labelledBox(c, b.l.time.uppercase(), b.time, left + w1 + gap, w2, y, dashed = true, height = boxH)
        y += boxH + 10f * s
        y += voidBanner(c, b, y)
        b.details(withReceipt = false, withTime = false).forEach { (k, v) -> y += row(c, k, v, y, value, label) }
        y += 4f * s
        y += dashedLine(c, y) + 6f * s
        y += qtyFirstItems(c, b, y, prefix = { "${it.quantity}" }, rules = false)
        y += dashedLine(c, y) + 6f * s
        b.totals().forEach { y += row(c, it.label, it.value, y, value, label) }
        y += row(c, b.l.total.uppercase(), b.money.format(b.r.totalMinor), y, totalValue, totalLabel) + 2f * s
        b.payments().forEach { y += row(c, it.label, it.value, y, if (it.strong) valueBold else value, if (it.strong) labelBold else label) }
        y += 12f * s
        y += stamp(c, b, y) + 12f * s
        y += dashedLine(c, y) + 8f * s
        y += amountWords(c, b, y)
        val bottom = footer(c, b, y) + zig
        if (c != null) {
            val edge = 3f * s
            c.drawLine(edge, zig, edge, bottom - zig, thin)
            c.drawLine(widthPx - edge, zig, widthPx - edge, bottom - zig, thin)
            zigzag(c, zig, edge, top = true)
            zigzag(c, bottom - zig, edge, top = false)
        }
        return bottom
    }

    /** Compact: details on three lines, one line per service, small print. */
    private fun compact(c: Canvas?, b: Bill, logo: Bitmap?): Float {
        var y = 10f * s
        y += header(c, b, logo, y, compact = true) + 6f * s
        y += dashedLine(c, y) + 6f * s
        y += voidBanner(c, b, y)
        y += line(c, "${b.r.receiptNumber} | ${b.date} ${b.time}", value, y)
        y += line(c, listOfNotNull(b.customer, b.phone).joinToString(" | "), value, y)
        if (b.staffNames.isNotEmpty()) y += line(c, "${b.l.servedBy}: ${b.staffNames.joinToString(", ")}", value, y)
        y += 4f * s
        y += dashedLine(c, y) + 6f * s
        // Service | qty | amount, notes in brackets after the name.
        val qtyW = maxOf(value.measureText("99"), value.measureText(b.l.qty)) + 6f * s
        // Room between the quantity and the amount so "1  1,500" never reads as one number.
        val amountW = b.r.items.maxOfOrNull { valueBold.measureText(b.amount(it)) }?.plus(18f * s) ?: 0f
        val nameW = content - qtyW - amountW - 6f * s
        b.r.items.forEach { item ->
            val extra = b.notes(item).joinToString(", ")
            val nameText = if (extra.isEmpty()) item.serviceName else "${item.serviceName} ($extra)"
            val h = maxOf(
                text(c, nameText, value, mx(left, nameW), nameW, start, y),
                text(c, item.quantity.toString(), value, mx(left + nameW, qtyW), qtyW, end, y),
                text(c, b.amount(item), valueBold, mx(right - amountW, amountW), amountW, end, y),
            )
            y += h + 3f * s
        }
        y += 2f * s
        y += dashedLine(c, y) + 6f * s
        b.totals().forEach { y += row(c, it.label, it.value, y, value, label) }
        y += row(c, b.l.total.uppercase(), b.money.format(b.r.totalMinor), y, totalLabel, totalLabel)
        b.payments().forEach { y += row(c, it.label, it.value, y, if (it.strong) valueBold else value, if (it.strong) labelBold else label) }
        y += 4f * s
        y += dashedLine(c, y) + 6f * s
        y += amountWords(c, b, y)
        return footer(c, b, y)
    }

    /** Big boxes: receipt, customer and staff in big bordered boxes; black total bar. */
    private fun boxes(c: Canvas?, b: Bill, logo: Bitmap?): Float {
        var y = 12f * s
        y += header(c, b, logo, y) + 10f * s
        val boxes = buildList {
            add(b.l.receiptNo.uppercase() to b.r.receiptNumber)
            add(b.l.customer.uppercase() to b.customer)
            if (b.staffNames.isNotEmpty()) add(b.l.servedBy.uppercase() to b.staffNames.joinToString(", "))
        }
        val top = y
        boxes.forEachIndexed { index, (k, v) ->
            val h = labelledBox(null, k, v, left, content, y, dashed = false)
            labelledBox(c, k, v, left, content, y, dashed = false, height = h, outline = false)
            y += h
            if (index < boxes.lastIndex) c?.drawLine(left, y, right, y, thick)
        }
        c?.drawRect(left, top, right, y, thick)
        y += 10f * s
        y += voidBanner(c, b, y)
        y += row(c, b.l.date, b.date, y, valueBold, label)
        y += row(c, b.l.time, b.time, y, valueBold, label)
        b.phone?.let { y += row(c, b.l.phone, it, y, valueBold, label) }
        y += 8f * s
        y += qtyFirstItems(c, b, y, prefix = { "${it.quantity}x" }, rules = true, bold = true)
        y += 4f * s
        b.totals().forEach { y += row(c, it.label, it.value, y, value, label) }
        y += 6f * s
        y += filledTotal(c, b, y) + 8f * s
        b.payments().forEach { y += row(c, it.label, it.value, y, valueBold, if (it.strong) labelBold else label) }
        y += 4f * s
        c?.drawLine(left, y, right, y, thick)
        y += 10f * s
        y += amountWords(c, b, y)
        return footer(c, b, y)
    }

    /** Royal: double frame, serif letters, diamond dividers and dotted leaders. */
    private fun royal(c: Canvas?, b: Bill, logo: Bitmap?): Float {
        var y = 18f * s
        y += header(c, b, logo, y) + 8f * s
        y += diamond(c, y)
        y += text(c, b.l.receiptTitle.uppercase(), font(base + 1f, bold = true, spacing = 0.6f), left, content, Layout.Alignment.ALIGN_CENTER, y) + 4f * s
        y += diamond(c, y)
        y += voidBanner(c, b, y)
        b.details().forEach { (k, v) -> y += row(c, k.uppercase(), v, y, value, caps, leaders = true) }
        y += diamond(c, y)
        b.r.items.forEach { item ->
            y += row(c, "${item.quantity} × ${item.serviceName}", b.amount(item), y, value, itemName, leaders = true)
            b.notes(item).forEach { n -> y += text(c, "- $n", note, mx(left + 12f * s, content - 12f * s), content - 12f * s, start, y) + 2f * s }
        }
        y += diamond(c, y)
        b.totals().forEach { y += row(c, it.label, it.value, y, value, label, leaders = true) }
        c?.drawLine(left, y + 2f * s, right, y + 2f * s, thin)
        y += 8f * s
        y += row(c, b.l.total.uppercase(), b.money.format(b.r.totalMinor), y, totalValue, totalLabel, leaders = true)
        c?.drawLine(left, y + 2f * s, right, y + 2f * s, thin)
        y += 8f * s
        b.payments().forEach { y += row(c, it.label, it.value, y, if (it.strong) valueBold else value, if (it.strong) labelBold else label, leaders = true) }
        y += diamond(c, y)
        y += amountWords(c, b, y)
        val bottom = footer(c, b, y, italic = true) + 8f * s
        c?.let {
            it.drawRect(4f * s, 4f * s, widthPx - 4f * s, bottom - 4f * s, thick)
            it.drawRect(8.5f * s, 8.5f * s, widthPx - 8.5f * s, bottom - 8.5f * s, thin)
        }
        return bottom
    }

    /** Grand total: double rules, a box with customer, date and receipt, very big total. */
    private fun grandTotal(c: Canvas?, b: Bill, logo: Bitmap?): Float {
        var y = 12f * s
        y += header(c, b, logo, y) + 8f * s
        y += doubleRule(c, y) + 8f * s
        // Box: customer, date and time, receipt number.
        val boxTop = y
        y += 10f * s
        y += text(c, b.customer.uppercase(), font(base + 6f, bold = true), left + 8f * s, content - 16f * s, Layout.Alignment.ALIGN_CENTER, y) + 2f * s
        y += text(c, "${b.date}  ${b.time}", value, left + 8f * s, content - 16f * s, Layout.Alignment.ALIGN_CENTER, y) + 2f * s
        y += text(c, b.r.receiptNumber, font(base + 8f, bold = true), left + 8f * s, content - 16f * s, Layout.Alignment.ALIGN_CENTER, y) + 10f * s
        c?.drawRect(left, boxTop, right, y, thick)
        y += 8f * s
        y += voidBanner(c, b, y)
        b.phone?.let { y += row(c, b.l.phone, it, y, value, label) }
        if (b.staffNames.isNotEmpty()) y += row(c, b.l.servedBy, b.staffNames.joinToString(", "), y, value, label)
        y += 4f * s
        y += doubleRule(c, y) + 8f * s
        y += qtyFirstItems(c, b, y, prefix = { "${it.quantity}x" }, rules = false, bold = false)
        c?.drawLine(left, y, right, y, thin)
        y += 8f * s
        b.totals().forEach { y += row(c, it.label, it.value, y, value, label) }
        y += 4f * s
        y += doubleRule(c, y) + 8f * s
        y += row(c, b.l.grandTotal, b.money.format(b.r.totalMinor), y, font(base + 9f, bold = true), font(base + 7f, bold = true)) + 2f * s
        y += doubleRule(c, y) + 8f * s
        b.payments().forEach { y += row(c, it.label, it.value, y, if (it.strong) valueBold else value, if (it.strong) labelBold else label) }
        y += 4f * s
        c?.drawLine(left, y, right, y, thin)
        y += 10f * s
        y += amountWords(c, b, y)
        return footer(c, b, y)
    }

    // ---- Building blocks --------------------------------------------------------------------

    private val start = Layout.Alignment.ALIGN_NORMAL
    private val end = Layout.Alignment.ALIGN_OPPOSITE

    /** Mirrors a box for Urdu (right to left). */
    private fun mx(x: Float, w: Float) = if (rtl) widthPx - x - w else x
    private fun px(x: Float) = if (rtl) widthPx - x else x

    private fun header(c: Canvas?, b: Bill, logo: Bitmap?, top: Float, compact: Boolean = false): Float {
        var y = top
        if (logo != null) {
            val k = minOf(widthPx * (if (compact) 0.3f else 0.4f) / logo.width, (if (compact) 60f else 80f) * s / logo.height)
            val w = logo.width * k
            val h = logo.height * k
            c?.drawBitmap(logo, null, RectF((widthPx - w) / 2f, y, (widthPx + w) / 2f, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 6f * s
        }
        val center = Layout.Alignment.ALIGN_CENTER
        val inner = content - 12f * s
        val x = left + 6f * s
        y += text(c, b.r.businessName, if (compact) font(base + 5f, bold = true) else name, x, inner, center, y)
        b.r.businessAddress?.takeIf { it.isNotBlank() }?.let { y += text(c, it, headSub, x, inner, center, y + 2f * s) + 2f * s }
        b.r.businessPhone?.takeIf { it.isNotBlank() }?.let { y += text(c, "${b.l.phone}: $it", headSub, x, inner, center, y + 1f * s) + 1f * s }
        b.r.headerNote?.takeIf { it.isNotBlank() }?.let { y += text(c, it, font(base - 0.5f, bold = true), x, inner, center, y + 1f * s) + 1f * s }
        return y - top
    }

    private fun voidBanner(c: Canvas?, b: Bill, y: Float): Float {
        if (!b.r.isVoided) return 0f
        var h = text(c, "*** ${b.l.voided.uppercase()} ***", font(base + 4f, bold = true, spacing = 0.15f), left, content, Layout.Alignment.ALIGN_CENTER, y)
        b.r.voidReason?.takeIf { it.isNotBlank() }?.let { h += text(c, it, small, left, content, Layout.Alignment.ALIGN_CENTER, y + h) }
        return h + 8f * s
    }

    /** Label | value grid with a line around every cell. */
    private fun keyValueGrid(c: Canvas?, rows: List<Pair<String, String>>, top: Float): Float {
        val cp = 6f * s
        val labelW = minOf((rows.maxOfOrNull { labelBold.measureText(it.first) } ?: 0f) + 2 * cp, content * 0.42f)
        val valueW = content - labelW
        var y = top
        rows.forEach { (k, v) ->
            val h = maxOf(measure(k, labelBold, labelW - 2 * cp), measure(v, value, valueW - 2 * cp)) + 2 * cp
            text(c, k, labelBold, mx(left + cp, labelW - 2 * cp), labelW - 2 * cp, start, y + cp)
            text(c, v, value, mx(left + labelW + cp, valueW - 2 * cp), valueW - 2 * cp, start, y + cp)
            y += h
            c?.drawLine(left, y, right, y, thin)
        }
        c?.let {
            it.drawRect(left, top, right, y, thin)
            it.drawLine(px(left + labelW), top, px(left + labelW), y, thin)
        }
        return y - top
    }

    /** Service | amount grid: name, "qty × rate" and notes on the left, amount on the right. */
    private fun itemGrid(c: Canvas?, b: Bill, top: Float, numbered: Boolean, filledHead: Boolean): Float {
        val cp = 6f * s
        val headLeft = b.l.item
        val headRight = b.l.amount
        val amountW = maxOf(labelBold.measureText(headRight), b.r.items.maxOfOrNull { valueBold.measureText(b.amount(it)) } ?: 0f) + 2 * cp
        val nameW = content - amountW
        var y = top
        val headH = maxOf(measure(headLeft, labelBold, nameW - 2 * cp), measure(headRight, labelBold, amountW - 2 * cp)) + 2 * cp
        if (filledHead) c?.drawRect(left, y, right, y + headH, solid)
        val headPaint = if (filledHead) headWhite else labelBold
        text(c, headLeft, headPaint, mx(left + cp, nameW - 2 * cp), nameW - 2 * cp, start, y + cp)
        text(c, headRight, headPaint, mx(left + nameW + cp, amountW - 2 * cp), amountW - 2 * cp, end, y + cp)
        y += headH
        if (!filledHead) c?.drawLine(left, y, right, y, thin)
        b.r.items.forEachIndexed { index, item ->
            var ty = y + cp
            val nameText = (if (numbered) "${index + 1}. " else "") + item.serviceName
            ty += text(c, nameText, itemName, mx(left + cp, nameW - 2 * cp), nameW - 2 * cp, start, ty)
            ty += text(c, "${item.quantity} × ${b.money.plain(item.unitPriceMinor)}", itemSub, mx(left + cp, nameW - 2 * cp), nameW - 2 * cp, start, ty + 1f * s) + 1f * s
            b.notes(item).forEach { n ->
                ty += text(c, "- $n", note, mx(left + cp + 8f * s, nameW - 2 * cp - 8f * s), nameW - 2 * cp - 8f * s, start, ty + 1f * s) + 1f * s
            }
            text(c, b.amount(item), valueBold, mx(left + nameW + cp, amountW - 2 * cp), amountW - 2 * cp, end, y + cp)
            y = ty + cp
            c?.drawLine(left, y, right, y, thin)
        }
        c?.let {
            it.drawRect(left, top, right, y, thin)
            it.drawLine(px(left + nameW), top, px(left + nameW), y, thin)
        }
        return y - top
    }

    /** Label | value grid for the totals: values right-aligned, the total row bigger. */
    private fun totalsGrid(c: Canvas?, rows: List<Row>, top: Float): Float {
        val cp = 6f * s
        val valueW = maxOf(rows.maxOf { (if (it.strong) totalValue else valueBold).measureText(it.value) } + 2 * cp, content * 0.36f)
            .coerceAtMost(content * 0.62f)
        val labelW = content - valueW
        var y = top
        rows.forEach { row ->
            val lp = if (row.strong) totalLabel else label
            val vp = if (row.strong) totalValue else valueBold
            val h = maxOf(measure(row.label, lp, labelW - 2 * cp), measure(row.value, vp, valueW - 2 * cp)) + 2 * cp
            text(c, row.label, lp, mx(left + cp, labelW - 2 * cp), labelW - 2 * cp, start, y + cp)
            text(c, row.value, vp, mx(left + labelW + cp, valueW - 2 * cp), valueW - 2 * cp, end, y + cp)
            y += h
            c?.drawLine(left, y, right, y, if (row.strong) thick else thin)
        }
        c?.let {
            it.drawRect(left, top, right, y, thin)
            it.drawLine(px(left + labelW), top, px(left + labelW), y, thin)
        }
        return y - top
    }

    /** Items with the quantity in front ("2x  Hair cut   900"), "@ rate" and notes under the name. */
    private fun qtyFirstItems(
        c: Canvas?,
        b: Bill,
        top: Float,
        prefix: (ReceiptItem) -> String,
        rules: Boolean,
        bold: Boolean = false,
    ): Float {
        val namePaint = if (bold) itemName else value
        val amountPaint = if (bold) valueBold else value
        val qtyW = (b.r.items.maxOfOrNull { namePaint.measureText(prefix(it)) } ?: 0f) + 8f * s
        val amountW = (b.r.items.maxOfOrNull { amountPaint.measureText(b.amount(it)) } ?: 0f) + 4f * s
        val nameW = content - qtyW - amountW - 6f * s
        var y = top
        b.r.items.forEachIndexed { index, item ->
            val rowTop = y
            text(c, prefix(item), namePaint, mx(left, qtyW), qtyW, start, rowTop)
            text(c, b.amount(item), if (item.boldPrice) valueBold else amountPaint, mx(right - amountW, amountW), amountW, end, rowTop)
            var ty = rowTop + text(c, item.serviceName, if (item.boldName) itemName else namePaint, mx(left + qtyW, nameW), nameW, start, rowTop)
            if (item.quantity > 1) ty += text(c, "@ ${b.money.plain(item.unitPriceMinor)}", itemSub, mx(left + qtyW, nameW), nameW, start, ty)
            b.notes(item).forEach { n -> ty += text(c, "- $n", note, mx(left + qtyW + 6f * s, nameW - 6f * s), nameW - 6f * s, start, ty) }
            y = ty + 5f * s
            if (rules && index < b.r.items.lastIndex) {
                c?.drawLine(left, y, right, y, thin)
                y += 6f * s
            }
        }
        return y - top
    }

    /** Small caps title and a big value in a box. Returns the box height. */
    private fun labelledBox(
        c: Canvas?,
        key: String,
        v: String,
        x: Float,
        w: Float,
        y: Float,
        dashed: Boolean,
        height: Float? = null,
        outline: Boolean = true,
    ): Float {
        val inner = w - 12f * s
        val keyH = measure(key, caps, inner)
        val valueH = measure(v, bigValue, inner)
        val h = height ?: (keyH + valueH + 18f * s)
        if (c != null) {
            val bx = mx(x, w)
            if (outline) c.drawRect(bx, y, bx + w, y + h, if (dashed) dash else thick)
            text(c, key, caps, bx + 6f * s, inner, Layout.Alignment.ALIGN_CENTER, y + 7f * s)
            text(c, v, bigValue, bx + 6f * s, inner, Layout.Alignment.ALIGN_CENTER, y + 9f * s + keyH)
        }
        return h
    }

    private fun filledTotal(c: Canvas?, b: Bill, y: Float): Float {
        val totalText = b.money.format(b.r.totalMinor)
        val big = font(base + 8f, bold = true, color = Color.WHITE)
        val lab = font(base + 2f, bold = true, color = Color.WHITE)
        val inner = 12f * s
        val valueW = minOf(big.measureText(totalText) + 2f, content * 0.7f)
        val h = maxOf(measure(totalText, big, valueW), measure(b.l.total.uppercase(), lab, content - valueW - inner * 3)) + 16f * s
        c?.drawRect(left, y, right, y + h, solid)
        val labH = measure(b.l.total.uppercase(), lab, content - valueW - inner * 3)
        text(c, b.l.total.uppercase(), lab, mx(left + inner, content - valueW - inner * 3), content - valueW - inner * 3, start, y + (h - labH) / 2f)
        text(c, totalText, big, mx(right - inner - valueW, valueW), valueW, end, y + 8f * s)
        return h
    }

    private fun stamp(c: Canvas?, b: Bill, y: Float): Float {
        val word = b.stamp
        val w = stampText.measureText(word) + 44f * s
        val h = 36f * s
        c?.let {
            val box = RectF((widthPx - w) / 2f, y, (widthPx + w) / 2f, y + h)
            it.drawRoundRect(box, 8f * s, 8f * s, Paint(thick).apply { color = stampText.color; strokeWidth = 3.2f * s })
            val baseline = y + h / 2f - (stampText.descent() + stampText.ascent()) / 2f
            it.drawText(word, (widthPx - stampText.measureText(word)) / 2f, baseline, stampText)
        }
        return h
    }

    private fun amountWords(c: Canvas?, b: Bill, y: Float): Float {
        val sentence = b.amountWords ?: return 0f
        return text(c, sentence, words, left, content, Layout.Alignment.ALIGN_CENTER, y) + 8f * s
    }

    private fun footer(c: Canvas?, b: Bill, top: Float, italic: Boolean = false): Float {
        var y = top
        val footer = b.r.footer?.takeIf { it.isNotBlank() } ?: b.l.defaultFooter
        y += text(c, footer.trim(), if (italic) font(base + 0.5f, bold = true, italic = true) else thanks, left, content, Layout.Alignment.ALIGN_CENTER, y)
        if (b.l.poweredBy.isNotBlank()) y += 6f * s + text(c, b.l.poweredBy, small, left, content, Layout.Alignment.ALIGN_CENTER, y + 6f * s)
        return y + 18f * s
    }

    /** Label on the start side, value on the end side; optional dotted leader between them. */
    private fun row(c: Canvas?, k: String, v: String, y: Float, vp: TextPaint, lp: TextPaint, leaders: Boolean = false): Float {
        val gap = 8f * s
        val valueW = minOf(vp.measureText(v) + 1f, content * 0.62f)
        val labelW = content - valueW - gap
        val keyLayout = layout(k, lp, labelW, start)
        val h1 = keyLayout.height.toFloat()
        val h2 = text(c, v, vp, mx(right - valueW, valueW), valueW, end, y)
        if (c != null) {
            c.save()
            c.translate(mx(left, labelW), y)
            keyLayout.draw(c)
            c.restore()
            if (leaders && keyLayout.lineCount == 1) {
                val baseline = y + keyLayout.getLineBaseline(0)
                val from = left + keyLayout.getLineWidth(0) + 4f * s
                val to = right - vp.measureText(v) - 4f * s
                if (to - from > 6f * s) c.drawLine(px(from), baseline, px(to), baseline, dots)
            }
        }
        return maxOf(h1, h2) + 3f * s
    }

    private fun line(c: Canvas?, v: String, p: TextPaint, y: Float): Float = text(c, v, p, left, content, start, y) + 2f * s

    private fun dashedLine(c: Canvas?, y: Float): Float {
        c?.drawLine(left, y, right, y, dash)
        return 2f * s
    }

    private fun doubleRule(c: Canvas?, y: Float): Float {
        c?.let {
            it.drawLine(left, y, right, y, thick)
            it.drawLine(left, y + 4.5f * s, right, y + 4.5f * s, thin)
        }
        return 6f * s
    }

    /** ——— ◆ ——— divider. */
    private fun diamond(c: Canvas?, y: Float): Float {
        val mid = y + 9f * s
        val cx = widthPx / 2f
        val r = 6f * s
        c?.let {
            it.drawLine(left, mid, cx - r - 8f * s, mid, thin)
            it.drawLine(cx + r + 8f * s, mid, right, mid, thin)
            val path = Path().apply {
                moveTo(cx, mid - r); lineTo(cx + r, mid); lineTo(cx, mid + r); lineTo(cx - r, mid); close()
            }
            it.drawPath(path, solid)
        }
        return 22f * s
    }

    /** Saw-tooth edge like a torn ticket. */
    private fun zigzag(c: Canvas, y: Float, edge: Float, top: Boolean) {
        val tooth = 16f * s
        val path = Path()
        var x = edge
        while (x < widthPx - edge - 1f) {
            val next = minOf(x + tooth, widthPx - edge)
            path.moveTo(x, y)
            path.lineTo((x + next) / 2f, if (top) y - tooth * 0.55f else y + tooth * 0.55f)
            path.lineTo(next, y)
            path.close()
            x = next
        }
        c.drawPath(path, Paint(solid).apply { color = ink })
    }

    private fun layout(v: String, p: TextPaint, width: Float, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(v, 0, v.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(false)
            .build()

    private fun measure(v: String, p: TextPaint, width: Float): Float = layout(v, p, width, start).height.toFloat()

    private fun text(c: Canvas?, v: String, p: TextPaint, x: Float, width: Float, align: Layout.Alignment, y: Float): Float {
        val l = layout(v, p, width, align)
        if (c != null) {
            c.save()
            c.translate(x, y)
            l.draw(c)
            c.restore()
        }
        return l.height.toFloat()
    }

    companion object {
        val STYLES = setOf(
            ReceiptStyle.CASH_MEMO, ReceiptStyle.INVOICE, ReceiptStyle.TICKET, ReceiptStyle.COMPACT,
            ReceiptStyle.BOXES, ReceiptStyle.ROYAL, ReceiptStyle.SALES_INVOICE, ReceiptStyle.GRAND_TOTAL,
        )
    }
}
