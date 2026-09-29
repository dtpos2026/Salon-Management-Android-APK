package com.dtpos.salonmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.repository.CustomerInput
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.StaffInput
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.SaleDiscount
import com.dtpos.salonmanager.domain.model.SaleStatus
import com.dtpos.salonmanager.domain.model.StaffRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SaleTransactionTest {

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

    private suspend fun addStaff(name: String, commissionBps: Int = 3_000): Long =
        (env.staff.save(StaffInput(name, null, StaffRole.BARBER, SalaryType.COMMISSION, commissionBps, 0, true, null)) as DataResult.Success).data

    @Test
    fun `completing a sale writes sale items visit receipt and cash atomically`() = runTest {
        val staffId = addStaff("Ahmed")
        val ahmed = env.staff.get(staffId)!!
        val customerId = (env.customers.save(CustomerInput("Ali Khan", "0301-5550000", Gender.MALE, null, null, null)) as DataResult.Success).data
        val haircut = env.service("Hair Cut")
        val beard = env.service("Beard")

        val result = env.sales.completeSale(
            env.request(listOf(env.line(haircut, ahmed), env.line(beard, ahmed, quantity = 2)), customerId, "Ali Khan"),
            atMillis = env.at(today),
        )
        val saleId = (result as DataResult.Success).data
        val stored = env.sales.observeSale(saleId).first()!!

        assertEquals("SAL-000001", stored.sale.receiptNumber)
        assertEquals(110_000L, stored.sale.totalMinor) // 500 + 2 x 300
        assertEquals(2, stored.items.size)
        assertEquals(33_000L, stored.items.sumOf { it.commissionMinor }) // 30% of 1,100
        assertEquals(today.toEpochDay(), stored.sale.businessDate)

        val visit = env.db.saleDao().getVisitForSale(saleId)!!
        assertEquals(customerId, visit.customerId)
        assertEquals("Hair Cut, Beard x2", visit.servicesSummary)

        val cashTotals = env.db.cashDao().totalsByType(env.businessId, today.toEpochDay())
        assertEquals(110_000L, cashTotals.single { it.type == CashTxType.SALE }.totalMinor)
    }

    @Test
    fun `receipt numbers are sequential and unique even after the prefix changes`() = runTest {
        val haircut = env.service("Hair Cut")
        val numbers = mutableListOf<String>()
        repeat(5) {
            val id = (env.sales.completeSale(env.request(listOf(env.line(haircut))), env.at(today)) as DataResult.Success).data
            numbers += env.sales.buildReceipt(id)!!.receiptNumber
        }
        env.business.updateReceiptSettings("BRB", null, null, showLogo = true, showStaff = true)
        val id = (env.sales.completeSale(env.request(listOf(env.line(haircut))), env.at(today)) as DataResult.Success).data
        numbers += env.sales.buildReceipt(id)!!.receiptNumber

        assertEquals(listOf("SAL-000001", "SAL-000002", "SAL-000003", "SAL-000004", "SAL-000005", "BRB-000006"), numbers)
        assertEquals(numbers.size, numbers.toSet().size)
        assertEquals("BRB-000007", env.business.nextReceiptNumberPreview())
    }

    @Test
    fun `a failing sale leaves no partial data behind`() = runTest {
        val haircut = env.service("Hair Cut")
        // Staff id 999 does not exist -> the foreign key fails while inserting the items.
        val broken = env.line(haircut).copy(staffId = 999L, staffName = "Ghost")
        val result = env.sales.completeSale(env.request(listOf(broken)), env.at(today))

        assertTrue(result is DataResult.Failure)
        assertEquals(0, env.sales.observeSummary(DateRange.single(today)).first().saleCount)
        assertTrue(env.db.cashDao().totalsByType(env.businessId, today.toEpochDay()).isEmpty())
        assertNull(env.db.saleDao().maxReceiptSequence(env.businessId))
        // The receipt counter was rolled back too, so the next sale is still number 1.
        val ok = env.sales.completeSale(env.request(listOf(env.line(haircut))), env.at(today)) as DataResult.Success
        assertEquals("SAL-000001", env.sales.buildReceipt(ok.data)!!.receiptNumber)
    }

    @Test
    fun `invalid carts are rejected before touching the database`() = runTest {
        val haircut = env.service("Hair Cut")
        assertEquals(DataResult.Failure(DataError.INVALID), env.sales.completeSale(env.request(emptyList()), env.at(today)))
        val tooMuchDiscount = env.request(listOf(env.line(haircut)), discount = SaleDiscount.amount(1_000_000))
        assertEquals(DataResult.Failure(DataError.INVALID), env.sales.completeSale(tooMuchDiscount, env.at(today)))
        assertEquals(0, env.sales.observeSummary(DateRange.single(today)).first().saleCount)
    }

    @Test
    fun `voided sales are excluded from totals and refunded in the cash counter`() = runTest {
        val haircut = env.service("Hair Cut")
        val keep = (env.sales.completeSale(env.request(listOf(env.line(haircut))), env.at(today)) as DataResult.Success).data
        val void = (env.sales.completeSale(env.request(listOf(env.line(haircut))), env.at(today)) as DataResult.Success).data
        assertEquals(DataResult.Success(Unit), env.sales.voidSale(void, "Wrong entry"))
        assertEquals(DataResult.Failure(DataError.ALREADY_CLOSED), env.sales.voidSale(void, null))

        val summary = env.sales.observeSummary(DateRange.single(today)).first()
        assertEquals(1, summary.saleCount)
        assertEquals(50_000L, summary.totalMinor)
        assertEquals(SaleStatus.VOIDED, env.db.saleDao().getSale(void)!!.status)
        assertEquals(SaleStatus.COMPLETED, env.db.saleDao().getSale(keep)!!.status)

        val cash = env.cash.observeDay(today).first().breakdown
        assertEquals(100_000L, cash.cashSalesMinor)
        assertEquals(50_000L, cash.voidRefundsMinor)
        assertEquals(50_000L, cash.expectedClosingMinor)
    }

    @Test
    fun `read only licence blocks new sales`() = runTest {
        val readOnly = com.dtpos.salonmanager.data.repository.SaleRepository(env.db, env.businessId) { true }
        val result = readOnly.completeSale(env.request(listOf(env.line(env.service("Hair Cut")))), env.at(today))
        assertEquals(DataResult.Failure(DataError.READ_ONLY), result)
    }

    @Test
    fun `card sales do not touch the cash drawer`() = runTest {
        env.sales.completeSale(env.request(listOf(env.line(env.service("Facial"))), method = PaymentMethod.CARD), env.at(today))
        val day = env.cash.observeDay(today).first()
        assertEquals(0L, day.breakdown.cashSalesMinor)
        assertTrue(day.transactions.none { it.referenceType == CashTransactionEntity.REF_SALE })
        assertEquals(150_000L, env.sales.observeSummary(DateRange.single(today)).first().totalMinor)
    }
}
