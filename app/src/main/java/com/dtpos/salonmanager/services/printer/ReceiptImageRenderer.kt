package com.dtpos.salonmanager.services.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.model.ReceiptData

/**
 * The professional, branded receipt: logo, salon name, coloured "RECEIPT" bar, details, items,
 * a boxed total and a PAID stamp. Used for the on-screen preview, PNG / JPEG export, WhatsApp
 * sharing and (in black and white, at 384 / 576 dots) for thermal printing in image mode.
 *
 * All sizes are designed for a 384 px wide 58 mm receipt and scale with [widthPx].
 * Text is laid out with StaticLayout, so Urdu and other scripts shape correctly; [rtl] mirrors
 * label/value rows for Urdu.
 */
class ReceiptImageRenderer(
    private val widthPx: Int,
    private val accentColor: Int = DEFAULT_ACCENT,
    private val forPrinter: Boolean = false,
    private val rtl: Boolean = false,
) {
    private val s = widthPx / 384f
    private val pad = 16f * s
    private val contentWidth = widthPx - 2 * pad

    private val ink = Color.BLACK
    private val muted = if (forPrinter) Color.BLACK else Color.rgb(0x62, 0x5E, 0x6B)
    private val accent = if (forPrinter) Color.BLACK else accentColor
    private val alert = if (forPrinter) Color.BLACK else Color.rgb(0xC6, 0x28, 0x28)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * s
        this.color = color
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = spacing
    }

    private val title = paint(21f, bold = true, spacing = 0.08f)
    private val sub = paint(12.5f, color = muted)
    private val barText = paint(15f, bold = true, color = Color.WHITE, spacing = 0.3f)
    private val label = paint(14f)
    private val value = paint(14f, bold = false)
    private val valueBold = paint(14f, bold = true)
    private val header = paint(12f, bold = true, spacing = 0.12f)
    private val itemName = paint(14.5f, bold = true)
    private val itemSub = paint(13f, color = muted)
    private val totalLabel = paint(19f, bold = true)
    private val totalValue = paint(20f, bold = true)
    private val stamp = paint(14f, bold = true, color = accent, spacing = 0.18f)
    private val thanks = paint(15.5f, bold = true)
    private val small = paint(11.5f, color = muted)
    private val voidPaint = paint(17f, bold = true, color = alert, spacing = 0.2f)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 2f * s; style = Paint.Style.STROKE }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (forPrinter) Color.BLACK else Color.rgb(0x9A, 0x96, 0xA3)
        strokeWidth = 1.4f * s
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f * s, 4f * s), 0f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun render(receipt: ReceiptData, labels: ReceiptLabels, logo: Bitmap?): Bitmap {
        val height = draw(null, receipt, labels, logo).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        draw(canvas, receipt, labels, logo)
        return bitmap
    }

    /** Lays the receipt out; draws when [canvas] is not null. Returns the total height. */
    private fun draw(canvas: Canvas?, r: ReceiptData, labels: ReceiptLabels, logo: Bitmap?): Float {
        val money = CurrencyFormatter(r.currency)
        var y = 20f * s

        if (logo != null) {
            val maxW = widthPx * 0.42f
            val maxH = 92f * s
            val scale = minOf(maxW / logo.width, maxH / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            canvas?.drawBitmap(logo, null, RectF((widthPx - w) / 2f, y, (widthPx + w) / 2f, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 12f * s
        }

        y += text(canvas, r.businessName.uppercase(), title, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y)
        r.headerNote?.takeIf { it.isNotBlank() }?.let { y += 3f * s + text(canvas, it, sub, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y + 3f * s) }
        r.businessAddress?.takeIf { it.isNotBlank() }?.let { y += 2f * s + text(canvas, it, sub, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y + 2f * s) }
        r.businessPhone?.takeIf { it.isNotBlank() }?.let { y += 2f * s + text(canvas, it, sub, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y + 2f * s) }

        // Coloured title bar.
        y += 12f * s
        val barHeight = 32f * s
        canvas?.let {
            fill.color = accent
            it.drawRoundRect(RectF(pad, y, widthPx - pad, y + barHeight), 7f * s, 7f * s, fill)
        }
        val barTextHeight = measure(labels.receiptTitle.uppercase(), barText, contentWidth)
        text(canvas, labels.receiptTitle.uppercase(), barText, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y + (barHeight - barTextHeight) / 2f)
        y += barHeight + 10f * s

        if (r.isVoided) {
            y += text(canvas, "*** ${labels.voided} ***", voidPaint, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y) + 2f * s
            r.voidReason?.takeIf { it.isNotBlank() }?.let { y += text(canvas, it, sub, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y) }
            y += 6f * s
        }

        y += row(canvas, labels.receiptNo, r.receiptNumber, y, valueBold)
        y += row(canvas, labels.date, DateTimeUtils.formatDate(r.createdAtMillis), y)
        y += row(canvas, labels.time, DateTimeUtils.formatTime(r.createdAtMillis), y)
        y += row(canvas, labels.customer, r.customerName?.takeIf { it.isNotBlank() } ?: labels.walkIn, y, valueBold)
        r.customerPhone?.takeIf { it.isNotBlank() }?.let { y += row(canvas, labels.phone, it, y) }
        if (r.showStaff) {
            val staff = r.items.mapNotNull { it.staffName?.takeIf { n -> n.isNotBlank() } }.distinct()
            if (staff.isNotEmpty()) y += row(canvas, labels.servedBy, staff.joinToString(", "), y)
        }
        y += row(canvas, labels.payment, labels.paymentMethodName, y)

        y += 6f * s
        y += dashed(canvas, y)
        y += 8f * s
        y += row(canvas, labels.item.uppercase(), labels.amount.uppercase(), y, header, header)
        canvas?.drawLine(pad, y + 2f * s, widthPx - pad, y + 2f * s, linePaint)
        y += 10f * s

        r.items.forEach { item ->
            y += text(canvas, item.serviceName, itemName, pad, contentWidth, start(), y) + 2f * s
            y += row(canvas, "${item.quantity} × ${money.format(item.unitPriceMinor)}", money.plain(item.unitPriceMinor * item.quantity), y, valueBold, itemSub)
            if (item.lineDiscountMinor > 0) y += row(canvas, labels.discount, "-" + money.plain(item.lineDiscountMinor), y, value, itemSub)
            y += 8f * s
        }

        y += dashed(canvas, y)
        y += 8f * s
        y += row(canvas, labels.subtotal, money.plain(r.subtotalMinor), y)
        if (r.totalDiscountMinor > 0) y += row(canvas, labels.discount, "-" + money.plain(r.totalDiscountMinor), y)

        // Boxed grand total.
        y += 8f * s
        val boxHeight = 50f * s
        canvas?.let {
            val box = Paint(linePaint).apply { strokeWidth = 2.6f * s }
            it.drawRoundRect(RectF(pad + 1.3f * s, y, widthPx - pad - 1.3f * s, y + boxHeight), 8f * s, 8f * s, box)
        }
        val totalText = money.format(r.totalMinor)
        val inner = 12f * s
        val totalWidth = totalValue.measureText(totalText)
        val labelHeight = measure(labels.total, totalLabel, contentWidth - totalWidth - inner * 3)
        if (rtl) {
            text(canvas, labels.total, totalLabel, pad + inner * 2 + totalWidth, contentWidth - totalWidth - inner * 3, Layout.Alignment.ALIGN_NORMAL, y + (boxHeight - labelHeight) / 2f)
            single(canvas, totalText, totalValue, pad + inner, y + boxHeight / 2f)
        } else {
            text(canvas, labels.total, totalLabel, pad + inner, contentWidth - totalWidth - inner * 3, Layout.Alignment.ALIGN_NORMAL, y + (boxHeight - labelHeight) / 2f)
            single(canvas, totalText, totalValue, widthPx - pad - inner - totalWidth, y + boxHeight / 2f)
        }
        y += boxHeight + 10f * s

        r.amountTenderedMinor?.takeIf { it > 0 }?.let {
            y += row(canvas, labels.tendered, money.plain(it), y)
            y += row(canvas, labels.change, money.plain(r.changeMinor), y)
        }

        // PAID / VOID stamp.
        y += 10f * s
        val stampText = if (r.isVoided) labels.voided.uppercase() else labels.paidStamp.uppercase()
        val stampPaint = if (r.isVoided) TextPaint(stamp).apply { color = alert } else stamp
        val stampWidth = stampPaint.measureText(stampText) + 34f * s
        val stampHeight = 30f * s
        canvas?.let {
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * s
                color = stampPaint.color
            }
            it.drawRoundRect(RectF((widthPx - stampWidth) / 2f, y, (widthPx + stampWidth) / 2f, y + stampHeight), stampHeight / 2f, stampHeight / 2f, outline)
        }
        single(canvas, stampText, stampPaint, (widthPx - stampPaint.measureText(stampText)) / 2f, y + stampHeight / 2f)
        y += stampHeight + 14f * s

        y += dashed(canvas, y)
        y += 10f * s
        val footer = r.footer?.takeIf { it.isNotBlank() } ?: labels.defaultFooter
        y += text(canvas, footer.trim(), thanks, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y)
        val contact = listOfNotNull(r.businessName.takeIf { it.isNotBlank() }, r.businessPhone?.takeIf { it.isNotBlank() }).joinToString("  |  ")
        if (contact.isNotBlank()) y += 4f * s + text(canvas, contact, sub, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y + 4f * s)
        if (labels.poweredBy.isNotBlank()) y += 8f * s + text(canvas, labels.poweredBy, small, pad, contentWidth, Layout.Alignment.ALIGN_CENTER, y + 8f * s)
        return y + 22f * s
    }

    private fun start() = Layout.Alignment.ALIGN_NORMAL

    /** Label on the start side, value on the end side (mirrored for Urdu). */
    private fun row(canvas: Canvas?, labelText: String, valueText: String, y: Float, valuePaint: TextPaint = value, labelPaint: TextPaint = label): Float {
        val gap = 10f * s
        val valueWidth = minOf(valuePaint.measureText(valueText), contentWidth * 0.62f)
        val labelWidth = contentWidth - valueWidth - gap
        val labelX = if (rtl) pad + valueWidth + gap else pad
        val valueX = if (rtl) pad else pad + labelWidth + gap
        val h1 = text(canvas, labelText, labelPaint, labelX, labelWidth, Layout.Alignment.ALIGN_NORMAL, y)
        val h2 = text(canvas, valueText, valuePaint, valueX, valueWidth, Layout.Alignment.ALIGN_OPPOSITE, y)
        return maxOf(h1, h2) + 5f * s
    }

    private fun dashed(canvas: Canvas?, y: Float): Float {
        canvas?.drawLine(pad, y, widthPx - pad, y, dashPaint)
        return 2f * s
    }

    /** Draws one line of text vertically centred on [centerY]. */
    private fun single(canvas: Canvas?, value: String, paint: TextPaint, x: Float, centerY: Float) {
        val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
        canvas?.drawText(value, x, baseline, paint)
    }

    private fun layout(value: String, paint: TextPaint, width: Float, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(false)
            .build()

    private fun measure(value: String, paint: TextPaint, width: Float): Float = layout(value, paint, width, Layout.Alignment.ALIGN_NORMAL).height.toFloat()

    private fun text(canvas: Canvas?, value: String, paint: TextPaint, x: Float, width: Float, align: Layout.Alignment, y: Float): Float {
        val l = layout(value, paint, width, align)
        if (canvas != null) {
            canvas.save()
            canvas.translate(x, y)
            l.draw(canvas)
            canvas.restore()
        }
        return l.height.toFloat()
    }

    companion object {
        const val DEFAULT_ACCENT = 0xFF3E0E72.toInt()

        /** Width used for shared/saved images: sharp on phones, still small files. */
        const val SHARE_WIDTH_PX = 720
    }
}
