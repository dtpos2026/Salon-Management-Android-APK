package com.dtpos.salonmanager.services.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dtpos.salonmanager.services.printer.PrintLine
import com.dtpos.salonmanager.services.printer.ReceiptCanvasRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** One row of a PDF report. */
sealed interface ReportBlock {
    data class Title(val text: String, val subtitle: String?) : ReportBlock
    data class Heading(val text: String) : ReportBlock
    data class KeyValue(val key: String, val value: String, val emphasize: Boolean = false) : ReportBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, val weights: List<Float>) : ReportBlock
    data class Note(val text: String) : ReportBlock
    data object Spacer : ReportBlock
}

/** Generates share-ready PDFs (reports on A4, receipts on narrow receipt-shaped pages). */
class PdfExporter(private val context: Context) {

    private val sharedDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }

    suspend fun receiptPdf(lines: List<PrintLine>, fileName: String, logo: android.graphics.Bitmap?): File? =
        withContext(Dispatchers.Default) {
            try {
                val width = 260
                val margin = 14
                val renderer = ReceiptCanvasRenderer(width - margin * 2, textSizePx = 9.5f, typeface = Typeface.DEFAULT)
                val scaledLogo = logo?.let { ReceiptCanvasRenderer.scaleLogo(it, 90, 90) }
                val height = renderer.measure(lines, scaledLogo) + margin * 2
                val doc = PdfDocument()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.translate(margin.toFloat(), margin.toFloat())
                renderer.draw(page.canvas, lines, scaledLogo)
                doc.finishPage(page)
                write(doc, fileName)
            } catch (e: Exception) {
                null
            }
        }

    suspend fun reportPdf(blocks: List<ReportBlock>, fileName: String): File? = withContext(Dispatchers.Default) {
        try {
            ReportWriter().apply { blocks.forEach(::draw) }.finish(fileName)
        } catch (e: Exception) {
            null
        }
    }

    private fun write(doc: PdfDocument, fileName: String): File {
        val file = File(sharedDir, fileName)
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    /** Minimal A4 layout engine with automatic page breaks. */
    private inner class ReportWriter {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 40f
        private val contentWidth = pageWidth - margin * 2
        private val doc = PdfDocument()
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        private val title = textPaint(20f, bold = true, color = 0xFF14213D.toInt())
        private val subtitle = textPaint(11f, bold = false, color = Color.DKGRAY)
        private val heading = textPaint(13f, bold = true, color = 0xFF14213D.toInt())
        private val body = textPaint(10f, bold = false)
        private val bodyBold = textPaint(10f, bold = true)
        private val small = textPaint(8f, bold = false, color = Color.GRAY)
        private val rule = Paint().apply { color = 0xFFD4A537.toInt(); strokeWidth = 1.5f }
        private val grid = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.7f }
        private val headerFill = Paint().apply { color = 0xFFF1F2F6.toInt() }

        private fun textPaint(size: Float, bold: Boolean, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun ensure(space: Float) {
            if (canvas == null || y + space > pageHeight - margin - 16) newPage()
        }

        private fun newPage() {
            page?.let { finishPage(it) }
            pageNumber++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            page = p
            canvas = p.canvas
            y = margin
        }

        private fun finishPage(p: PdfDocument.Page) {
            p.canvas.drawText("$pageNumber", pageWidth - margin, pageHeight - margin / 2, small.apply { textAlign = Paint.Align.RIGHT })
            small.textAlign = Paint.Align.LEFT
            doc.finishPage(p)
        }

        fun draw(block: ReportBlock) {
            when (block) {
                is ReportBlock.Title -> {
                    ensure(60f)
                    y += 20f
                    canvas!!.drawText(block.text, margin, y, title)
                    block.subtitle?.let {
                        y += 16f
                        canvas!!.drawText(it, margin, y, subtitle)
                    }
                    y += 10f
                    canvas!!.drawLine(margin, y, pageWidth - margin, y, rule)
                    y += 14f
                }
                is ReportBlock.Heading -> {
                    ensure(40f)
                    y += 16f
                    canvas!!.drawText(block.text, margin, y, heading)
                    y += 8f
                }
                is ReportBlock.KeyValue -> {
                    ensure(18f)
                    y += 15f
                    val p = if (block.emphasize) bodyBold else body
                    canvas!!.drawText(ellipsize(block.key, p, contentWidth * 0.6f), margin, y, p)
                    canvas!!.drawText(block.value, pageWidth - margin - p.measureText(block.value), y, p)
                    canvas!!.drawLine(margin, y + 4f, pageWidth - margin, y + 4f, grid)
                }
                is ReportBlock.Table -> drawTable(block)
                is ReportBlock.Note -> {
                    ensure(16f)
                    y += 13f
                    canvas!!.drawText(ellipsize(block.text, small, contentWidth), margin, y, small)
                }
                ReportBlock.Spacer -> y += 10f
            }
        }

        private fun drawTable(table: ReportBlock.Table) {
            val totalWeight = table.weights.sum()
            val widths = table.weights.map { contentWidth * it / totalWeight }
            fun row(values: List<String>, paint: Paint, fill: Boolean) {
                ensure(20f)
                if (fill) canvas!!.drawRect(margin, y + 2f, pageWidth - margin, y + 19f, headerFill)
                y += 15f
                var x = margin
                values.forEachIndexed { i, v ->
                    val w = widths.getOrElse(i) { 60f }
                    val text = ellipsize(v, paint, w - 6f)
                    // First column left aligned, numbers right aligned.
                    val tx = if (i == 0) x + 3f else x + w - 3f - paint.measureText(text)
                    canvas!!.drawText(text, tx, y, paint)
                    x += w
                }
                y += 4f
                canvas!!.drawLine(margin, y, pageWidth - margin, y, grid)
            }
            row(table.headers, bodyBold, fill = true)
            table.rows.forEach { row(it, body, fill = false) }
        }

        private fun ellipsize(text: String, paint: Paint, width: Float): String {
            if (paint.measureText(text) <= width) return text
            var end = text.length
            while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("...") > width) end--
            return text.substring(0, end) + "..."
        }

        fun finish(fileName: String): File {
            if (canvas == null) newPage()
            page?.let { finishPage(it) }
            page = null
            return write(doc, fileName)
        }
    }
}
