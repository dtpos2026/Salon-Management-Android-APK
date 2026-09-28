package com.dtpos.salonmanager.core.validation

import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.Percent

/** Validation failures. The presentation layer maps each to a translated message. */
enum class ValidationError {
    REQUIRED,
    TOO_LONG,
    INVALID_PHONE,
    INVALID_AMOUNT,
    AMOUNT_MUST_BE_POSITIVE,
    INVALID_PERCENT,
    INVALID_QUANTITY,
    DISCOUNT_EXCEEDS_AMOUNT,
    DUPLICATE_NAME,
    DUPLICATE_PHONE,
    INVALID_PREFIX,
    PIN_FORMAT,
    PASSWORD_TOO_SHORT,
    CONFIRMATION_MISMATCH,
    INVALID_DURATION,
}

/** Result of parsing + validating a single input field. */
sealed interface FieldResult<out T> {
    data class Valid<T>(val value: T) : FieldResult<T>
    data class Invalid(val error: ValidationError) : FieldResult<Nothing>

    val valueOrNull: T? get() = (this as? Valid<T>)?.value
    val errorOrNull: ValidationError? get() = (this as? Invalid)?.error
}

object Validators {
    const val MAX_NAME_LENGTH = 60
    const val MAX_NOTE_LENGTH = 500
    const val MAX_QUANTITY = 99
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_SERVICE_DURATION_MIN = 600

    private val PREFIX_PATTERN = Regex("[A-Za-z0-9]{1,8}")

    fun requiredName(value: String, maxLength: Int = MAX_NAME_LENGTH): FieldResult<String> {
        val trimmed = value.trim().replace(Regex("\\s+"), " ")
        return when {
            trimmed.isEmpty() -> FieldResult.Invalid(ValidationError.REQUIRED)
            trimmed.length > maxLength -> FieldResult.Invalid(ValidationError.TOO_LONG)
            else -> FieldResult.Valid(trimmed)
        }
    }

    fun optionalText(value: String, maxLength: Int = MAX_NOTE_LENGTH): FieldResult<String?> {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> FieldResult.Valid(null)
            trimmed.length > maxLength -> FieldResult.Invalid(ValidationError.TOO_LONG)
            else -> FieldResult.Valid(trimmed)
        }
    }

    /** Phone numbers: optional unless [required]; 7-15 digits, may contain +, spaces, dashes, brackets. */
    fun phone(value: String, required: Boolean = false): FieldResult<String?> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return if (required) FieldResult.Invalid(ValidationError.REQUIRED) else FieldResult.Valid(null)
        }
        if (!trimmed.all { it.isDigit() || it in "+-() " }) return FieldResult.Invalid(ValidationError.INVALID_PHONE)
        if (trimmed.indexOf('+') > 0) return FieldResult.Invalid(ValidationError.INVALID_PHONE)
        val digits = trimmed.count { it.isDigit() }
        return if (digits in 7..15) FieldResult.Valid(trimmed) else FieldResult.Invalid(ValidationError.INVALID_PHONE)
    }

    /** Digits-only form used for duplicate detection and search, e.g. "+92 300-1234567" -> "923001234567". */
    fun normalizePhone(value: String?): String? =
        value?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }

    /** Money input. [allowZero] = false rejects 0 (e.g. service prices, expenses). */
    fun amount(input: String, allowZero: Boolean = false, required: Boolean = true): FieldResult<Long> {
        if (input.isBlank()) {
            return if (required) FieldResult.Invalid(ValidationError.REQUIRED) else FieldResult.Valid(0L)
        }
        val minor = Money.parse(input) ?: return FieldResult.Invalid(ValidationError.INVALID_AMOUNT)
        if (!allowZero && minor <= 0L) return FieldResult.Invalid(ValidationError.AMOUNT_MUST_BE_POSITIVE)
        return FieldResult.Valid(minor)
    }

    fun percentBps(input: String, required: Boolean = false): FieldResult<Int> {
        if (input.isBlank()) {
            return if (required) FieldResult.Invalid(ValidationError.REQUIRED) else FieldResult.Valid(0)
        }
        val bps = Percent.parseBps(input) ?: return FieldResult.Invalid(ValidationError.INVALID_PERCENT)
        return FieldResult.Valid(bps)
    }

    fun quantity(value: Int): FieldResult<Int> =
        if (value in 1..MAX_QUANTITY) FieldResult.Valid(value) else FieldResult.Invalid(ValidationError.INVALID_QUANTITY)

    fun durationMinutes(input: String): FieldResult<Int> {
        if (input.isBlank()) return FieldResult.Valid(0)
        val minutes = input.trim().toIntOrNull() ?: return FieldResult.Invalid(ValidationError.INVALID_DURATION)
        return if (minutes in 0..MAX_SERVICE_DURATION_MIN) FieldResult.Valid(minutes)
        else FieldResult.Invalid(ValidationError.INVALID_DURATION)
    }

    fun receiptPrefix(value: String): FieldResult<String> {
        val trimmed = value.trim().uppercase()
        return if (PREFIX_PATTERN.matches(trimmed)) FieldResult.Valid(trimmed)
        else FieldResult.Invalid(ValidationError.INVALID_PREFIX)
    }

    fun pin(value: String): FieldResult<String> =
        if (value.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && value.all { it.isDigit() }) FieldResult.Valid(value)
        else FieldResult.Invalid(ValidationError.PIN_FORMAT)

    fun password(value: String): FieldResult<String> =
        if (value.length >= MIN_PASSWORD_LENGTH) FieldResult.Valid(value)
        else FieldResult.Invalid(ValidationError.PASSWORD_TOO_SHORT)
}
