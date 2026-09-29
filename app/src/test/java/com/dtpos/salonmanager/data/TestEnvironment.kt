package com.dtpos.salonmanager.data

import android.content.Context
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.repository.BusinessRepository
import com.dtpos.salonmanager.data.repository.CashRepository
import com.dtpos.salonmanager.data.repository.CustomerRepository
import com.dtpos.salonmanager.data.repository.ExpenseRepository
import com.dtpos.salonmanager.data.repository.ReportRepository
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.data.repository.ServiceRepository
import com.dtpos.salonmanager.data.repository.SettingsRepository
import com.dtpos.salonmanager.data.repository.StaffRepository
import com.dtpos.salonmanager.data.repository.TargetRepository
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.NewSaleRequest
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SaleDiscount
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/** Wires every repository against one database, exactly like AppContainer does. */
class TestEnvironment(context: Context, val db: SalonDatabase = SalonDatabase.inMemory(context)) {
    val businessId = 1L
    val settings = SettingsRepository(db.settingsDao())
    val business = BusinessRepository(db, businessId)
    val customers = CustomerRepository(db, businessId)
    val services = ServiceRepository(db, businessId)
    val staff = StaffRepository(db, businessId)
    val sales = SaleRepository(db, businessId)
    val expenses = ExpenseRepository(db, businessId)
    val cash = CashRepository(db, businessId)
    val targets = TargetRepository(db, businessId)
    val reports = ReportRepository(db, businessId, staff)

    suspend fun setUp(): TestEnvironment {
        business.ensureInitialized()
        business.completeSetup("Royal Barber Shop", "0300-1234567", "Main Bazaar", CurrencyConfig(), addDefaultServices = true)
        return this
    }

    suspend fun service(name: String): ServiceEntity = services.observeActive().first().first { it.name == name }

    fun line(service: ServiceEntity, staff: StaffEntity? = null, quantity: Int = 1, discountMinor: Long = 0) = CartLine(
        key = UUID.randomUUID().toString(),
        serviceId = service.id,
        serviceName = service.name,
        staffId = staff?.id,
        staffName = staff?.name,
        unitPriceMinor = service.priceMinor,
        quantity = quantity,
        discountMinor = discountMinor,
        commissionBps = if (staff != null && staff.salaryType.hasCommission) staff.commissionBps else 0,
    )

    fun request(
        lines: List<CartLine>,
        customerId: Long? = null,
        customerName: String? = null,
        method: PaymentMethod = PaymentMethod.CASH,
        discount: SaleDiscount = SaleDiscount.NONE,
    ) = NewSaleRequest(customerId, customerName, null, lines, discount, method, null, null)

    /** 10:00 on the given day, so sales land on a predictable business date. */
    fun at(date: LocalDate): Long = DateTimeUtils.startOfDayMillis(date) + 10 * 60 * 60 * 1000L
}
