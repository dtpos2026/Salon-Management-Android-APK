package com.dtpos.salonmanager.core.di

import android.app.Application
import com.dtpos.salonmanager.BuildConfig
import com.dtpos.salonmanager.data.DemoDataSeeder
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.repository.BusinessRepository
import com.dtpos.salonmanager.data.repository.CashRepository
import com.dtpos.salonmanager.data.repository.CustomerRepository
import com.dtpos.salonmanager.data.repository.DueRepository
import com.dtpos.salonmanager.data.repository.ExpenseRepository
import com.dtpos.salonmanager.data.repository.ReportRepository
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.data.repository.ServiceRepository
import com.dtpos.salonmanager.data.repository.SettingsRepository
import com.dtpos.salonmanager.data.repository.StaffRepository
import com.dtpos.salonmanager.data.repository.TargetRepository
import com.dtpos.salonmanager.domain.insights.CompositeInsightEngine
import com.dtpos.salonmanager.domain.insights.InsightEngine
import com.dtpos.salonmanager.domain.insights.RuleBasedInsightEngine
import com.dtpos.salonmanager.services.backup.BackupManager
import com.dtpos.salonmanager.services.branding.LogoStore
import com.dtpos.salonmanager.services.export.DataExporter
import com.dtpos.salonmanager.services.export.PdfExporter
import com.dtpos.salonmanager.services.account.AccountBackend
import com.dtpos.salonmanager.services.account.AccountCache
import com.dtpos.salonmanager.services.account.AccountManager
import com.dtpos.salonmanager.services.account.FirebaseAccountBackend
import com.dtpos.salonmanager.services.license.LicenseConfig
import com.dtpos.salonmanager.services.license.LicenseManager
import com.dtpos.salonmanager.services.printer.BluetoothPrinterService
import com.dtpos.salonmanager.services.printer.PrinterSettingsStore
import com.dtpos.salonmanager.services.prefs.SoundEffects
import com.dtpos.salonmanager.services.prefs.UiPreferences
import com.dtpos.salonmanager.services.printer.ReceiptPrinter
import com.dtpos.salonmanager.services.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manual dependency injection (no DI framework needed for an app of this size).
 * Everything is created lazily so app start stays fast.
 */
class AppContainer(
    private val app: Application,
    /** Online account backend; tests pass a fake, the app uses Firebase. */
    accountBackend: AccountBackend? = null,
) {

    /** Version 1 is single-salon; every repository is already scoped by this id. */
    val businessId: Long = DEFAULT_BUSINESS_ID

    /** Application context (safe to keep; used for strings in exports). */
    val context: android.content.Context get() = app

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dbLock = Any()
    @Volatile private var db: SalonDatabase? = null

    val database: SalonDatabase
        get() = db ?: synchronized(dbLock) { db ?: SalonDatabase.build(app).also { db = it } }

    fun closeDatabase() = synchronized(dbLock) {
        db?.close()
        db = null
    }

    private val _ready = MutableStateFlow(false)
    /** True once the database is initialised and the licence evaluated. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val settingsRepository by lazy { SettingsRepository(database.settingsDao()) }
    val businessRepository by lazy { BusinessRepository(database, businessId) }
    val customerRepository by lazy { CustomerRepository(database, businessId) }
    val dueRepository by lazy { DueRepository(database, businessId) }
    val bookingRepository by lazy { com.dtpos.salonmanager.data.repository.BookingRepository(database, businessId) }
    val supportChat by lazy { com.dtpos.salonmanager.services.support.SupportChat(app) }
    val aiAssistant by lazy { com.dtpos.salonmanager.services.ai.AiAssistant(app) }
    val serviceRepository by lazy { ServiceRepository(database, businessId) }
    val staffRepository by lazy { StaffRepository(database, businessId) }
    val expenseRepository by lazy { ExpenseRepository(database, businessId) }
    val cashRepository by lazy { CashRepository(database, businessId) }
    val targetRepository by lazy { TargetRepository(database, businessId) }
    val saleRepository by lazy { SaleRepository(database, businessId) { licenseManager.state.value.isReadOnly } }
    val reportRepository by lazy { ReportRepository(database, businessId, staffRepository) }
    val paymentAccountRepository by lazy { com.dtpos.salonmanager.data.repository.PaymentAccountRepository(database, businessId) }

    val logoStore by lazy { LogoStore(app) }
    val serviceImageStore by lazy { com.dtpos.salonmanager.services.branding.ServiceImageStore(app) }

    /** Appearance, colour theme, language and sound preferences (read at startup, so not lazy). */
    val uiPreferences = UiPreferences(app)
    val soundEffects by lazy { SoundEffects(app, uiPreferences) }

    /** Email sign-in + account and phone approval. Only the account lives online; salon data stays in Room. */
    private val accountBackendOverride = accountBackend

    val accountManager by lazy {
        AccountManager(
            context = app,
            backend = accountBackendOverride ?: FirebaseAccountBackend(app),
            cache = AccountCache(app),
            scope = appScope,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
    val securityManager by lazy { SecurityManager(settingsRepository, appScope) }
    val licenseManager by lazy {
        LicenseManager(
            context = app,
            dao = database.licenseDao(),
            config = LicenseConfig(
                enforced = BuildConfig.ENFORCE_LICENSE,
                trialDays = BuildConfig.TRIAL_DAYS,
                publicKeyBase64 = BuildConfig.LICENSE_PUBLIC_KEY,
            ),
        )
    }

    val printerSettingsStore by lazy { PrinterSettingsStore(settingsRepository) }
    val bluetoothPrinterService by lazy { BluetoothPrinterService(app) }
    val lanPrinterService by lazy { com.dtpos.salonmanager.services.printer.LanPrinterService() }
    val receiptPrinter by lazy {
        ReceiptPrinter(app, bluetoothPrinterService, lanPrinterService, printerSettingsStore, saleRepository, logoStore)
    }

    val receiptExporter by lazy {
        com.dtpos.salonmanager.services.export.ReceiptExporter(app, receiptPrinter, logoStore, uiPreferences)
    }

    val backupManager by lazy {
        BackupManager(app, { database }, ::closeDatabase, settingsRepository, logoStore)
    }
    val pdfExporter by lazy { PdfExporter(app) }
    val dataExporter by lazy {
        DataExporter(app, database, businessId, customerRepository, staffRepository, expenseRepository)
    }

    /** Offline rule engine today; an optional AI engine can be passed as `primary` later. */
    val insightEngine: InsightEngine by lazy { CompositeInsightEngine(fallback = RuleBasedInsightEngine(), primary = null) }

    val demoDataSeeder by lazy {
        DemoDataSeeder(
            settingsRepository, serviceRepository, staffRepository, customerRepository,
            saleRepository, expenseRepository, cashRepository, targetRepository,
        )
    }

    /** Daily sales totals for the Super Admin (numbers only; see SalesSync). */
    val salesSync by lazy {
        com.dtpos.salonmanager.services.account.SalesSync(app, { database }, businessId, accountManager, appScope, BuildConfig.VERSION_NAME)
    }

    /** Phone status for the Super Admin's phone list and map (see DeviceMonitor). */
    val deviceMonitor by lazy {
        com.dtpos.salonmanager.services.account.DeviceMonitor(app, accountManager, appScope, BuildConfig.VERSION_NAME)
    }

    suspend fun initialize() {
        try {
            accountManager.start()
        } catch (e: Exception) {
            // The gate falls back to "not configured"; the salon data is unaffected.
        }
        try {
            salesSync.start()
            deviceMonitor.start()
        } catch (e: Exception) {
            // Sharing totals and phone status is best effort; it never blocks the salon.
        }
        try {
            businessRepository.ensureInitialized()
            securityManager // start observing security settings early
            licenseManager.refresh()
        } finally {
            _ready.value = true
        }
        try {
            backupManager.runAutoBackupIfDue()
        } catch (e: Exception) {
            // Auto backup is best-effort; never block app start.
        }
    }

    /**
     * Factory reset: saves a safety backup, then wipes every table except the licence and
     * recreates the defaults. Used from Settings > Erase all data.
     */
    suspend fun eraseAllData(): Boolean {
        backupManager.createLocalBackup("before-erase") ?: return false
        withContext(Dispatchers.IO) {
            val license = database.licenseDao().get()
            database.clearAllTables()
            license?.let { database.licenseDao().put(it) }
        }
        logoStore.delete()
        businessRepository.ensureInitialized()
        securityManager.lockNow()
        return true
    }

    companion object {
        const val DEFAULT_BUSINESS_ID = 1L
    }
}
