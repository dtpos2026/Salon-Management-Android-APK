package com.dtpos.salonmanager.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.domain.model.ReceiptItem
import com.dtpos.salonmanager.presentation.account.AccessGate
import com.dtpos.salonmanager.presentation.account.IntroSplash
import com.dtpos.salonmanager.presentation.account.IntroState
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.settings.AppPreferencesScreen
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.FakeAccountBackend
import com.dtpos.salonmanager.services.account.AccountStatus
import com.dtpos.salonmanager.services.account.CloudAccount
import com.dtpos.salonmanager.services.account.SignedInUser
import com.dtpos.salonmanager.services.prefs.ColorTheme
import com.dtpos.salonmanager.services.printer.ReceiptImageRenderer
import com.dtpos.salonmanager.services.printer.ReceiptStyle
import com.dtpos.salonmanager.services.printer.ReceiptLabels
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Renders the real screens with Robolectric's native graphics and saves them as images
 * (build/screenshots), so the design can be reviewed without a phone. Also checks that the
 * screens compose without crashing in every colour theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h850dp-hdpi")
class ScreenshotTest {

    companion object {
        const val SMALL_PHONE = "w320dp-h568dp-mdpi"
        const val TABLET = "w800dp-h1280dp-mdpi"
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val backend = FakeAccountBackend()
    private lateinit var container: AppContainer
    private val outDir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    @Before
    fun setUp() {
        app.getSharedPreferences("dt_account", 0).edit().clear().commit()
        app.getSharedPreferences("dt_ui", 0).edit().clear().commit()
        app.deleteDatabase("salon.db")
        container = AppContainer(app, backend)
        IntroState.shown = true
        // The salon app opens only with location allowed and on (LocationGate).
        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        org.robolectric.Shadows.shadowOf(app.getSystemService(android.location.LocationManager::class.java)).setLocationEnabled(true)
    }

    @After
    fun tearDown() {
        container.closeDatabase()
    }

    private fun save(name: String, bitmap: Bitmap) {
        FileOutputStream(File(outDir, "$name.jpg")).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 78, it) }
    }

    /** Draws the whole window (software canvas, real pixels with native graphics). */
    private fun windowBitmap(): Bitmap {
        var bitmap: Bitmap? = null
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return bitmap!!
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        save(name, windowBitmap())
    }

    private fun showGate(theme: ColorTheme, dark: Boolean) {
        container.uiPreferences.setColorTheme(theme)
        compose.setContent {
            SalonTheme(darkTheme = dark, colorTheme = theme) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AccessGate { SalonAppRoot() }
                }
            }
        }
    }

    private fun startAccounts() = runBlocking {
        container.initialize()
        container.accountManager.refresh()
    }

    @Test
    fun loginRoyalPurple() {
        startAccounts()
        showGate(ColorTheme.ROYAL_PURPLE, dark = false)
        capture("01-login-royal-purple")
    }

    @Test
    fun pendingApprovalBlackGold() {
        backend.user = SignedInUser("u1", "royalcuts@gmail.com", "Ali Raza", null)
        backend.accounts["u1"] = CloudAccount(uid = "u1", email = "royalcuts@gmail.com", salonName = "Royal Cuts", ownerName = "Ali Raza", status = AccountStatus.PENDING)
        startAccounts()
        showGate(ColorTheme.BLACK_GOLD, dark = true)
        capture("02-pending-black-gold")
    }

    @Test
    fun newPhoneNeedsApproval() {
        backend.user = SignedInUser("u1", "royalcuts@gmail.com")
        backend.accounts["u1"] = CloudAccount(
            uid = "u1", email = "royalcuts@gmail.com", salonName = "Royal Cuts", status = AccountStatus.APPROVED,
            customerId = "DTC-0002", deviceId = "a-first-phone", deviceModel = "Samsung Galaxy A15",
        )
        startAccounts()
        showGate(ColorTheme.ROYAL_PURPLE, dark = false)
        capture("09-new-phone-approval")
    }

    @Test
    fun paymentPendingRoseGold() {
        backend.user = SignedInUser("u1", "glam@gmail.com", "Sara", null)
        backend.accounts["u1"] = CloudAccount(
            uid = "u1", email = "glam@gmail.com", salonName = "Glam Studio", status = AccountStatus.PAYMENT_PENDING,
            customerId = "DTC-0007", licenseId = "DTL-7KQM-2XWD", pendingAmount = 4000, messageToUser = "Please pay by the 5th to continue.",
        )
        startAccounts()
        showGate(ColorTheme.ROSE_GOLD, dark = true)
        capture("03-payment-pending-rose-gold")
    }

    @Test
    fun introAnimation() {
        compose.mainClock.autoAdvance = false
        compose.setContent { SalonTheme { IntroSplash(onFinished = {}) } }
        compose.mainClock.advanceTimeBy(950)
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        save("00-intro", bitmap!!)
    }

    private fun dashboard(theme: ColorTheme, dark: Boolean, name: String) {
        backend.user = SignedInUser("u1", "royalcuts@gmail.com", "Ali Raza", null)
        backend.accounts["u1"] = CloudAccount(
            uid = "u1", status = AccountStatus.APPROVED, salonName = "Royal Cuts",
            expiresAtMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30),
        )
        runBlocking {
            container.initialize()
            container.demoDataSeeder.seed()
            container.businessRepository.completeSetup("Royal Cuts", "0300-1234567", "Main Bazar, Burewala", CurrencyConfig(), addDefaultServices = true)
            container.accountManager.refresh()
        }
        showGate(theme, dark)
        // Room queries run on background threads; give the dashboard time to load.
        repeat(5) {
            Thread.sleep(400)
            compose.waitForIdle()
        }
        capture(name)
    }

    @Test
    fun locationRequiredBeforeTheApp() {
        org.robolectric.Shadows.shadowOf(app).denyPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        dashboard(ColorTheme.ROYAL_PURPLE, dark = false, name = "16-location-required")
        compose.onNodeWithText("Allow location").assertExists()
        compose.onNodeWithText("Today").assertDoesNotExist()
    }

    @Test
    fun dashboardRoyalPurpleLight() = dashboard(ColorTheme.ROYAL_PURPLE, dark = false, name = "04-dashboard-royal-purple")

    @Test
    fun dashboardBlackGoldDark() = dashboard(ColorTheme.BLACK_GOLD, dark = true, name = "05-dashboard-black-gold-dark")

    @Test
    fun preferencesScreen() {
        compose.setContent {
            SalonTheme(darkTheme = false, colorTheme = ColorTheme.ROYAL_PURPLE) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AppPreferencesScreen(onBack = {})
                }
            }
        }
        capture("06-preferences")
    }

    @Test
    @Config(qualifiers = "ur-w392dp-h850dp-hdpi")
    fun loginUrdu() {
        startAccounts()
        showGate(ColorTheme.ROYAL_PURPLE, dark = false)
        capture("07-login-urdu")
    }

    @Test
    fun brandedReceipt() {
        val receipt = ReceiptData(
            saleId = 1, businessName = "Royal Cuts Salon", businessPhone = "0300-1234567", businessAddress = "Main Bazar, Burewala",
            logoPath = null, headerNote = "Hair • Beard • Facial", footer = "Shukriya! Phir tashreef layein", currency = CurrencyConfig(),
            showStaff = true, showLogo = false, receiptNumber = "SAL-000123", createdAtMillis = System.currentTimeMillis(),
            customerName = "Ali Raza", customerPhone = "0300-1234567",
            items = listOf(
                ReceiptItem("Hair cut", "Usman", 1, 50_000, 0, 50_000),
                ReceiptItem("Beard styling", "Usman", 1, 30_000, 0, 30_000),
                ReceiptItem("Facial (gold)", "Bilal", 2, 150_000, 20_000, 280_000),
            ),
            subtotalMinor = 380_000, itemDiscountMinor = 20_000, saleDiscountMinor = 0, totalMinor = 360_000,
            paymentMethod = PaymentMethod.CASH, amountTenderedMinor = 400_000, changeMinor = 40_000, isVoided = false, voidReason = null,
        )
        val labels = container.receiptPrinter.labels(receipt.paymentMethod)
        val bitmap = ReceiptImageRenderer(ReceiptImageRenderer.SHARE_WIDTH_PX).render(receipt, labels, logo = null)
        assertTrue(bitmap.height > 1000)
        FileOutputStream(File(outDir, "08-receipt.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The other receipt designs, side by side for review.
        val styles = listOf(ReceiptStyle.MODERN, ReceiptStyle.MINIMAL, ReceiptStyle.ELEGANT)
            .map { ReceiptImageRenderer(480, style = it).render(receipt, labels, logo = null) }
        val sheet = Bitmap.createBitmap(styles.sumOf { it.width } + 40 * (styles.size + 1), styles.maxOf { it.height } + 80, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(sheet).apply {
            drawColor(android.graphics.Color.rgb(0xEE, 0xEB, 0xF3))
            var x = 40f
            styles.forEach { drawBitmap(it, x, 40f, null); x += it.width + 40 }
        }
        FileOutputStream(File(outDir, "10-receipt-designs.jpg")).use { sheet.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        val token = com.dtpos.salonmanager.services.export.TokenImage.render(
            salon = "Royal Cuts Salon", title = "Your token", token = 12, name = "Ali Raza",
            whenText = "05 Oct 2026  ·  4:30 PM", service = "Hair cut + beard", footer = "Please come 5 minutes early. Thank you!",
            accent = ReceiptImageRenderer.DEFAULT_ACCENT,
        )
        FileOutputStream(File(outDir, "11-token.png")).use { token.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { Text("ok") }
    }

    @Test
    fun monoReceiptsAndTokenSlip() {
        val receipt = ReceiptData(
            saleId = 2, businessName = "Classic Cuts Barbershop", businessPhone = "0300-2223344", businessAddress = "45 Main Street, Burewala",
            logoPath = null, headerNote = null, footer = "Walk-ins welcome!\nOpen Tue-Sun 10AM-10PM", currency = CurrencyConfig(),
            showStaff = true, showLogo = false, receiptNumber = "SAL-000124", createdAtMillis = System.currentTimeMillis(),
            customerName = "Marcus Taylor", customerPhone = null,
            items = listOf(
                ReceiptItem("Men's haircut", "Tony", 1, 50_000, 0, 50_000, boldName = true),
                ReceiptItem("Beard trim & shape", "Tony", 1, 30_000, 0, 30_000),
                ReceiptItem("Hot towel shave", "Asif", 1, 25_000, 0, 25_000, boldPrice = true),
            ),
            subtotalMinor = 105_000, itemDiscountMinor = 0, saleDiscountMinor = 5_000, totalMinor = 100_000,
            paymentMethod = PaymentMethod.BANK, amountTenderedMinor = null, changeMinor = 0, isVoided = false, voidReason = null,
            paymentAccountName = "JazzCash", creditMinor = 40_000,
        )
        val labels = container.receiptPrinter.labels(receipt.paymentMethod)
        val designs = listOf(ReceiptStyle.MONO, ReceiptStyle.TABLE).map { ReceiptImageRenderer(384, style = it).render(receipt, labels, logo = null) } +
            ReceiptImageRenderer(576, style = ReceiptStyle.TABLE, forPrinter = true).render(receipt.copy(creditMinor = 0), labels, logo = null)
        designs.forEach { assertTrue(it.height > 500) }
        val sheet = Bitmap.createBitmap(designs.sumOf { it.width } + 40 * (designs.size + 1), designs.maxOf { it.height } + 80, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(sheet).apply {
            drawColor(android.graphics.Color.rgb(0xEE, 0xEB, 0xF3))
            var x = 40f
            designs.forEach { drawBitmap(it, x, 40f, null); x += it.width + 40 }
        }
        FileOutputStream(File(outDir, "12-receipt-mono-designs.jpg")).use { sheet.compress(Bitmap.CompressFormat.JPEG, 85, it) }

        // Token slip exactly as the 58 mm printer gets it.
        val slip = com.dtpos.salonmanager.services.export.TokenImage.render(
            salon = "Royal Cuts Salon", title = "Your token", token = 7, name = "Ali Raza",
            whenText = "05 Oct 2026  ·  4:30 PM", service = "Hair cut", footer = "Please wait for your turn",
            accent = android.graphics.Color.BLACK, width = 384, forPrinter = true,
        )
        assertEquals(384, slip.width)
        FileOutputStream(File(outDir, "13-token-printer-58mm.png")).use { slip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seedSalon() {
        runBlocking {
            container.initialize()
            container.demoDataSeeder.seed()
            container.businessRepository.completeSetup("Royal Cuts", "0300-1234567", "Main Bazar, Burewala", CurrencyConfig(), addDefaultServices = true)
            container.paymentAccountRepository.save(null, "JazzCash", com.dtpos.salonmanager.domain.model.AccountKind.WALLET, null, "0300-1111111")
        }
    }

    @Test
    fun closeDayScreen() {
        seedSalon()
        compose.setContent {
            SalonTheme(darkTheme = false, colorTheme = ColorTheme.ROYAL_PURPLE) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    com.dtpos.salonmanager.presentation.cash.CloseDayScreen(onBack = {})
                }
            }
        }
        repeat(5) {
            Thread.sleep(400)
            compose.waitForIdle()
        }
        capture("14-close-day")
    }

    /** The staff payment slip renders as the preview picture the owner sees before printing / WhatsApp. */
    @Test
    fun staffPaymentSlipPreview() {
        val staff = com.dtpos.salonmanager.data.database.entities.StaffEntity(
            id = 3, businessId = 1, name = "Taimoor", phone = "0300-1234567", createdAt = 0, updatedAt = 0,
        )
        val payment = com.dtpos.salonmanager.data.database.entities.StaffPaymentEntity(
            id = 9, businessId = 1, staffId = 3, amountMinor = 8_800, type = com.dtpos.salonmanager.domain.model.StaffPaymentType.ADVANCE,
            paymentDate = com.dtpos.salonmanager.core.util.DateTimeUtils.today().toEpochDay(), paymentMethod = PaymentMethod.CASH,
            paidFromCounter = true, note = null, createdAt = 0,
        )
        val lines = com.dtpos.salonmanager.services.printer.Slips.staffPayment(
            payment, staff, null, "Burewala Salon", app, com.dtpos.salonmanager.core.util.CurrencyFormatter(CurrencyConfig()),
        )
        val bitmap = com.dtpos.salonmanager.services.printer.ReceiptCanvasRenderer(576, 576 / 17f).renderBitmap(lines, null)
        assertEquals(576, bitmap.width)
        assertTrue(bitmap.height > 200)
        val text = com.dtpos.salonmanager.services.printer.Slips.asText(lines)
        assertTrue(text, text.contains("Taimoor") && text.contains("Rs. 88"))
        FileOutputStream(File(outDir, "28-staff-slip.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { Text("ok") }
    }

    @Test
    fun udhaarPaymentReceipt58mm() {
        val due = com.dtpos.salonmanager.data.database.entities.DueEntity(
            id = 4, businessId = 1, customerId = null, customerName = "Usman Ali", customerPhone = "0300-1112233", amountMinor = 150_000,
            paidMinor = 150_000, note = "SAL-000012", createdAt = 0, settledAt = 1, lastReminderAt = null, saleId = 12,
        )
        val payment = com.dtpos.salonmanager.data.database.entities.DuePaymentEntity(
            id = 2, businessId = 1, dueId = 4, amountMinor = 50_000, paymentMethod = PaymentMethod.BANK, paymentAccountId = 1,
            paymentAccountName = "EasyPaisa", businessDate = 0, createdAt = System.currentTimeMillis(),
        )
        val lines = com.dtpos.salonmanager.services.printer.Slips.duePayment(
            due, payment, 150_000, "Burewala Salon", app, com.dtpos.salonmanager.core.util.CurrencyFormatter(CurrencyConfig()),
        )
        // 58 mm paper: 384 printer dots wide, as in the preview.
        val bitmap = com.dtpos.salonmanager.services.printer.ReceiptCanvasRenderer(384, 384 / 17f).renderBitmap(lines, null)
        assertEquals(384, bitmap.width)
        val text = com.dtpos.salonmanager.services.printer.Slips.asText(lines)
        assertTrue(text, text.contains("Usman Ali") && text.contains("Rs. 500") && text.contains("EasyPaisa") && text.contains("FULLY PAID"))
        FileOutputStream(File(outDir, "29-udhaar-receipt-58mm.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { Text("ok") }
    }

    /** Every receipt and token design exactly as the 58 mm printer gets it (black and white). */
    @Test
    fun printDesigns58mm() {
        val receipt = ReceiptData(
            saleId = 3, businessName = "Burewala", businessPhone = "03451873354", businessAddress = "Chichawatni",
            logoPath = null, headerNote = null, footer = null, currency = CurrencyConfig(), showStaff = true, showLogo = false,
            receiptNumber = "SAL-000002", createdAtMillis = System.currentTimeMillis(), customerName = null, customerPhone = null,
            items = listOf(
                ReceiptItem("Hair Color", "Taimoor", 1, 150_000, 0, 150_000),
                ReceiptItem("Hair Cut", "Taimoor", 1, 50_000, 0, 50_000),
                ReceiptItem("Hair Styling", "Taimoor", 1, 40_000, 0, 40_000),
                ReceiptItem("Kids Hair Cut", "Taimoor", 1, 35_000, 0, 35_000),
                ReceiptItem("Facial", "Taimoor", 1, 150_000, 0, 150_000),
            ),
            subtotalMinor = 425_000, itemDiscountMinor = 0, saleDiscountMinor = 0, totalMinor = 425_000,
            paymentMethod = PaymentMethod.CASH, amountTenderedMinor = null, changeMinor = 0, isVoided = false, voidReason = null,
        )
        val labels = container.receiptPrinter.labels(receipt.paymentMethod)
        fun printed(bitmap: Bitmap): Bitmap {
            val mono = com.dtpos.salonmanager.services.printer.ReceiptCanvasRenderer.toMonochrome(
                bitmap, dither = false, threshold = com.dtpos.salonmanager.services.printer.ReceiptPrinter.PRINT_THRESHOLD,
            )
            val pixels = IntArray(mono.width * mono.height) { i -> if (mono.isBlack(i % mono.width, i / mono.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            return Bitmap.createBitmap(pixels, mono.width, mono.height, Bitmap.Config.ARGB_8888)
        }
        ReceiptStyle.entries.forEach { style ->
            val bitmap = printed(ReceiptImageRenderer(384, forPrinter = true, style = style).render(receipt, labels, logo = null))
            assertEquals(384, bitmap.width)
            FileOutputStream(File(outDir, "30-receipt-58mm-${style.name.lowercase()}.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Two people, a double service with a discount: the case where the table helps most.
        val mixed = receipt.copy(items = receipt.items.take(3) + ReceiptItem("Beard trim and shape", "Ali", 2, 30_000, 5_000, 55_000))
        listOf(ReceiptStyle.PLAIN_TABLE, ReceiptStyle.GRID, ReceiptStyle.CASH_MEMO, ReceiptStyle.BOXES).forEach { style ->
            val bitmap = printed(ReceiptImageRenderer(384, forPrinter = true, style = style).render(mixed, labels, logo = null))
            FileOutputStream(File(outDir, "30-receipt-58mm-${style.name.lowercase()}-mixed.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Text (fast) print as the printer's own 12 x 24 dot font lays it out: 32 letters a line.
        com.dtpos.salonmanager.services.printer.ItemLayout.entries.forEach { layout ->
            val rendered = com.dtpos.salonmanager.services.printer.ReceiptTextRenderer.render(
                com.dtpos.salonmanager.services.printer.ReceiptLayout.build(mixed, labels, items = layout), 32,
            )
            val font = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 20f }
            font.textSize *= 384f / font.measureText("M".repeat(32))
            val height = rendered.sumOf { if (it.large) 54L else 30L }.toInt() + 20
            val bitmap = Bitmap.createBitmap(384, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
            var y = 10f
            rendered.forEach { line ->
                val p = android.graphics.Paint(font).apply {
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, if (line.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    if (line.large) textSize *= 2f
                }
                y += if (line.large) 54f else 30f
                canvas.drawText(line.text, 0f, y - 8f, p)
            }
            FileOutputStream(File(outDir, "32-text-print-58mm-${layout.name.lowercase()}.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        com.dtpos.salonmanager.services.export.TokenStyle.entries.forEach { style ->
            val token = printed(
                com.dtpos.salonmanager.services.export.TokenImage.render(
                    salon = "Burewala", title = "Your token", token = 1, name = "TAIMOOR", whenText = "13 Nov 2026",
                    service = "Beard, Hair Color, Kids Hair Cut, Hair Styling", footer = "Please wait for your number. Thank you!",
                    accent = android.graphics.Color.BLACK, width = 384, forPrinter = true, style = style,
                ),
            )
            FileOutputStream(File(outDir, "31-token-58mm-${style.name.lowercase()}.png")).use { token.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.setContent { Text("ok") }
    }

    /** Salon with today's queue (a walk-in and a booking) and a hand-written udhaar. */
    private fun seedSalonWithQueue() {
        seedSalon()
        runBlocking {
            container.settingsRepository.putBoolean(com.dtpos.salonmanager.data.repository.SettingKeys.TOKENS_ENABLED, true)
            val today = com.dtpos.salonmanager.core.util.DateTimeUtils.today()
            container.bookingRepository.issue(today, "Ali Raza", "03001234567", "Hair cut")
            container.bookingRepository.issue(today, "Bilal Ahmed", null, "Beard styling", timeMinutes = 17 * 60 + 30)
            container.dueRepository.add(null, "Usman Ali", "03001112233", 150_000, "Facial last week")
        }
    }

    /** Renders one real screen with data and saves it; any crash while composing fails the test. */
    private fun screen(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        seedSalonWithQueue()
        compose.setContent {
            SalonTheme(darkTheme = false, colorTheme = ColorTheme.ROYAL_PURPLE) {
                CompositionLocalProvider(LocalAppContainer provides container) { content() }
            }
        }
        repeat(5) {
            Thread.sleep(400)
            compose.waitForIdle()
        }
        capture(name)
    }

    // Small phones (4.7", 320 dp wide) and tablets: the main screens must fit and work.

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneDashboard() = dashboard(ColorTheme.ROYAL_PURPLE, dark = false, name = "20-small-dashboard")

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneNewSale() = screen("21-small-new-sale") {
        com.dtpos.salonmanager.presentation.sales.PosScreen(initialCustomerId = null, onBack = {}, onSaleCompleted = {})
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneReceipt() = screen("22-small-receipt") {
        com.dtpos.salonmanager.presentation.sales.SaleDetailScreen(saleId = 1, isNewSale = false, onBack = {}, onNewSale = {}, onOpenCustomer = {})
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneUdhaar() = screen("23-small-udhaar") { com.dtpos.salonmanager.presentation.messages.DuesScreen(onBack = {}) }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneTokens() = screen("24-small-tokens") { com.dtpos.salonmanager.presentation.tokens.TokensScreen(onBack = {}) }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneMenu() = screen("25-small-menu") {
        com.dtpos.salonmanager.presentation.services.MenuScreen(onBack = {}, onNewSale = {}, onManage = {})
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletDashboard() = dashboard(ColorTheme.ROYAL_PURPLE, dark = false, name = "26-tablet-dashboard")

    @Test
    @Config(qualifiers = TABLET)
    fun tabletNewSale() = screen("27-tablet-new-sale") {
        com.dtpos.salonmanager.presentation.sales.PosScreen(initialCustomerId = null, onBack = {}, onSaleCompleted = {})
    }

    @Test
    fun paymentAccountsScreen() {
        seedSalon()
        compose.setContent {
            SalonTheme(darkTheme = false, colorTheme = ColorTheme.ROYAL_PURPLE) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    com.dtpos.salonmanager.presentation.settings.PaymentAccountsScreen(onBack = {})
                }
            }
        }
        repeat(3) {
            Thread.sleep(300)
            compose.waitForIdle()
        }
        capture("15-payment-accounts")
    }
}
