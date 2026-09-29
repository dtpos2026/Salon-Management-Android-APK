package com.dtpos.salonmanager

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.repository.BusinessRepository
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.ReportRepository
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.data.repository.ServiceRepository
import com.dtpos.salonmanager.data.repository.StaffRepository
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.NewSaleRequest
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SaleDiscount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real queries on the device's own SQLite version (Android 8 ships a much older SQLite
 * than current phones), covering sales, reports and the dashboard aggregates.
 */
@RunWith(AndroidJUnit4::class)
class DeviceDatabaseTest {
    private val db = SalonDatabase.inMemory(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = db.close()

    @Test
    fun salesReportsAndDashboardWorkOnDeviceSqlite() = runTest {
        val business = BusinessRepository(db, 1L)
        business.ensureInitialized()
        business.completeSetup("Device Test Salon", null, null, CurrencyConfig(), addDefaultServices = true)
        val staff = StaffRepository(db, 1L)
        val sales = SaleRepository(db, 1L)
        val reports = ReportRepository(db, 1L, staff)
        val haircut = ServiceRepository(db, 1L).observeActive().first().first { it.name == "Hair Cut" }
        val today = DateTimeUtils.today()

        repeat(3) {
            val result = sales.completeSale(
                NewSaleRequest(
                    customerId = null, customerName = null, customerPhone = null,
                    lines = listOf(CartLine("k$it", haircut.id, haircut.name, null, null, haircut.priceMinor, 1)),
                    discount = SaleDiscount.NONE, paymentMethod = PaymentMethod.CASH, amountTenderedMinor = null, note = null,
                ),
            )
            assertTrue(result is DataResult.Success)
        }

        val report = reports.buildReport(DateRange.single(today))
        assertEquals(3, report.sales.saleCount)
        assertEquals(haircut.priceMinor * 3, report.profit.grossSalesMinor)
        assertEquals(3, reports.observeDashboard(today).first().todaySales.saleCount)
        reports.buildSnapshot(today)
        assertEquals("SAL-000004", business.nextReceiptNumberPreview())
    }
}
