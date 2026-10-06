package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.services.export.ShareMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareMessagesTest {

    private val money = CurrencyFormatter()

    private val labels = ShareMessages.Labels(
        greeting = "Assalam o Alaikum Ali,", receipt = "Receipt", discount = "Discount", total = "Total",
        paid = "Paid", balance = "Udhaar (balance)", thanks = "Thank you for visiting!", cancelled = "This receipt was cancelled.",
    )

    private val dueLabels = ShareMessages.DueLabels(
        greeting = "Assalam o Alaikum Ali,", intro = "A friendly reminder from Royal Barber Shop about your pending bill.",
        receipt = "Receipt", note = "Note", bill = "Bill", paid = "Paid", balance = "Balance due",
        closing = "Please clear it at your convenience. Thank you!",
    )

    private fun receipt(creditMinor: Long = 0, voided: Boolean = false) = ReceiptData(
        saleId = 7, businessName = "Royal Barber Shop", businessPhone = null, businessAddress = null, logoPath = null,
        headerNote = null, footer = null, currency = CurrencyConfig(), showStaff = true, showLogo = false,
        receiptNumber = "SAL-000123", createdAtMillis = 0, customerName = "Ali", customerPhone = "03001234567",
        items = listOf(
            ReceiptItem("Haircut", "Usman", 1, 50_000, 0, 50_000),
            ReceiptItem("Beard", "Usman", 2, 30_000, 0, 60_000),
        ),
        subtotalMinor = 110_000, itemDiscountMinor = 0, saleDiscountMinor = 10_000, totalMinor = 100_000,
        paymentMethod = PaymentMethod.CASH, amountTenderedMinor = null, changeMinor = 0,
        isVoided = voided, voidReason = null, creditMinor = creditMinor,
    )

    @Test
    fun `paid receipt lists every service, discount, total and payment`() {
        val text = ShareMessages.receipt(receipt(), labels, money, "06 Oct 2026, 7:30 PM", "Cash")
        assertEquals(
            listOf(
                "Assalam o Alaikum Ali,",
                "Royal Barber Shop",
                "Receipt SAL-000123 · 06 Oct 2026, 7:30 PM",
                "",
                "• Haircut: Rs. 500",
                "• Beard x2: Rs. 600",
                "Discount: -Rs. 100",
                "Total: Rs. 1,000",
                "Paid (Cash): Rs. 1,000",
                "",
                "Thank you for visiting!",
            ).joinToString("\n"),
            text,
        )
        assertFalse(text.contains("Udhaar"))
    }

    @Test
    fun `udhaar sale shows what was paid and what is left, never paid in full`() {
        val text = ShareMessages.receipt(receipt(creditMinor = 60_000), labels, money, "06 Oct 2026", "JazzCash")
        assertTrue(text, text.contains("Total: Rs. 1,000"))
        assertTrue(text, text.contains("Paid: Rs. 400 (JazzCash)"))
        assertTrue(text, text.contains("Udhaar (balance): Rs. 600"))
        assertFalse(text, text.contains("Paid (JazzCash)"))
    }

    @Test
    fun `full udhaar shows nothing paid`() {
        val text = ShareMessages.receipt(receipt(creditMinor = 100_000), labels, money, "06 Oct 2026", "Cash")
        assertTrue(text, text.contains("Paid: Rs. 0\n"))
        assertTrue(text, text.contains("Udhaar (balance): Rs. 1,000"))
    }

    @Test
    fun `cancelled receipt says so`() {
        val text = ShareMessages.receipt(receipt(voided = true), labels, money, "06 Oct 2026", "Cash")
        assertTrue(text, text.contains("This receipt was cancelled."))
    }

    @Test
    fun `udhaar reminder for a credit sale quotes the receipt, bill, paid and balance`() {
        val text = ShareMessages.dueReminder(dueLabels, money, fromSale = true, reference = "SAL-000123", date = "06 Oct 2026", amountMinor = 100_000, paidMinor = 40_000)
        assertEquals(
            listOf(
                "Assalam o Alaikum Ali,",
                "A friendly reminder from Royal Barber Shop about your pending bill.",
                "",
                "Receipt SAL-000123 · 06 Oct 2026",
                "Bill: Rs. 1,000",
                "Paid: Rs. 400",
                "Balance due: Rs. 600",
                "",
                "Please clear it at your convenience. Thank you!",
            ).joinToString("\n"),
            text,
        )
    }

    @Test
    fun `hand-written due shows its note and skips an empty paid line`() {
        val text = ShareMessages.dueReminder(dueLabels, money, fromSale = false, reference = "Facial last week", date = "01 Oct 2026", amountMinor = 250_000, paidMinor = 0)
        assertTrue(text, text.contains("Note: Facial last week · 01 Oct 2026"))
        assertTrue(text, text.contains("Balance due: Rs. 2,500"))
        assertFalse(text, text.contains("Paid:"))
    }
}
