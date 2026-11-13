package com.dtpos.salonmanager.services.printer

import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.util.Money

/**
 * Amount in words for receipts, the way Pakistani bills write it:
 * 1900 -> "One Thousand Nine Hundred", 150000 -> "One Lakh Fifty Thousand".
 */
object AmountWords {
    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
    )
    private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun below100(n: Int): String =
        if (n < 20) ones[n] else tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""

    private fun below1000(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        return listOfNotNull(
            if (hundreds > 0) ones[hundreds] + " Hundred" else null,
            if (rest > 0) below100(rest) else null,
        ).joinToString(" ")
    }

    /** Whole number in words with crore / lakh / thousand. */
    fun words(value: Long): String {
        if (value <= 0L) return "Zero"
        val parts = mutableListOf<String>()
        val crore = value / 10_000_000
        val lakh = (value / 100_000 % 100).toInt()
        val thousand = (value / 1_000 % 100).toInt()
        val rest = (value % 1_000).toInt()
        if (crore > 0) parts += words(crore) + " Crore"
        if (lakh > 0) parts += below100(lakh) + " Lakh"
        if (thousand > 0) parts += below100(thousand) + " Thousand"
        if (rest > 0) parts += below1000(rest)
        return parts.joinToString(" ")
    }

    /** "Rupees One Thousand Nine Hundred Only"; null for other currencies or a negative amount. */
    fun rupees(minor: Long, currency: CurrencyConfig): String? {
        if (minor < 0 || !currency.code.equals("PKR", ignoreCase = true)) return null
        val rupees = minor / Money.MINOR_PER_UNIT
        val paisa = minor % Money.MINOR_PER_UNIT
        return buildString {
            append("Rupees ").append(words(rupees))
            if (paisa > 0) append(" and ").append(words(paisa)).append(" Paisa")
            append(" Only")
        }
    }
}
