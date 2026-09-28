package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.services.printer.EscPos
import com.dtpos.salonmanager.services.printer.EscPosBuilder
import com.dtpos.salonmanager.services.printer.MonochromeImage
import com.dtpos.salonmanager.services.printer.PrintAlign
import com.dtpos.salonmanager.services.printer.PrintLine
import com.dtpos.salonmanager.services.printer.ReceiptLabels
import com.dtpos.salonmanager.services.printer.ReceiptLayout
import com.dtpos.salonmanager.services.printer.ReceiptTextRenderer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterFormattingTest {

    private val labels = ReceiptLabels(
        receiptNo = "Receipt", date = "Date", time = "Time", customer = "Customer", walkIn = "Walk-in",
        phone = "Ph", item = "Item", amount = "Amount", by = "By", discount = "Discount", subtotal = "Subtotal",
        total = "TOTAL", payment = "Payment", tendered = "Received", change = "Change", voided = "VOID",
        paymentMethodName = "Cash", defaultFooter = "Thank you!",
    )

    private val receipt = ReceiptData(
        saleId = 1, businessName = "Royal Barber Shop", businessPhone = "0300-1234567",
        businessAddress = "Main Bazaar, Lahore", logoPath = null, headerNote = null, footer = "Thank you for visiting!",
        currency = CurrencyConfig(), showStaff = true, showLogo = false, receiptNumber = "SAL-000042",
        createdAtMillis = 1_790_000_000_000, customerName = "Ali Khan", customerPhone = null,
        items = listOf(
            ReceiptItem("Hair Cut", "Ahmed", 1, 50_000, 0, 50_000),
            ReceiptItem("Beard", "Ahmed", 2, 30_000, 10_000, 50_000),
        ),
        subtotalMinor = 110_000, itemDiscountMinor = 10_000, saleDiscountMinor = 0, totalMinor = 100_000,
        paymentMethod = PaymentMethod.CASH, amountTenderedMinor = 150_000, changeMinor = 50_000,
        isVoided = false, voidReason = null,
    )

    @Test
    fun `every rendered line fits 58mm paper`() {
        val lines = ReceiptLayout.build(receipt, labels)
        val rendered = ReceiptTextRenderer.render(lines, ReceiptTextRenderer.CHARS_58MM)
        rendered.forEach { line ->
            val width = if (line.large) 16 else 32
            assertTrue("'${line.text}' too wide", line.text.length <= width)
        }
        val text = ReceiptTextRenderer.renderToString(lines, 32)
        assertTrue(text.contains("SAL-000042"))
        assertTrue(text.contains("Ali Khan"))
        assertTrue(text.contains("Hair Cut"))
        assertTrue(text.contains("By: Ahmed"))
        assertTrue(text.contains("Rs. 1,000"))
        assertTrue(text.contains("Thank you for visiting!"))
    }

    @Test
    fun `80mm layout also fits`() {
        val rendered = ReceiptTextRenderer.render(ReceiptLayout.build(receipt, labels), ReceiptTextRenderer.CHARS_80MM)
        assertTrue(rendered.all { it.text.length <= if (it.large) 24 else 48 })
    }

    @Test
    fun `columns right align values and wrap long names`() {
        assertEquals(listOf("Hair Cut                     500"), ReceiptTextRenderer.columns("Hair Cut", "500", 32))
        val wrapped = ReceiptTextRenderer.columns("Very long premium hair treatment with keratin", "12,500", 32)
        assertTrue(wrapped.size > 1)
        assertTrue(wrapped[0].endsWith("12,500"))
        assertTrue(wrapped.all { it.length == 32 })
    }

    @Test
    fun `word wrap hard breaks long words`() {
        assertEquals(listOf("abcde", "fghij", "k"), ReceiptTextRenderer.wrap("abcdefghijk", 5))
        assertEquals(listOf("one two", "three"), ReceiptTextRenderer.wrap("one two three", 7))
    }

    @Test
    fun `centered text`() {
        val out = ReceiptTextRenderer.render(listOf(PrintLine.Text("ABC", PrintAlign.CENTER)), 16)
        assertEquals("      ABC       ", out[0].text)
    }

    @Test
    fun `escpos text is ascii safe`() {
        assertArrayEquals("Cafe ?".toByteArray(), EscPos.toPrinterBytes("Café ا"))
        assertArrayEquals("Rs 5".toByteArray(), EscPos.toPrinterBytes("₨ 5"))
    }

    @Test
    fun `escpos stream starts with init and ends with cut`() {
        val rendered = ReceiptTextRenderer.render(ReceiptLayout.build(receipt, labels), 32)
        val bytes = EscPos.encodeText(rendered, logo = null, feedLines = 3, cut = true)
        assertEquals(0x1B, bytes[0].toInt())
        assertEquals('@'.code, bytes[1].toInt())
        val tail = bytes.takeLast(4).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1D, 'V'.code, 66, 0), tail)
    }

    @Test
    fun `raster command encodes bits msb first`() {
        // 9x1 image: first and last pixel black -> 2 bytes per row: 0x80, 0x80
        val pixels = BooleanArray(9).also { it[0] = true; it[8] = true }
        val bytes = EscPosBuilder().raster(MonochromeImage(9, 1, pixels)).build().map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1D, 'v'.code, '0'.code, 0, 2, 0, 1, 0, 0x80, 0x80), bytes)
    }

    @Test
    fun `dithering maps black white and transparent`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val transparent = 0x00000000
        val img = MonochromeImage.fromArgb(intArrayOf(black, white, transparent, black), 2, 2)
        assertTrue(img.isBlack(0, 0))
        assertTrue(!img.isBlack(1, 0))
        assertTrue(!img.isBlack(0, 1))
        assertTrue(img.isBlack(1, 1))
    }
}
