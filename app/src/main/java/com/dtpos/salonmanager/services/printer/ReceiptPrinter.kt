package com.dtpos.salonmanager.services.printer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.BookingEntity
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.presentation.tokens.formatSlot
import com.dtpos.salonmanager.services.branding.LogoStore
import com.dtpos.salonmanager.services.export.TokenImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** High-level printing: turns a stored sale into ESC/POS bytes and sends it to the saved printer. */
class ReceiptPrinter(
    private val context: Context,
    private val bluetooth: BluetoothPrinterService,
    private val lan: LanPrinterService,
    private val settingsStore: PrinterSettingsStore,
    private val saleRepository: SaleRepository,
    private val logoStore: LogoStore,
) {

    suspend fun receiptStyle(): ReceiptStyle = settingsStore.current().receiptStyle

    suspend fun tokenStyle(): com.dtpos.salonmanager.services.export.TokenStyle = settingsStore.current().tokenStyle

    /** Text print: items as a table or as a list (also used for the matching on-screen preview). */
    suspend fun itemLayout(): ItemLayout = settingsStore.current().itemLayout

    suspend fun printSale(saleId: Long): PrintResult {
        val receipt = saleRepository.buildReceipt(saleId) ?: return PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
        return printReceipt(receipt)
    }

    suspend fun printReceipt(receipt: ReceiptData): PrintResult {
        val settings = settingsStore.current()
        if (!settings.isConfigured) return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        val job = try {
            buildReceiptBytes(receipt, settings)
        } catch (e: Exception) {
            return PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
        } catch (e: OutOfMemoryError) {
            return PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
        }
        return transmit(settings, job, settings.copies)
    }

    /** ESC/POS bytes and about how many dot rows of paper they print. */
    private class Job(val bytes: ByteArray, val rows: Int)

    /**
     * Sends to the active printer (Bluetooth or network), one job at a time. Except at FAST speed,
     * a job waits until the previous one has had time to print, so bills never mix on paper.
     */
    private suspend fun transmit(settings: PrinterSettings, job: Job, copies: Int): PrintResult = queue.withLock {
        if (settings.speed != PrintSpeed.FAST) {
            val wait = busyUntil - android.os.SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait.coerceAtMost(MAX_WAIT_MS))
        }
        val started = android.os.SystemClock.elapsedRealtime()
        val result = when (settings.connection) {
            PrinterConnection.BLUETOOTH -> bluetooth.send(settings.address, job.bytes, copies, settings.speed)
            PrinterConnection.LAN -> lan.send(settings.lanHost, settings.lanPort, job.bytes, copies)
        }
        if (result is PrintResult.Success) {
            // Printing runs while the data is still arriving, so count from the start.
            busyUntil = started + (job.rows * copies.coerceIn(1, 3) * settings.speed.msPerRow).toLong()
        }
        result
    }

    /** Prints any slip (Close Day report, staff payment) on the active printer. */
    suspend fun printLines(lines: List<PrintLine>): PrintResult {
        val settings = settingsStore.current()
        if (!settings.isConfigured) return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        return transmit(settings, buildBytes(lines, settings, logoPath = null), copies = 1)
    }

    /** The paper the printer is set to (58 mm unless changed in Printer settings). */
    suspend fun paper(): PaperWidth = settingsStore.current().paper

    /**
     * A slip (Close Day, staff payment, udhaar payment) as the printer prints it: rendered at the
     * paper's dot width, black on white. The preview shows it and WhatsApp / share send it.
     */
    suspend fun slipBitmap(lines: List<PrintLine>): Bitmap {
        val dots = settingsStore.current().paper.dots
        return withContext(Dispatchers.Default) { ReceiptCanvasRenderer(dots, textSizePx = dots / 17f, heavy = true).renderBitmap(lines, null) }
    }

    /**
     * Context for slip labels: English when a text-mode printer would get Urdu (its built-in
     * font has Latin letters only), otherwise the app language.
     */
    suspend fun slipContext(): Context =
        if (settingsStore.current().mode == PrintMode.TEXT && isRtl()) englishContext() else context

    /** Checks that a network printer answers on its IP and port (nothing is printed). */
    suspend fun checkLan(host: String, port: Int): PrintResult = lan.check(host, port)

    /**
     * The token slip exactly as the printer prints it: black and white at the paper's dot width.
     * The print preview shows this bitmap and [printTokenSlip] sends the same bitmap.
     */
    suspend fun tokenSlip(booking: BookingEntity, salonName: String): Bitmap {
        val settings = settingsStore.current()
        val whenText = DateTimeUtils.formatDate(LocalDate.ofEpochDay(booking.dateEpochDay)) +
            (booking.timeMinutes?.let { "  ·  " + formatSlot(it) } ?: "")
        return withContext(Dispatchers.Default) {
            TokenImage.render(
                salon = salonName,
                title = context.getString(R.string.tokens_slip_title),
                token = booking.tokenNumber,
                name = booking.customerName,
                whenText = whenText,
                service = booking.service,
                footer = context.getString(R.string.tokens_slip_wait),
                accent = android.graphics.Color.BLACK,
                width = settings.paper.dots,
                forPrinter = true,
                style = settings.tokenStyle,
            )
        }
    }

    suspend fun printTokenSlip(slip: Bitmap): PrintResult {
        val settings = settingsStore.current()
        if (!settings.isConfigured) return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        val job = withContext(Dispatchers.Default) { imageJob(slip, settings) }
        return transmit(settings, job, copies = 1)
    }

    /** Small queue slip: salon, big token number, name, date / booked time. */
    suspend fun printToken(booking: BookingEntity, salonName: String): PrintResult {
        if (!settingsStore.current().isConfigured) return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        return printTokenSlip(tokenSlip(booking, salonName))
    }

    /** Prints a short page that shows alignment, width and characters. */
    suspend fun printTest(overrideAddress: String? = null): PrintResult {
        val saved = settingsStore.current()
        val settings = if (overrideAddress != null) saved.copy(connection = PrinterConnection.BLUETOOTH, address = overrideAddress) else saved
        if (!settings.isConfigured) return PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        val width = settings.paper.chars
        val lines = listOf(
            PrintLine.Text(context.getString(R.string.printer_test_title), PrintAlign.CENTER, bold = true, large = true),
            PrintLine.Text(context.getString(R.string.app_name), PrintAlign.CENTER),
            PrintLine.Separator(),
            PrintLine.Columns(context.getString(R.string.printer_test_paper), if (settings.paper == PaperWidth.MM58) "58 mm" else "80 mm"),
            PrintLine.Columns(context.getString(R.string.printer_test_chars), width.toString()),
            PrintLine.Columns(
                context.getString(R.string.printer_test_connection),
                if (settings.connection == PrinterConnection.LAN) "LAN ${settings.lanHost}:${settings.lanPort}" else "Bluetooth",
            ),
            PrintLine.Text("1234567890".repeat(width / 10 + 1).take(width)),
            PrintLine.Text("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
            PrintLine.Separator('='),
            PrintLine.Text(context.getString(R.string.printer_test_ok), PrintAlign.CENTER, bold = true),
        )
        val job = buildBytes(lines, settings, logoPath = null)
        return transmit(settings, job, copies = 1)
    }

    /** Image mode prints the styled receipt; text mode the classic printer-font layout. */
    private suspend fun buildReceiptBytes(receipt: ReceiptData, settings: PrinterSettings): Job {
        if (settings.mode == PrintMode.TEXT) {
            val lines = ReceiptLayout.build(receipt, labels(receipt.paymentMethod, forTextPrinter = true), items = settings.itemLayout)
            return buildBytes(lines, settings, receipt.logoPath.takeIf { receipt.showLogo })
        }
        return withContext(Dispatchers.Default) {
            val logo = receipt.logoPath.takeIf { receipt.showLogo && settings.printLogo }
                ?.let { logoStore.loadBitmap(it) }
                ?.let { ditheredLogo(it) }
            val bitmap = ReceiptImageRenderer(settings.paper.dots, forPrinter = true, rtl = isRtl(), style = settings.receiptStyle)
                .render(receipt, labels(receipt.paymentMethod), logo)
            imageJob(bitmap, settings)
        }
    }

    /** A rendered picture as a print job: darkened to solid black and sent in strips for [PrintSpeed]. */
    private fun imageJob(bitmap: Bitmap, settings: PrinterSettings): Job {
        val mono = ReceiptCanvasRenderer.toMonochrome(bitmap, dither = false, threshold = PRINT_THRESHOLD)
        return Job(EscPos.encodeImage(mono, settings.feedLines, settings.cut, settings.speed.bandRows), EscPos.imageRows(mono, settings.feedLines))
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

    private suspend fun buildBytes(lines: List<PrintLine>, settings: PrinterSettings, logoPath: String?): Job =
        withContext(Dispatchers.Default) {
            val logo = logoPath?.takeIf { settings.printLogo }?.let { logoStore.loadBitmap(it) }
                ?.let { ReceiptCanvasRenderer.scaleLogo(it, maxWidth = settings.paper.dots / 2, maxHeight = 160) }
            when (settings.mode) {
                PrintMode.TEXT -> {
                    val rendered = ReceiptTextRenderer.render(lines, settings.paper.chars)
                    val mono = logo?.let { ReceiptCanvasRenderer.toMonochrome(it, dither = true) }
                    Job(
                        EscPos.encodeText(rendered, mono, settings.feedLines, settings.cut, settings.speed.bandRows),
                        EscPos.textRows(rendered, mono, settings.feedLines),
                    )
                }
                PrintMode.IMAGE -> {
                    val renderer = ReceiptCanvasRenderer(settings.paper.dots, textSizePx = settings.paper.dots / 17f, heavy = true)
                    imageJob(renderer.renderBitmap(lines, logo), settings)
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
        paidNow = context.getString(R.string.receipt_paid_now),
        balanceDue = context.getString(R.string.receipt_balance_due),
        creditStamp = context.getString(R.string.receipt_credit_stamp),
        qty = context.getString(R.string.receipt_qty),
        rate = context.getString(R.string.receipt_rate),
        itemsCount = context.getString(R.string.receipt_items_count),
        totalQty = context.getString(R.string.receipt_total_qty),
        cashMemo = context.getString(R.string.receipt_cash_memo),
        invoice = context.getString(R.string.receipt_invoice),
        salesInvoice = context.getString(R.string.receipt_sales_invoice),
        grandTotal = context.getString(R.string.receipt_grand_total),
    )

    companion object {
        /** One print job at a time across the whole app, and when the last one should be done. */
        private val queue = Mutex()
        @Volatile private var busyUntil = 0L
        private const val MAX_WAIT_MS = 15_000L

        /**
         * Grey above which a pixel stays white. Higher than the usual 128, so the soft edges of
         * letters turn black too: thicker, darker text on thermal paper.
         */
        const val PRINT_THRESHOLD = 168f

        fun paymentMethodLabel(method: PaymentMethod): Int = when (method) {
            PaymentMethod.CASH -> R.string.payment_cash
            PaymentMethod.CARD -> R.string.payment_card
            PaymentMethod.BANK -> R.string.payment_bank
            PaymentMethod.OTHER -> R.string.payment_other
        }
    }
}
