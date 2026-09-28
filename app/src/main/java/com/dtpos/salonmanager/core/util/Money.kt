package com.dtpos.salonmanager.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * All money in the app is stored as [Long] minor units (1 Rs. = 100 paisa) so that no
 * floating point rounding error can ever creep into totals, profit or cash reconciliation.
 */
object Money {
    const val MINOR_PER_UNIT = 100L

    /** Upper bound for a single entered amount (Rs. 10 crore). Guards against overflow and typos. */
    const val MAX_AMOUNT_MINOR = 100_000_000_00L

    private val INPUT_PATTERN = Regex("""\d{1,12}(\.\d{0,2})?""")

    fun fromUnits(units: Long): Long = units * MINOR_PER_UNIT

    /**
     * Parses user input such as "1,250", "1250.5" or " 300 " into minor units.
     * Returns null for empty, malformed, negative or unreasonably large input.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty() || !INPUT_PATTERN.matches(cleaned)) return null
        return try {
            val minor = BigDecimal(cleaned.trimEnd('.'))
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
            if (minor > MAX_AMOUNT_MINOR) null else minor
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Editable representation without grouping or symbol: 125050 -> "1250.50", 50000 -> "500". */
    fun toInput(minor: Long): String {
        if (minor == 0L) return ""
        val units = minor / MINOR_PER_UNIT
        val fraction = abs(minor % MINOR_PER_UNIT)
        return if (fraction == 0L) units.toString() else "$units.${fraction.toString().padStart(2, '0')}"
    }

    /** Groups digits with commas: 1234567 -> "1,234,567". Thread-safe (no DecimalFormat). */
    fun groupDigits(value: Long): String {
        val digits = abs(value).toString()
        val sb = StringBuilder(digits.length + digits.length / 3)
        digits.forEachIndexed { index, c ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return if (value < 0) "-$sb" else sb.toString()
    }

    /** Multiplies an amount by a basis-point rate (1250 = 12.50%), rounding half-up. */
    fun applyBps(amountMinor: Long, bps: Int): Long {
        if (amountMinor == 0L || bps == 0) return 0L
        return BigDecimal.valueOf(amountMinor)
            .multiply(BigDecimal.valueOf(bps.toLong()))
            .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
            .toLong()
    }
}

/** Currency shown in the UI and on receipts. Default is Pakistani Rupee. */
data class CurrencyConfig(
    val code: String = DEFAULT_CODE,
    val symbol: String = DEFAULT_SYMBOL,
) {
    companion object {
        const val DEFAULT_CODE = "PKR"
        const val DEFAULT_SYMBOL = "Rs."
    }
}

/** Formats minor-unit amounts, e.g. 1850000 -> "Rs. 18,500", 12550 -> "Rs. 125.50". */
class CurrencyFormatter(val config: CurrencyConfig = CurrencyConfig()) {

    fun format(minor: Long, withSymbol: Boolean = true): String {
        val absolute = abs(minor)
        val units = absolute / Money.MINOR_PER_UNIT
        val fraction = absolute % Money.MINOR_PER_UNIT
        val body = buildString {
            append(Money.groupDigits(units))
            if (fraction != 0L) append('.').append(fraction.toString().padStart(2, '0'))
        }
        val sign = if (minor < 0) "-" else ""
        val symbol = config.symbol.trim()
        return if (withSymbol && symbol.isNotEmpty()) "$sign$symbol $body" else "$sign$body"
    }

    /** Amount without the currency symbol, used in receipt columns. */
    fun plain(minor: Long): String = format(minor, withSymbol = false)

    /** Compact form for chart labels: 18500 units -> "18.5K", 1250000 units -> "1.3M". */
    fun compact(minor: Long): String {
        val units = minor / Money.MINOR_PER_UNIT
        val absUnits = abs(units)
        val sign = if (units < 0) "-" else ""
        return when {
            absUnits >= 1_000_000 -> sign + trimZero(absUnits / 1_000_000.0) + "M"
            absUnits >= 1_000 -> sign + trimZero(absUnits / 1_000.0) + "K"
            else -> sign + absUnits.toString()
        }
    }

    private fun trimZero(value: Double): String {
        val rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
        return rounded.stripTrailingZeros().toPlainString()
    }
}

/** Percentages are stored as basis points (Int): 1250 = 12.50%. */
object Percent {
    const val MAX_BPS = 10_000

    /** "12.5" -> 1250. Returns null when invalid or outside 0..100. */
    fun parseBps(input: String): Int? {
        val cleaned = input.trim().removeSuffix("%").trim()
        if (cleaned.isEmpty() || !Regex("""\d{1,3}(\.\d{0,2})?""").matches(cleaned)) return null
        val bps = BigDecimal(cleaned.trimEnd('.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
        return if (bps in 0..MAX_BPS) bps else null
    }

    /** 1250 -> "12.5", 1000 -> "10". */
    fun formatBps(bps: Int): String =
        BigDecimal.valueOf(bps.toLong(), 2).stripTrailingZeros().toPlainString()

    /** Ratio as a percentage with one decimal: (925, 1000) -> 92.5. Returns 0 when [whole] is 0. */
    fun ratio(part: Long, whole: Long): Double {
        if (whole == 0L) return 0.0
        return BigDecimal.valueOf(part)
            .multiply(BigDecimal.valueOf(100L))
            .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun formatRatio(value: Double): String =
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
