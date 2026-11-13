package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.services.printer.EscPos
import com.dtpos.salonmanager.services.printer.EscPosBuilder
import com.dtpos.salonmanager.services.printer.ItemLayout
import com.dtpos.salonmanager.services.printer.MonochromeImage
import com.dtpos.salonmanager.services.printer.PrintAlign
import com.dtpos.salonmanager.services.printer.PrintLine
import com.dtpos.salonmanager.services.printer.PrintSpeed
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
        // One staff member for every service: named once at the top, not under each item.
        assertTrue(text, text.contains("Served by") && text.contains("Ahmed"))
        assertTrue(text, !text.contains("By: Ahmed"))
        assertTrue(text.contains("Rs. 1,000"))
        assertTrue(text.contains("Thank you for visiting!"))
    }

    @Test
    fun `table layout puts qty rate and amount in straight columns on 58mm`() {
        val two = receipt.copy(items = receipt.items + ReceiptItem("Facial and full body massage deluxe", "Bilal", 1, 1_250_000, 0, 1_250_000))
        val rendered = ReceiptTextRenderer.render(ReceiptLayout.build(two, labels, items = ItemLayout.TABLE), 32)
        rendered.forEach { line -> assertTrue("'${line.text}' too wide", line.text.length <= if (line.large) 16 else 32) }
        val text = rendered.joinToString("\n") { it.text.trimEnd() }
        val header = rendered.first { it.text.startsWith("Item") }
        assertTrue(header.bold)
        assertTrue(header.text, header.text.contains("Qty") && header.text.contains("Rate") && header.text.trimEnd().endsWith("Amount"))
        // Amounts end in the same column as the "Amount" title.
        val end = header.text.trimEnd().length
        val beard = rendered.first { it.text.startsWith("Beard") }.text
        assertEquals(end, beard.trimEnd().length)
        assertTrue(beard, beard.contains(" 2 ") && beard.contains("300") && beard.trimEnd().endsWith("600"))
        // Long names wrap inside the first column; the numbers stay on the first line.
        val facial = rendered.indexOfFirst { it.text.startsWith("Facial") }
        assertTrue(rendered[facial].text.trimEnd().endsWith("12,500"))
        assertTrue(rendered[facial + 1].text.trim().isNotEmpty() && !rendered[facial + 1].text.contains("12,500"))
        // Two people worked, so each service shows who did it.
        assertTrue(text, text.contains("By: Ahmed") && text.contains("By: Bilal"))
        assertTrue(text, text.contains("Discount") && text.contains("-100"))
    }

    @Test
    fun `table falls back to numbers under the name when the paper is too narrow`() {
        val table = PrintLine.Table(
            listOf("Item", "Qty", "Rate", "Amount"),
            listOf(PrintLine.Table.Row(listOf("Hair Cut", "1", "1,234,567", "12,345,678"))),
        )
        val rendered = ReceiptTextRenderer.table(table, 20)
        rendered.forEach { assertTrue("'${it.text}' too wide", it.text.length <= 20) }
        assertTrue(rendered.any { it.text.trimEnd().endsWith("12,345,678") })
        assertTrue(rendered.any { it.text.startsWith("Hair Cut") })
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
    fun `a job resets styles without ESC @ and ends with a cut`() {
        val rendered = ReceiptTextRenderer.render(ReceiptLayout.build(receipt, labels), 32)
        val bytes = EscPos.encodeText(rendered, logo = null, feedLines = 3, cut = true).map { it.toInt() and 0xFF }
        // Bold off, normal size, left: the previous bill's settings never carry over.
        assertEquals(listOf(0x1B, 'E'.code, 0, 0x1D, '!'.code, 0, 0x1B, 'a'.code, 0), bytes.take(9))
        // No ESC @: it clears the printer's buffer, so a bill still printing was printed over.
        assertTrue(bytes.windowed(2).none { it == listOf(0x1B, '@'.code) })
        assertEquals(listOf(0x1D, 'V'.code, 66, 0), bytes.takeLast(4))
    }

    @Test
    fun `pictures go in strips of the print speed and paper rows are estimated`() {
        val image = MonochromeImage(16, 100, BooleanArray(16 * 100))
        fun strips(speed: PrintSpeed): Int {
            val bytes = EscPos.encodeImage(image, feedLines = 2, cut = false, bandRows = speed.bandRows).map { it.toInt() and 0xFF }
            return bytes.windowed(3).count { it == listOf(0x1D, 'v'.code, '0'.code) }
        }
        assertEquals(5, strips(PrintSpeed.SAFE)) // 24 + 24 + 24 + 24 + 4 rows
        assertEquals(1, strips(PrintSpeed.STANDARD))
        assertEquals(1, strips(PrintSpeed.FAST))
        assertEquals(100 + 2 * 30, EscPos.imageRows(image, feedLines = 2))
        val rendered = ReceiptTextRenderer.render(ReceiptLayout.build(receipt, labels), 32)
        assertTrue(EscPos.textRows(rendered, null, 3) >= rendered.size * 30 + 90)
        // Safe is slowest and gives the printer the most time; Fast keeps the connection.
        assertTrue(PrintSpeed.SAFE.msPerRow > PrintSpeed.STANDARD.msPerRow && PrintSpeed.STANDARD.msPerRow > PrintSpeed.FAST.msPerRow)
        assertTrue(PrintSpeed.FAST.keepConnection && !PrintSpeed.STANDARD.keepConnection && !PrintSpeed.SAFE.keepConnection)
    }

    @Test
    fun `darker threshold turns light grey edges of letters black`() {
        val grey = 0xFF9A9A9A.toInt() // luminance 154: between 128 and 168
        assertTrue(!MonochromeImage.fromArgb(intArrayOf(grey), 1, 1, dither = false).isBlack(0, 0))
        assertTrue(MonochromeImage.fromArgb(intArrayOf(grey), 1, 1, dither = false, threshold = 168f).isBlack(0, 0))
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
