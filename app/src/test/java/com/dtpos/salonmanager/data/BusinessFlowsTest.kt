package com.dtpos.salonmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.repository.CustomerInput
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.ExpenseInput
import com.dtpos.salonmanager.data.repository.StaffInput
import com.dtpos.salonmanager.data.repository.StaffPaymentInput
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import com.dtpos.salonmanager.domain.model.StaffRole
import com.dtpos.salonmanager.domain.model.TargetPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BusinessFlowsTest {

    private lateinit var env: TestEnvironment
    private val today = DateTimeUtils.today()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(ApplicationProvider.getApplicationContext()).setUp()
    }

    @After
    fun tearDown() {
        env.db.close()
    }

    private suspend fun category(type: ExpenseType, key: String): Long =
        env.expenses.observeCategories(type).first().first { it.systemKey == key }.id

    @Test
    fun `customer history stats and duplicate phone protection`() = runTest {
        val id = (env.customers.save(CustomerInput("Usman", "0300-1112222", Gender.MALE, null, null, null)) as DataResult.Success).data
        val duplicate = env.customers.save(CustomerInput("Other", "+0300 111 2222", Gender.MALE, null, null, null))
        assertEquals(DataResult.Failure(DataError.DUPLICATE_PHONE), duplicate)

        val haircut = env.service("Hair Cut")
        val facial = env.service("Facial")
        env.sales.completeSale(env.request(listOf(env.line(haircut)), id, "Usman"), env.at(today.minusDays(3)))
        env.sales.completeSale(env.request(listOf(env.line(haircut), env.line(facial)), id, "Usman"), env.at(today))

        val stats = env.customers.observeStats(id).first()
        assertEquals(2, stats.visitCount)
        assertEquals(250_000L, stats.totalSpentMinor)
        val visits = env.customers.observeVisits(id).first()
        assertEquals(2, visits.size)
        assertEquals("Hair Cut, Facial", visits.first().servicesSummary) // newest first
        assertEquals("Hair Cut", env.customers.observeFavoriteServices(id).first().first().name)

        // Search by name and by phone digits.
        assertEquals(id, env.customers.search("usm").first().single().customer.id)
        assertEquals(id, env.customers.search("1112").first().single().customer.id)

        // Deleting the customer keeps the receipts (name snapshot) intact.
        env.customers.delete(id)
        assertEquals(2, env.sales.observeSummary(DateRange(today.minusDays(3), today)).first().saleCount)
    }

    @Test
    fun `business and personal expenses stay separate in profit`() = runTest {
        env.sales.completeSale(env.request(listOf(env.line(env.service("Hair Color")))), env.at(today)) // 1,500
        env.expenses.save(ExpenseInput(ExpenseType.BUSINESS, category(ExpenseType.BUSINESS, "supplies"), 40_000, today.toEpochDay(), PaymentMethod.CASH, true, null))
        env.expenses.save(ExpenseInput(ExpenseType.PERSONAL, category(ExpenseType.PERSONAL, "food"), 30_000, today.toEpochDay(), PaymentMethod.CASH, true, null))

        val report = env.reports.buildReport(DateRange.single(today))
        assertEquals(150_000L, report.profit.grossSalesMinor)
        assertEquals(40_000L, report.profit.businessExpensesMinor)
        assertEquals(110_000L, report.profit.businessProfitMinor) // personal NOT subtracted
        assertEquals(30_000L, report.profit.personalExpensesMinor)
        assertEquals(80_000L, report.profit.remainingCashMinor)

        // Category must match the expense type.
        val wrong = env.expenses.save(ExpenseInput(ExpenseType.BUSINESS, category(ExpenseType.PERSONAL, "food"), 1_000, today.toEpochDay(), PaymentMethod.CASH, false, null))
        assertEquals(DataResult.Failure(DataError.INVALID), wrong)
    }

    @Test
    fun `cash counter reconciles sales expenses staff payments and manual movements`() = runTest {
        val staffId = (env.staff.save(StaffInput("Bilal", null, StaffRole.BARBER, SalaryType.FIXED, 0, 2_000_000, true, null)) as DataResult.Success).data
        env.cash.setOpeningCash(today, 200_000)
        env.sales.completeSale(env.request(listOf(env.line(env.service("Hair Cut")))), env.at(today)) // +500
        env.sales.completeSale(env.request(listOf(env.line(env.service("Beard"))), method = PaymentMethod.BANK), env.at(today))
        val expenseId = (env.expenses.save(ExpenseInput(ExpenseType.BUSINESS, category(ExpenseType.BUSINESS, "water"), 10_000, today.toEpochDay(), PaymentMethod.CASH, true, null)) as DataResult.Success).data
        env.staff.recordPayment(StaffPaymentInput(staffId, 20_000, StaffPaymentType.ADVANCE, today.toEpochDay(), PaymentMethod.CASH, true, null))
        env.cash.addManualMovement(today, CashTxType.CASH_OUT, 5_000, "Bank deposit")

        var day = env.cash.observeDay(today).first()
        assertEquals(200_000L + 50_000 - 10_000 - 20_000 - 5_000, day.breakdown.expectedClosingMinor)

        // Editing the expense amount updates the drawer; deleting removes it.
        env.expenses.save(ExpenseInput(ExpenseType.BUSINESS, category(ExpenseType.BUSINESS, "water"), 15_000, today.toEpochDay(), PaymentMethod.CASH, true, null), expenseId)
        day = env.cash.observeDay(today).first()
        assertEquals(15_000L, day.breakdown.cashExpensesMinor)

        val difference = (env.cash.closeDay(today, 210_000, "counted") as DataResult.Success).data
        assertEquals(210_000L - (200_000L + 50_000 - 15_000 - 20_000 - 5_000), difference)
        assertTrue(env.cash.observeDay(today).first().isClosed)
        assertEquals(DataResult.Failure(DataError.ALREADY_CLOSED), env.cash.addManualMovement(today, CashTxType.CASH_IN, 100, null))
        assertEquals(210_000L, env.cash.suggestedOpening(today.plusDays(1)))
    }

    @Test
    fun `staff commission settlement and deletion rules`() = runTest {
        val staffId = (env.staff.save(StaffInput("Ahmed", null, StaffRole.BARBER, SalaryType.FIXED_PLUS_COMMISSION, 1_000, 2_000_000, true, null)) as DataResult.Success).data
        val ahmed = env.staff.get(staffId)!!
        env.sales.completeSale(env.request(listOf(env.line(env.service("Facial"), ahmed))), env.at(today)) // 1,500 -> 10% = 150
        env.staff.recordPayment(StaffPaymentInput(staffId, 500_000, StaffPaymentType.ADVANCE, today.toEpochDay(), PaymentMethod.CASH, false, null))

        val month = DateRange(DateTimeUtils.monthStart(today), DateTimeUtils.monthEnd(today))
        val settlement = env.staff.observeSettlement(staffId, month).first()!!
        assertEquals(2_000_000L, settlement.fixedSalaryMinor)
        assertEquals(15_000L, settlement.commissionEarnedMinor)
        assertEquals(2_015_000L - 500_000L, settlement.outstandingMinor)

        val perf = env.staff.observePerformance(staffId, month).first()
        assertEquals(1, perf.customerCount)
        assertEquals(150_000L, perf.salesMinor)

        // Staff with history cannot be deleted, only deactivated.
        assertEquals(DataResult.Failure(DataError.IN_USE), env.staff.delete(staffId))
        env.staff.setActive(staffId, false)
        assertTrue(env.staff.observeActive().first().none { it.id == staffId })
    }

    @Test
    fun `dashboard shows today numbers and target progress`() = runTest {
        env.targets.setTarget(TargetPeriod.DAILY, 200_000)
        env.sales.completeSale(env.request(listOf(env.line(env.service("Hair Cut")), env.line(env.service("Beard")))), env.at(today))
        env.expenses.save(ExpenseInput(ExpenseType.BUSINESS, category(ExpenseType.BUSINESS, "gas"), 20_000, today.toEpochDay(), PaymentMethod.CASH, true, null))

        val dashboard = env.reports.observeDashboard(today).first()
        assertEquals(80_000L, dashboard.todaySales.totalMinor)
        assertEquals(1, dashboard.todaySales.saleCount)
        assertEquals(2, dashboard.todaySales.serviceCount)
        assertEquals(60_000L, dashboard.todayProfit.businessProfitMinor)
        assertEquals(40.0, dashboard.dailyTarget.achievedPercent, 0.001)
        assertEquals(7, dashboard.last7Days.size)
        assertEquals(80_000L, dashboard.last7Days.last().amountMinor)
    }

    @Test
    fun `service names are unique case insensitively`() = runTest {
        val result = env.services.save(
            com.dtpos.salonmanager.data.repository.ServiceInput("hair cut", "Hair", 60_000, 30, true),
        )
        assertEquals(DataResult.Failure(DataError.DUPLICATE_NAME), result)
    }
}
