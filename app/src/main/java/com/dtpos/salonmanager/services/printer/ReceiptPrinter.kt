package com.dtpos.salonmanager.services.printer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.services.branding.LogoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** High-level printing: turns a stored sale into ESC/POS bytes and sends it to the saved printer. */
class ReceiptPrinter(
    private val context: Context,
    private val bluetooth: BluetoothPrinterService,
    private val settingsStore: PrinterSettingsStore,
    private val saleRepository: SaleRepository,
    private val logoStore: LogoStore,
) {

    suspend fun printSale(saleId: Long): PrintResult {
        val receipt = saleRepository.buildReceipt(saleId) ?: return PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
        return printReceipt(receipt)
    }

    suspend fun printReceipt(receipt: ReceiptData): PrintResult {
        val settings = settingsStore.current()
        if (!settings.isConfigured) return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        val bytes = try {
            buildReceiptBytes(receipt, settings)
        } catch (e: Exception) {
            return PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
        } catch (e: OutOfMemoryError) {
            return PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
        }
        return bluetooth.send(settings.address, bytes, settings.copies)
    }

    /** Prints a short page that shows alignment, width and characters. */
    suspend fun printTest(overrideAddress: String? = null): PrintResult {
        val settings = settingsStore.current()
        val address = overrideAddress ?: settings.address ?: return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        val width = settings.paper.chars
        val lines = listOf(
            PrintLine.Text(context.getString(R.string.printer_test_title), PrintAlign.CENTER, bold = true, large = true),
            PrintLine.Text(context.getString(R.string.app_name), PrintAlign.CENTER),
            PrintLine.Separator(),
            PrintLine.Columns(context.getString(R.string.printer_test_paper), if (settings.paper == PaperWidth.MM58) "58 mm" else "80 mm"),
            PrintLine.Columns(context.getString(R.string.printer_test_chars), width.toString()),
            PrintLine.Text("1234567890".repeat(width / 10 + 1).take(width)),
            PrintLine.Text("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
            PrintLine.Separator('='),
            PrintLine.Text(context.getString(R.string.printer_test_ok), PrintAlign.CENTER, bold = true),
        )
        val bytes = withContext(Dispatchers.Default) { buildBytes(lines, settings, logoPath = null) }
        return bluetooth.send(address, bytes, copies = 1)
    }

    /** Image mode prints the styled receipt; text mode the classic printer-font layout. */
    private suspend fun buildReceiptBytes(receipt: ReceiptData, settings: PrinterSettings): ByteArray {
        if (settings.mode == PrintMode.TEXT) {
            val lines = ReceiptLayout.build(receipt, labels(receipt.paymentMethod, forTextPrinter = true))
            return buildBytes(lines, settings, receipt.logoPath.takeIf { receipt.showLogo })
        }
        return withContext(Dispatchers.Default) {
            val logo = receipt.logoPath.takeIf { receipt.showLogo && settings.printLogo }
                ?.let { logoStore.loadBitmap(it) }
                ?.let { ditheredLogo(it) }
            val bitmap = ReceiptImageRenderer(settings.paper.dots, forPrinter = true, rtl = isRtl())
                .render(receipt, labels(receipt.paymentMethod), logo)
            EscPos.encodeImage(ReceiptCanvasRenderer.toMonochrome(bitmap, dither = false), settings.feedLines, settings.cut)
        }
    }

    /** Photos and coloured logos look best on thermal paper when dithered once, before layout. */
    private fun ditheredLogo(source: Bitmap): Bitmap {
        val scaled = ReceiptCanvasRenderer.scaleLogo(source, maxWidth = 200, maxHeight = 120)
        val mono = ReceiptCanvasRenderer.toMonochrome(scaled, dither = true)
        val pixels = IntArray(mono.width * mono.height) { i ->
            if (mono.isBlack(i % mono.width, i / mono.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, mono.width, mono.height, Bitmap.Config.ARGB_8888)
    }

    fun isRtl(): Boolean = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private suspend fun buildBytes(lines: List<PrintLine>, settings: PrinterSettings, logoPath: String?): ByteArray =
        withContext(Dispatchers.Default) {
            val logo = logoPath?.takeIf { settings.printLogo }?.let { logoStore.loadBitmap(it) }
                ?.let { ReceiptCanvasRenderer.scaleLogo(it, maxWidth = settings.paper.dots / 2, maxHeight = 160) }
            when (settings.mode) {
                PrintMode.TEXT -> EscPos.encodeText(
                    lines = ReceiptTextRenderer.render(lines, settings.paper.chars),
                    logo = logo?.let { ReceiptCanvasRenderer.toMonochrome(it, dither = true) },
                    feedLines = settings.feedLines,
                    cut = settings.cut,
                )
                PrintMode.IMAGE -> {
                    val renderer = ReceiptCanvasRenderer(settings.paper.dots, textSizePx = settings.paper.dots / 17f)
                    val bitmap = renderer.renderBitmap(lines, logo)
                    EscPos.encodeImage(ReceiptCanvasRenderer.toMonochrome(bitmap, dither = false), settings.feedLines, settings.cut)
                }
            }
        }

    /**
     * Receipt labels in the app language. The printer's built-in font only has Latin letters, so
     * text-mode printing falls back to English labels when the app is in Urdu.
     */
    fun labels(method: PaymentMethod, forTextPrinter: Boolean = false): ReceiptLabels {
        val res = if (forTextPrinter && isRtl()) englishContext() else context
        return labelsFrom(res, method)
    }

    private fun englishContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        return context.createConfigurationContext(config)
    }

    private fun labelsFrom(context: Context, method: PaymentMethod): ReceiptLabels = ReceiptLabels(
        receiptNo = context.getString(R.string.receipt_no),
        date = context.getString(R.string.receipt_date),
        time = context.getString(R.string.receipt_time),
        customer = context.getString(R.string.receipt_customer),
        walkIn = context.getString(R.string.walk_in_customer),
        phone = context.getString(R.string.receipt_phone),
        item = context.getString(R.string.receipt_item),
        amount = context.getString(R.string.receipt_amount),
        by = context.getString(R.string.receipt_by),
        discount = context.getString(R.string.receipt_discount),
        subtotal = context.getString(R.string.receipt_subtotal),
        total = context.getString(R.string.receipt_total),
        payment = context.getString(R.string.receipt_payment),
        tendered = context.getString(R.string.receipt_tendered),
        change = context.getString(R.string.receipt_change),
        voided = context.getString(R.string.receipt_voided),
        paymentMethodName = context.getString(paymentMethodLabel(method)),
        defaultFooter = context.getString(R.string.receipt_default_footer),
        receiptTitle = context.getString(R.string.receipt_title),
        servedBy = context.getString(R.string.receipt_served_by),
        paidStamp = context.getString(R.string.receipt_paid_stamp),
        poweredBy = context.getString(R.string.receipt_powered_by, context.getString(R.string.app_name)),
    )

    companion object {
        fun paymentMethodLabel(method: PaymentMethod): Int = when (method) {
            PaymentMethod.CASH -> R.string.payment_cash
            PaymentMethod.CARD -> R.string.payment_card
            PaymentMethod.BANK -> R.string.payment_bank
            PaymentMethod.OTHER -> R.string.payment_other
        }
    }
}
