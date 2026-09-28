package com.dtpos.salonmanager.domain.calc

/**
 * Receipt numbers look like "SAL-000001". The numeric part comes from a per-business sequence
 * that is incremented inside the same database transaction as the sale, and the receipt number
 * column has a UNIQUE index, so duplicates are impossible even if the prefix is changed later.
 */
object ReceiptNumbering {
    const val DEFAULT_PREFIX = "SAL"
    const val DIGITS = 6
    private val PATTERN = Regex("^([A-Z0-9]{1,8})-(\\d{$DIGITS,})$")

    fun format(prefix: String, sequence: Long): String {
        require(sequence > 0) { "sequence must be positive" }
        val cleanPrefix = prefix.trim().uppercase().ifEmpty { DEFAULT_PREFIX }
        return "$cleanPrefix-${sequence.toString().padStart(DIGITS, '0')}"
    }

    fun isValid(receiptNumber: String): Boolean = PATTERN.matches(receiptNumber)

    fun parseSequence(receiptNumber: String): Long? =
        PATTERN.matchEntire(receiptNumber)?.groupValues?.get(2)?.toLongOrNull()
}
