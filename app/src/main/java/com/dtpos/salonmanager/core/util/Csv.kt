package com.dtpos.salonmanager.core.util

/** Minimal RFC 4180 CSV writer (Excel / Google Sheets compatible). */
class CsvWriter(private val out: Appendable) {

    fun row(vararg values: Any?): CsvWriter = row(values.asList())

    fun row(values: List<Any?>): CsvWriter {
        values.forEachIndexed { index, value ->
            if (index > 0) out.append(',')
            out.append(escape(value?.toString() ?: ""))
        }
        out.append("\r\n")
        return this
    }

    companion object {
        /** UTF-8 byte-order mark so Excel opens Urdu / accented names correctly. */
        const val BOM = "\uFEFF"

        fun escape(value: String): String {
            // Neutralise spreadsheet formula injection from free-text fields such as notes.
            val safe = if (value.isNotEmpty() && value[0] in "=+-@" && value.toDoubleOrNull() == null) "'$value" else value
            val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
        }
    }
}
