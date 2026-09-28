package com.dtpos.salonmanager.services.printer

import android.content.Context
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.services.branding.LogoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            buildBytes(ReceiptLayout.build(receipt, labels(receipt.paymentMethod)), settings, receipt.logoPath.takeIf { receipt.showLogo })
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

    fun labels(method: PaymentMethod): ReceiptLabels = ReceiptLabels(
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
