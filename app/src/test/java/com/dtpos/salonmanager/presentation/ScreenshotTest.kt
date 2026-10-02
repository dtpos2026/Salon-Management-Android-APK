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
import com.dtpos.salonmanager.services.printer.ReceiptLabels
import kotlinx.coroutines.runBlocking
import org.junit.After
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
        compose.setContent { Text("ok") }
    }
}
