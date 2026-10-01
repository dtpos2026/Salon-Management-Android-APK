package com.dtpos.salonmanager.core.util

/** Phone helpers for WhatsApp links. Defaults to Pakistan (+92) for local numbers. */
object PhoneNumbers {

    const val DEFAULT_COUNTRY_CODE = "92"

    /**
     * Converts a local or international number to WhatsApp's digits-only international format,
     * e.g. "0300-1234567" -> "923001234567". Returns null when it cannot be a phone number.
     */
    fun toWhatsApp(raw: String?, countryCode: String = DEFAULT_COUNTRY_CODE): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        var digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        when {
            trimmed.startsWith("+") -> Unit
            digits.startsWith("00") -> digits = digits.drop(2)
            digits.startsWith(countryCode) && digits.length >= 11 -> Unit
            digits.startsWith("0") -> digits = countryCode + digits.drop(1)
            digits.length == 10 && digits.startsWith("3") -> digits = countryCode + digits
        }
        return digits.takeIf { it.length in 10..15 }
    }

    /** Digits only, used for search in the admin panel. */
    fun digits(raw: String?): String = raw.orEmpty().filter { it.isDigit() }
}
