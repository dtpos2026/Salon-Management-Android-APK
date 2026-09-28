package com.dtpos.salonmanager.core

import com.dtpos.salonmanager.core.security.PasswordHasher
import com.dtpos.salonmanager.core.util.CsvWriter
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.domain.calc.ReceiptNumbering
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.domain.model.TargetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoreUtilsTest {

    @Test
    fun `money parsing accepts common input and rejects invalid`() {
        assertEquals(125_000L, Money.parse("1,250"))
        assertEquals(125_050L, Money.parse("1250.5"))
        assertEquals(125_055L, Money.parse(" 1250.55 "))
        assertEquals(30_000L, Money.parse("300."))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("999999999999"))
    }

    @Test
    fun `money formatting`() {
        val f = CurrencyFormatter(CurrencyConfig())
        assertEquals("Rs. 18,500", f.format(1_850_000))
        assertEquals("Rs. 125.50", f.format(12_550))
        assertEquals("-Rs. 4,200", f.format(-420_000))
        assertEquals("1,000,000", f.plain(100_000_000))
        assertEquals("Rs. 0", f.format(0))
        assertEquals("18.5K", f.compact(1_850_000))
        assertEquals("1.3M", f.compact(125_000_000)) // 1.25M rounds half-up
        assertEquals("$ 5", CurrencyFormatter(CurrencyConfig("USD", "$")).format(500))
        assertEquals("1250.50", Money.toInput(125_050))
        assertEquals("500", Money.toInput(50_000))
    }

    @Test
    fun `basis points helpers`() {
        assertEquals(1250, Percent.parseBps("12.5"))
        assertEquals(10000, Percent.parseBps("100%"))
        assertNull(Percent.parseBps("100.01"))
        assertEquals("12.5", Percent.formatBps(1250))
        assertEquals(333L, Money.applyBps(3333, 1000))
        assertEquals(92.5, Percent.ratio(925, 1000), 0.0001)
        assertEquals(0.0, Percent.ratio(5, 0), 0.0)
    }

    @Test
    fun `validators`() {
        assertEquals(FieldResult.Valid("Ali Khan"), Validators.requiredName("  Ali   Khan "))
        assertEquals(ValidationError.REQUIRED, Validators.requiredName("   ").errorOrNull)
        assertEquals(ValidationError.TOO_LONG, Validators.requiredName("x".repeat(61)).errorOrNull)
        assertEquals(FieldResult.Valid<String?>(null), Validators.phone(""))
        assertEquals(ValidationError.REQUIRED, Validators.phone("", required = true).errorOrNull)
        assertEquals("0300-1234567", Validators.phone("0300-1234567").valueOrNull)
        assertEquals(ValidationError.INVALID_PHONE, Validators.phone("12ab").errorOrNull)
        assertEquals(ValidationError.INVALID_PHONE, Validators.phone("12+345678").errorOrNull)
        assertEquals("923001234567", Validators.normalizePhone("+92 300-1234567"))
        assertEquals(ValidationError.AMOUNT_MUST_BE_POSITIVE, Validators.amount("0").errorOrNull)
        assertEquals(0L, Validators.amount("0", allowZero = true).valueOrNull)
        assertEquals(ValidationError.INVALID_AMOUNT, Validators.amount("-10").errorOrNull)
        assertEquals("SAL", Validators.receiptPrefix(" sal ").valueOrNull)
        assertEquals(ValidationError.INVALID_PREFIX, Validators.receiptPrefix("SA-L").errorOrNull)
        assertEquals(ValidationError.PIN_FORMAT, Validators.pin("12a4").errorOrNull)
        assertEquals("1234", Validators.pin("1234").valueOrNull)
        assertEquals(ValidationError.INVALID_DURATION, Validators.durationMinutes("-1").errorOrNull)
    }

    @Test
    fun `receipt numbers are zero padded and parseable`() {
        assertEquals("SAL-000001", ReceiptNumbering.format("SAL", 1))
        assertEquals("BRB-000123", ReceiptNumbering.format("brb", 123))
        assertEquals("SAL-1234567", ReceiptNumbering.format("SAL", 1_234_567))
        assertTrue(ReceiptNumbering.isValid("SAL-000002"))
        assertFalse(ReceiptNumbering.isValid("SAL-12"))
        assertEquals(42L, ReceiptNumbering.parseSequence("SAL-000042"))
        val numbers = (1L..5000L).map { ReceiptNumbering.format("SAL", it) }
        assertEquals(numbers.size, numbers.toSet().size)
    }

    @Test
    fun `pin hashing never stores plain text and verifies`() {
        val hash = PasswordHasher.hash("4321".toCharArray(), iterations = 1_000)
        assertFalse(hash.contains("4321"))
        assertTrue(PasswordHasher.verify("4321".toCharArray(), hash))
        assertFalse(PasswordHasher.verify("1234".toCharArray(), hash))
        assertFalse(PasswordHasher.verify("4321".toCharArray(), "garbage"))
        // Same secret, different salt -> different hash.
        assertFalse(hash == PasswordHasher.hash("4321".toCharArray(), iterations = 1_000))
        val code = PasswordHasher.newRecoveryCode()
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
        assertEquals(code.replace("-", ""), PasswordHasher.normalizeRecoveryCode(code.lowercase()))
    }

    @Test
    fun `csv escaping`() {
        val sb = StringBuilder()
        CsvWriter(sb).row("a", "b,c", "say \"hi\"", null, 5).row("=cmd()", "-5")
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\",,5\r\n'=cmd(),-5\r\n", sb.toString())
    }

    @Test
    fun `period presets`() {
        val today = LocalDate.of(2026, 9, 30) // Wednesday
        assertEquals(DateRange(LocalDate.of(2026, 9, 28), today), Periods.range(PeriodPreset.THIS_WEEK, today))
        assertEquals(
            DateRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)),
            Periods.range(PeriodPreset.LAST_WEEK, today),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
            Periods.range(PeriodPreset.LAST_MONTH, today),
        )
        assertEquals(30, Periods.targetRange(TargetPeriod.MONTHLY, today).dayCount)
        assertEquals(7, Periods.targetRange(TargetPeriod.WEEKLY, today).dayCount)
        assertEquals(
            DateRange(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 29)),
            DateRange(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2)).previous(),
        )
    }
}
