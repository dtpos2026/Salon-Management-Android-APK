package com.dtpos.salonmanager.services

import android.app.Application
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.services.printer.ReceiptImageRenderer
import com.dtpos.salonmanager.services.printer.ReceiptLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReceiptImageTest {

    private val labels = ReceiptLabels(
        receiptNo = "Receipt", date = "Date", time = "Time", customer = "Customer", walkIn = "Walk-in",
        phone = "Phone", item = "Service", amount = "Amount", by = "By", discount = "Discount",
        subtotal = "Subtotal", total = "TOTAL", payment = "Payment", tendered = "Received", change = "Change",
        voided = "VOID", paymentMethodName = "Cash", defaultFooter = "Shukriya! Thank you",
        poweredBy = "Powered by DT Salon Management",
    )

    private fun receipt(items: Int, voided: Boolean = false) = ReceiptData(
        saleId = 1, businessName = "Royal Barber Shop", businessPhone = "0300-1234567", businessAddress = "Main Bazar, Burewala",
        logoPath = null, headerNote = "Hair • Beard • Facial", footer = null, currency = CurrencyConfig(), showStaff = true,
        showLogo = false, receiptNumber = "SAL-000123", createdAtMillis = 1_790_000_000_000L,
        customerName = "Ali Raza", customerPhone = "03001234567",
        items = (1..items).map { ReceiptItem("Hair cut and beard styling $it", "Usman", 1 + it % 2, 50_000, 0, 50_000L * (1 + it % 2)) },
        subtotalMinor = 400_000, itemDiscountMinor = 0, saleDiscountMinor = 20_000, totalMinor = 380_000,
        paymentMethod = PaymentMethod.CASH, amountTenderedMinor = 400_000, changeMinor = 20_000,
        isVoided = voided, voidReason = if (voided) "Customer left" else null,
    )

    @Test
    fun `renders a 58 mm receipt that grows with the items`() {
        val short = ReceiptImageRenderer(384).render(receipt(1), labels, logo = null)
        val long = ReceiptImageRenderer(384).render(receipt(6), labels, logo = null)
        assertEquals(384, short.width)
        assertTrue("height ${short.height}", short.height > 400)
        assertTrue(long.height > short.height)
    }

    @Test
    fun `share image, printer image, voided and urdu layouts render`() {
        val share = ReceiptImageRenderer(ReceiptImageRenderer.SHARE_WIDTH_PX).render(receipt(3), labels, null)
        assertEquals(720, share.width)
        val printer = ReceiptImageRenderer(576, forPrinter = true).render(receipt(3, voided = true), labels, null)
        assertEquals(576, printer.width)
        val urdu = ReceiptImageRenderer(384, rtl = true).render(
            receipt(2),
            labels.copy(customer = "گاہک", total = "کل رقم", receiptTitle = "رسید"),
            null,
        )
        assertTrue(urdu.height > 400)
    }
}
