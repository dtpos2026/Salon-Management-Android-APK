package com.dtpos.salonmanager.services.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Draws receipt [PrintLine]s with Android fonts onto any Canvas (thermal bitmap or PDF page).
 * Unlike the printer's built-in font this supports every script, including Urdu.
 * [heavy] (thermal printing): every line in bold and bold lines extra thick, so the picture
 * prints as dark and sharp as the printer's own font.
 */
class ReceiptCanvasRenderer(
    private val widthPx: Int,
    textSizePx: Float,
    private val typeface: Typeface = Typeface.MONOSPACE,
    private val heavy: Boolean = false,
    /** Space between lines as a share of the text size (0.35 is like the printer's own font). */
    lineSpacing: Float = 0.35f,
) {
    private val normal = paint(textSizePx, bold = heavy)
    private val bold = paint(textSizePx, bold = true).apply { isFakeBoldText = heavy }
    private val largeBold = paint(textSizePx * 1.6f, bold = true).apply { isFakeBoldText = heavy }
    private val lineGap = textSizePx * lineSpacing
    private val rule = (textSizePx * 0.1f).coerceAtLeast(1.5f)

    private fun paint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        this.typeface = Typeface.create(this@ReceiptCanvasRenderer.typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun paintFor(bold: Boolean, large: Boolean) = when {
        large -> largeBold
        bold -> this.bold
        else -> normal
    }

    private fun lineHeight(p: Paint) = p.fontSpacing + lineGap

    /** Height needed for [lines] (plus an optional logo drawn above). */
    fun measure(lines: List<PrintLine>, logo: Bitmap? = null): Int {
        var y = logo?.let { it.height + lineGap * 2 } ?: 0f
        lines.forEach { y += heightOf(it) }
        return y.toInt() + 4
    }

    private fun heightOf(line: PrintLine): Float = when (line) {
        is PrintLine.Text -> {
            val p = paintFor(line.bold, line.large)
            wrap(line.text, p, widthPx.toFloat()).size * lineHeight(p)
        }
        is PrintLine.Columns -> {
            val p = paintFor(line.bold, line.large)
            val rightWidth = p.measureText(line.right)
            val leftWidth = (widthPx - rightWidth - p.measureText(" ")).coerceAtLeast(widthPx * 0.3f)
            wrap(line.left, p, leftWidth).size * lineHeight(p)
        }
        is PrintLine.Separator -> lineHeight(normal)
        is PrintLine.Feed -> line.lines * lineHeight(normal)
        is PrintLine.Table -> table(null, line, 0f)
    }

    fun draw(canvas: Canvas, lines: List<PrintLine>, logo: Bitmap? = null, top: Float = 0f): Float {
        var y = top
        if (logo != null) {
            val left = ((widthPx - logo.width) / 2f).coerceAtLeast(0f)
            canvas.drawBitmap(logo, left, y, null)
            y += logo.height + lineGap * 2
        }
        for (line in lines) {
            when (line) {
                is PrintLine.Text -> {
                    val p = paintFor(line.bold, line.large)
                    for (part in wrap(line.text, p, widthPx.toFloat())) {
                        val x = when (line.align) {
                            PrintAlign.LEFT -> 0f
                            PrintAlign.CENTER -> (widthPx - p.measureText(part)) / 2f
                            PrintAlign.RIGHT -> widthPx - p.measureText(part)
                        }
                        y += lineHeight(p)
                        canvas.drawText(part, x.coerceAtLeast(0f), y - lineGap - p.descent(), p)
                    }
                }
                is PrintLine.Columns -> {
                    val p = paintFor(line.bold, line.large)
                    val rightWidth = p.measureText(line.right)
                    val leftWidth = (widthPx - rightWidth - p.measureText(" ")).coerceAtLeast(widthPx * 0.3f)
                    wrap(line.left, p, leftWidth).forEachIndexed { index, part ->
                        y += lineHeight(p)
                        val baseline = y - lineGap - p.descent()
                        canvas.drawText(part, 0f, baseline, p)
                        if (index == 0) canvas.drawText(line.right, widthPx - rightWidth, baseline, p)
                    }
                }
                is PrintLine.Separator -> {
                    y += lineHeight(normal)
                    separator(canvas, y - lineHeight(normal) / 2f, line.char)
                }
                is PrintLine.Feed -> y += line.lines * lineHeight(normal)
                is PrintLine.Table -> y += table(canvas, line, y)
            }
        }
        return y
    }

    private fun separator(canvas: Canvas, mid: Float, char: Char) {
        val dash = if (char == '=') 0f else 6f
        var x = 0f
        while (x < widthPx) {
            val end = if (dash == 0f) widthPx.toFloat() else minOf(x + dash, widthPx.toFloat())
            canvas.drawRect(x, mid - 1f, end, mid + 1f, normal)
            x = if (dash == 0f) widthPx.toFloat() else x + dash * 2
        }
    }

    /**
     * Item table; returns its height and draws it when [canvas] is set. Number columns are
     * right-aligned and as wide as their longest value, the first column takes the rest and
     * wraps. Boxed: a grid with a black header row and a line around every cell. When the
     * numbers leave too little room the name goes on its own line, numbers under it.
     */
    private fun table(canvas: Canvas?, t: PrintLine.Table, top: Float): Float {
        val columns = t.header.size
        val numberWidths = (1 until columns).map { c ->
            maxOf(bold.measureText(t.header.getOrElse(c) { "" }), t.rows.maxOfOrNull { paintFor(it.bold, false).measureText(it.cells.getOrElse(c) { "" }) } ?: 0f)
        }
        val boxedWanted = t.boxed
        val pad = if (boxedWanted) normal.textSize * 0.3f else 0f
        val gap = if (boxedWanted) 0f else normal.measureText("  ")
        val firstWidth = widthPx - numberWidths.sum() - (columns - 1) * gap - columns * 2 * pad
        val stacked = firstWidth < widthPx * 0.3f
        val boxed = boxedWanted && !stacked
        val vpad = if (boxed) normal.textSize * 0.22f else 0f
        // Column borders: edges[c] is where column c starts, edges[columns] the right side.
        val edges = FloatArray(columns + 1)
        edges[1] = firstWidth + 2 * pad
        for (c in 1 until columns) edges[c + 1] = edges[c] + gap + numberWidths[c - 1] + 2 * pad
        edges[columns] = widthPx.toFloat()
        val lines = Paint(normal).apply { style = Paint.Style.STROKE; strokeWidth = rule }
        var y = top

        fun row(cells: List<String>, p: Paint, note: String?, header: Boolean) {
            val rowTop = y
            val nameWidth = if (stacked) widthPx.toFloat() else firstWidth
            val first = cells.firstOrNull() ?: ""
            // Keeps the indent of rows such as an item's discount.
            val indent = p.measureText(first.takeWhile { it == ' ' }).coerceAtMost(nameWidth / 2f)
            val names = wrap(first, p, nameWidth - indent)
            val notes = note?.let { wrap(it.trim(), normal, if (boxed) firstWidth else widthPx - normal.measureText("  ")) } ?: emptyList()
            val numbersOwnLine = stacked && cells.drop(1).any { it.isNotBlank() }
            val height = 2 * vpad + names.size * lineHeight(p) + (if (numbersOwnLine) lineHeight(p) else 0f) +
                (if (boxed) notes.size * lineHeight(normal) else 0f)
            val ink = if (boxed && header) Paint(p).apply { color = Color.WHITE } else p
            if (canvas != null) {
                if (boxed && header) canvas.drawRect(0f, rowTop, widthPx.toFloat(), rowTop + height, Paint(normal).apply { style = Paint.Style.FILL })
                var ty = rowTop + vpad
                names.forEachIndexed { index, part ->
                    ty += lineHeight(p)
                    val baseline = ty - lineGap - p.descent()
                    canvas.drawText(part, edges[0] + pad + indent, baseline, ink)
                    if (index == 0 && !stacked) {
                        for (c in 1 until columns) {
                            val v = cells.getOrElse(c) { "" }
                            canvas.drawText(v, edges[c + 1] - pad - p.measureText(v), baseline, ink)
                        }
                    }
                }
                if (numbersOwnLine) {
                    ty += lineHeight(p)
                    val v = cells.drop(1).filter { it.isNotBlank() }.joinToString("  ")
                    canvas.drawText(v, widthPx - p.measureText(v), ty - lineGap - p.descent(), ink)
                }
                if (boxed) notes.forEach { n ->
                    ty += lineHeight(normal)
                    canvas.drawText(n, edges[0] + pad, ty - lineGap - normal.descent(), normal)
                }
            }
            y += height
            if (!boxed && notes.isNotEmpty()) {
                notes.forEach { n ->
                    y += lineHeight(normal)
                    canvas?.drawText(n, normal.measureText("  "), y - lineGap - normal.descent(), normal)
                }
            }
            if (boxed) canvas?.drawLine(0f, y, widthPx.toFloat(), y, lines)
        }

        row(t.header, bold, null, header = true)
        if (!boxed) {
            y += lineHeight(normal)
            canvas?.let { separator(it, y - lineHeight(normal) / 2f, '-') }
        }
        t.rows.forEach { r -> row(r.cells, paintFor(r.bold, false), r.note, header = false) }
        if (boxed && canvas != null) {
            val half = rule / 2f
            canvas.drawRect(half, top + half, widthPx - half, y - half, lines)
            for (c in 1 until columns) canvas.drawLine(edges[c], top, edges[c], y, lines)
        }
        if (boxed) y += lineGap
        return y - top
    }

    /** Renders to a white bitmap exactly [widthPx] wide. */
    fun renderBitmap(lines: List<PrintLine>, logo: Bitmap?): Bitmap {
        val height = measure(lines, logo).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        draw(canvas, lines, logo)
        return bitmap
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var remaining = paragraph.trim()
            while (remaining.isNotEmpty()) {
                var count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count)
                    if (space > 0) count = space
                }
                result += remaining.substring(0, count).trimEnd()
                remaining = remaining.substring(count).trimStart()
            }
        }
        return result.ifEmpty { listOf("") }
    }

    companion object {
        /** Scales a logo to at most [maxWidth] x [maxHeight] keeping its aspect ratio. */
        fun scaleLogo(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
            val scale = minOf(maxWidth.toFloat() / source.width, maxHeight.toFloat() / source.height, 1f)
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }

        fun toMonochrome(bitmap: Bitmap, dither: Boolean, threshold: Float = 128f): MonochromeImage {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return MonochromeImage.fromArgb(pixels, bitmap.width, bitmap.height, dither, threshold)
        }
    }
}
