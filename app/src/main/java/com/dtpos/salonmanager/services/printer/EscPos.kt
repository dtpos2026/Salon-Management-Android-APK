package com.dtpos.salonmanager.services.printer

import java.io.ByteArrayOutputStream
import java.text.Normalizer

/**
 * Brand-neutral ESC/POS command builder. Only uses the widely supported command subset
 * (ESC @, ESC a, ESC E, GS !, ESC d, GS V, GS v 0) so generic 58/80 mm Bluetooth printers
 * from different manufacturers behave the same way.
 */
class EscPosBuilder {
    private val out = ByteArrayOutputStream()

    fun initialize() = apply { bytes(ESC, '@'.code) }

    fun align(align: PrintAlign) = apply {
        bytes(ESC, 'a'.code, when (align) { PrintAlign.LEFT -> 0; PrintAlign.CENTER -> 1; PrintAlign.RIGHT -> 2 })
    }

    fun bold(on: Boolean) = apply { bytes(ESC, 'E'.code, if (on) 1 else 0) }

    /** Double width + double height when [large]. */
    fun large(on: Boolean) = apply { bytes(GS, '!'.code, if (on) 0x11 else 0x00) }

    fun text(value: String) = apply { out.write(EscPos.toPrinterBytes(value)) }

    fun newLine() = apply { out.write(LF) }

    fun feed(lines: Int) = apply { if (lines > 0) bytes(ESC, 'd'.code, lines.coerceIn(1, 255)) }

    /** Partial cut with feed. Printers without a cutter simply ignore it. */
    fun cut() = apply { bytes(GS, 'V'.code, 66, 0) }

    /**
     * Prints a 1-bit image with GS v 0. Sent in bands of [bandHeight] rows because many
     * low-cost printers have small receive buffers.
     */
    fun raster(image: MonochromeImage, bandHeight: Int = 128) = apply {
        val bytesPerRow = (image.width + 7) / 8
        var y = 0
        while (y < image.height) {
            val rows = minOf(bandHeight, image.height - y)
            bytes(GS, 'v'.code, '0'.code, 0, bytesPerRow and 0xFF, (bytesPerRow shr 8) and 0xFF, rows and 0xFF, (rows shr 8) and 0xFF)
            for (row in y until y + rows) {
                for (byteIndex in 0 until bytesPerRow) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = byteIndex * 8 + bit
                        if (x < image.width && image.isBlack(x, row)) b = b or (0x80 shr bit)
                    }
                    out.write(b)
                }
            }
            y += rows
        }
    }

    fun raw(data: ByteArray) = apply { out.write(data) }

    fun build(): ByteArray = out.toByteArray()

    private fun bytes(vararg values: Int) = values.forEach { out.write(it) }

    companion object {
        private const val ESC = 0x1B
        private const val GS = 0x1D
        private const val LF = 0x0A
    }
}

object EscPos {

    /**
     * Converts text to bytes every ESC/POS printer understands: accents are stripped and
     * anything outside printable ASCII becomes '?'. Non-Latin scripts (e.g. Urdu) should use
     * the image print mode instead, which renders text with Android fonts.
     */
    fun toPrinterBytes(value: String): ByteArray {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                c.code in 0x20..0x7E -> sb.append(c)
                c == '–' || c == '—' -> sb.append('-')
                c == '‘' || c == '’' -> sb.append('\'')
                c == '“' || c == '”' -> sb.append('"')
                c == '₨' -> sb.append("Rs")
                c == ' ' -> sb.append(' ')
                else -> sb.append('?')
            }
        }
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /** Encodes rendered text lines (text print mode). */
    fun encodeText(
        lines: List<RenderedLine>,
        logo: MonochromeImage?,
        feedLines: Int,
        cut: Boolean,
    ): ByteArray {
        val b = EscPosBuilder().initialize()
        if (logo != null) {
            b.align(PrintAlign.CENTER).raster(logo).newLine()
        }
        for (line in lines) {
            b.align(PrintAlign.LEFT).bold(line.bold).large(line.large)
            // Lines are pre-padded for alignment, so trailing spaces can be dropped safely.
            b.text(line.text.trimEnd()).newLine()
        }
        b.bold(false).large(false).align(PrintAlign.LEFT)
        b.feed(feedLines)
        if (cut) b.cut()
        return b.build()
    }

    /** Encodes a fully rendered receipt image (image print mode, any language). */
    fun encodeImage(image: MonochromeImage, feedLines: Int, cut: Boolean): ByteArray {
        val b = EscPosBuilder().initialize().align(PrintAlign.CENTER).raster(image).align(PrintAlign.LEFT)
        b.feed(feedLines)
        if (cut) b.cut()
        return b.build()
    }
}
