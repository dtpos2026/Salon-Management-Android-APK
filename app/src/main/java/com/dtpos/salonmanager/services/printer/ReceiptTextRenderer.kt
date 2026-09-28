package com.dtpos.salonmanager.services.printer

/** A physical printer line: already wrapped and padded to the paper width. */
data class RenderedLine(
    val text: String,
    val align: PrintAlign = PrintAlign.LEFT,
    val bold: Boolean = false,
    val large: Boolean = false,
)

/**
 * Converts [PrintLine]s into fixed-width lines for monospace output.
 * 58 mm printers print 32 characters per line (Font A), 80 mm printers 48.
 * "Large" text is double width, so only half as many characters fit.
 */
object ReceiptTextRenderer {
    const val CHARS_58MM = 32
    const val CHARS_80MM = 48

    fun render(lines: List<PrintLine>, width: Int): List<RenderedLine> {
        require(width >= 16) { "width too small" }
        val out = mutableListOf<RenderedLine>()
        for (line in lines) {
            when (line) {
                is PrintLine.Text -> {
                    val w = if (line.large) width / 2 else width
                    wrap(line.text, w).forEach { part ->
                        out += RenderedLine(align(part, w, line.align), line.align, line.bold, line.large)
                    }
                }
                is PrintLine.Columns -> {
                    val w = if (line.large) width / 2 else width
                    out += columns(line.left, line.right, w).map { RenderedLine(it, PrintAlign.LEFT, line.bold, line.large) }
                }
                is PrintLine.Separator -> out += RenderedLine(line.char.toString().repeat(width))
                is PrintLine.Feed -> repeat(line.lines.coerceIn(0, 10)) { out += RenderedLine("") }
            }
        }
        return out
    }

    /** Convenience for previews and tests. */
    fun renderToString(lines: List<PrintLine>, width: Int): String =
        render(lines, width).joinToString("\n") { it.text.trimEnd() }

    fun columns(left: String, right: String, width: Int): List<String> {
        val rightText = right.take(width)
        val leftWidth = width - rightText.length - 1
        if (leftWidth < 4) {
            // Value too long to share the row: put it on its own right-aligned line.
            return wrap(left, width).map { it.padEnd(width) } + rightText.padStart(width)
        }
        val leftParts = wrap(left, leftWidth)
        return leftParts.mapIndexed { index, part ->
            if (index == 0) part.padEnd(leftWidth) + " " + rightText.padStart(rightText.length)
            else part.padEnd(width)
        }.map { it.padEnd(width).take(width) }
    }

    /** Word wrap that hard-breaks words longer than the width. */
    fun wrap(text: String, width: Int): List<String> {
        val clean = text.replace('\t', ' ').trim()
        if (clean.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (word in clean.split(Regex("\\s+"))) {
            var remaining = word
            while (remaining.length > width) {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current = StringBuilder()
                }
                result += remaining.substring(0, width)
                remaining = remaining.substring(width)
            }
            if (remaining.isEmpty()) continue
            val needed = if (current.isEmpty()) remaining.length else current.length + 1 + remaining.length
            if (needed > width) {
                result += current.toString()
                current = StringBuilder(remaining)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(remaining)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun align(text: String, width: Int, align: PrintAlign): String {
        val t = text.take(width)
        return when (align) {
            PrintAlign.LEFT -> t.padEnd(width)
            PrintAlign.RIGHT -> t.padStart(width)
            PrintAlign.CENTER -> {
                val left = (width - t.length) / 2
                (" ".repeat(left) + t).padEnd(width)
            }
        }
    }
}
