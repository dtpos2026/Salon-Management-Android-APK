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
 */
class ReceiptCanvasRenderer(
    private val widthPx: Int,
    textSizePx: Float,
    private val typeface: Typeface = Typeface.MONOSPACE,
) {
    private val normal = paint(textSizePx, bold = false)
    private val bold = paint(textSizePx, bold = true)
    private val largeBold = paint(textSizePx * 1.6f, bold = true)
    private val lineGap = textSizePx * 0.35f

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
                    val mid = y - lineHeight(normal) / 2f
                    val dash = if (line.char == '=') 0f else 6f
                    var x = 0f
                    while (x < widthPx) {
                        val end = if (dash == 0f) widthPx.toFloat() else minOf(x + dash, widthPx.toFloat())
                        canvas.drawRect(x, mid - 1f, end, mid + 1f, normal)
                        x = if (dash == 0f) widthPx.toFloat() else x + dash * 2
                    }
                }
                is PrintLine.Feed -> y += line.lines * lineHeight(normal)
            }
        }
        return y
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

        fun toMonochrome(bitmap: Bitmap, dither: Boolean): MonochromeImage {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return MonochromeImage.fromArgb(pixels, bitmap.width, bitmap.height, dither)
        }
    }
}
