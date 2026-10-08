package com.dtpos.salonmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.DueRepository
import com.dtpos.salonmanager.data.repository.PaymentAccountRepository
import com.dtpos.salonmanager.data.repository.StaffInput
import com.dtpos.salonmanager.domain.model.AccountKind
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Payment accounts, udhaar (credit) sales, receipt correction and Close Day. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountsCreditCloseTest {
    private val env = TestEnvironment(ApplicationProvider.getApplicationContext())
    private val accounts = PaymentAccountRepository(env.db, env.businessId)
    private val dues = DueRepository(env.db, env.businessId)
    private val today = DateTimeUtils.today()
    private val day get() = DateRange.single(today)

    @Before
    fun setUp() = runTest { env.setUp() }

    private suspend fun cashOf(saleId: Long): Long =
        env.cash.observeDay(today).first().transactions
            .filter { it.referenceType == CashTransactionEntity.REF_SALE && it.referenceId == saleId }
            .sumOf { it.amountMinor }

    @Test
    fun `account sale is reported under the account name`() = runTest {
        val jazz = (accounts.save(null, "JazzCash", AccountKind.WALLET, "Ali Salon", "0300-1111111") as DataResult.Success).data
        val request = env.request(listOf(env.line(env.service("Hair Cut"))), method = PaymentAccountRepository.methodFor(AccountKind.WALLET))
            .copy(paymentAccountId = jazz, paymentAccountName = "JazzCash")
        val saleId = (env.sales.completeSale(request, env.at(today)) as DataResult.Success).data

        val received = env.sales.observeReceived(day).first()
        assertEquals("JazzCash", received.single().accountName)
        assertEquals(50_000L, received.single().totalMinor)
        assertEquals(0L, cashOf(saleId))
        assertEquals("JazzCash", env.sales.buildReceipt(saleId)!!.paymentAccountName)

        // Used accounts are hidden, not deleted, so history keeps the name.
        accounts.delete(jazz)
        assertTrue(accounts.observeActive().first().isEmpty())
        assertEquals(1, accounts.observeAll().first().size)
    }

    @Test
    fun `credit sale puts only the paid part in the drawer and opens a due`() = runTest {
        val customer = (env.customers.save(com.dtpos.salonmanager.data.repository.CustomerInput("Bilal", "0300-2222222", com.dtpos.salonmanager.domain.model.Gender.MALE, null, null, null)) as DataResult.Success).data
        val request = env.request(listOf(env.line(env.service("Hair Cut"))), customerId = customer, customerName = "Bilal")
            .copy(customerPhone = "0300-2222222", creditMinor = 30_000)
        val saleId = (env.sales.completeSale(request, env.at(today)) as DataResult.Success).data

        assertEquals(20_000L, cashOf(saleId))
        val summary = env.sales.observeSummary(day).first()
        assertEquals(50_000L, summary.totalMinor)
        assertEquals(20_000L, summary.cashMinor)
        assertEquals(30_000L, summary.creditMinor)
        val due = dues.observeOpen().first().single()
        assertEquals(saleId, due.saleId)
        assertEquals(30_000L, due.amountMinor)
        assertEquals("Bilal", due.customerName)
        val receipt = env.sales.buildReceipt(saleId)!!
        assertEquals(30_000L, receipt.creditMinor)
        assertEquals(20_000L, receipt.paidMinor)

        // Udhaar without a customer name is refused; more credit than the bill too.
        val anonymous = env.request(listOf(env.line(env.service("Beard")))).copy(creditMinor = 10_000)
        assertEquals(DataResult.Failure(DataError.INVALID), env.sales.completeSale(anonymous, env.at(today)))
        val tooMuch = request.copy(creditMinor = 60_000)
        assertEquals(DataResult.Failure(DataError.INVALID), env.sales.completeSale(tooMuch, env.at(today)))

        // Voiding refunds only the paid part and removes the unpaid due.
        env.sales.voidSale(saleId, "wrong customer")
        assertEquals(0L, cashOf(saleId))
        assertTrue(dues.observeOpen().first().isEmpty())
    }

    @Test
    fun `editing a receipt updates totals cash and udhaar`() = runTest {
        val haircut = env.service("Hair Cut")
        val request = env.request(listOf(env.line(haircut)), customerName = "Hamza")
        val saleId = (env.sales.completeSale(request, env.at(today)) as DataResult.Success).data
        val number = env.sales.buildReceipt(saleId)!!.receiptNumber

        // Wrong price punched: correct to 400 and leave 100 as udhaar.
        val corrected = request.copy(lines = listOf(env.line(haircut).copy(unitPriceMinor = 40_000)), creditMinor = 10_000)
        assertEquals(DataResult.Success(saleId), env.sales.updateSale(saleId, corrected, env.at(today) + 60_000))

        val receipt = env.sales.buildReceipt(saleId)!!
        assertEquals(number, receipt.receiptNumber)
        assertEquals(40_000L, receipt.totalMinor)
        assertEquals(30_000L, cashOf(saleId))
        assertEquals(10_000L, dues.observeOpen().first().single().amountMinor)
        assertNotNull(env.sales.saleWithItems(saleId)!!.sale.editedAt)

        // Paid in full again: the unpaid due goes away.
        env.sales.updateSale(saleId, corrected.copy(creditMinor = 0), env.at(today) + 120_000)
        assertTrue(dues.observeOpen().first().isEmpty())
        assertEquals(40_000L, cashOf(saleId))
    }

    @Test
    fun `after close day new sales count for the next day`() = runTest {
        val owner = (env.staff.save(StaffInput("Owner Sb", null, StaffRole.OWNER, SalaryType.FIXED, 0, 0, true, null)) as DataResult.Success).data
        val barber = (env.staff.save(StaffInput("Asif", null, StaffRole.BARBER, SalaryType.COMMISSION, 2_000, 0, true, null)) as DataResult.Success).data
        env.sales.completeSale(env.request(listOf(env.line(env.service("Hair Cut"), env.staff.get(owner)))), env.at(today))
        env.sales.completeSale(env.request(listOf(env.line(env.service("Facial"), env.staff.get(barber)))), env.at(today))

        val report = env.reports.buildDayClose(today)
        assertEquals(200_000L, report.sales.totalMinor)
        assertEquals(2, report.sales.saleCount)
        assertEquals(50_000L, report.ownerWorkMinor)
        assertEquals(30_000L, report.commissionMinor) // 20% of 1,500
        assertEquals(200_000L, report.cash.expectedClosingMinor)

        env.cash.closeDay(today, 200_000, null)
        assertEquals(today.plusDays(1), env.cash.observeBusinessDay(today).first())
        val late = (env.sales.completeSale(env.request(listOf(env.line(env.service("Beard")))), env.at(today) + 60_000) as DataResult.Success).data
        assertEquals(today.plusDays(1).toEpochDay(), env.sales.saleWithItems(late)!!.sale.businessDate)
        assertEquals(200_000L, env.reports.buildDayClose(today).sales.totalMinor)
        assertEquals(30_000L, env.reports.buildDayClose(today.plusDays(1)).sales.totalMinor)

        env.cash.reopenDay(today)
        assertEquals(today, env.cash.observeBusinessDay(today).first())
    }

    @Test
    fun `udhaar received in cash goes to the drawer and the report`() = runTest {
        val id = (dues.add(null, "Kamran", null, 80_000, null) as DataResult.Success).data
        dues.recordPayment(id, 50_000, intoCashDrawer = true)
        val drawer = env.cash.observeDay(today).first()
        assertEquals(50_000L, drawer.breakdown.cashInMinor)
        assertEquals(CashTxType.CASH_IN, drawer.transactions.single().type)
        assertEquals(50_000L, env.reports.buildDayClose(today).duesCollectedMinor)
        // Without the cash option nothing reaches the drawer.
        dues.recordPayment(id, 10_000)
        assertEquals(50_000L, env.cash.observeDay(today).first().breakdown.cashInMinor)
        assertNull(dues.observeOpen().first().single().settledAt)
    }

    @Test
    fun `udhaar paid online goes to the account, never to the cash drawer`() = runTest {
        val jazz = (accounts.save(null, "JazzCash", AccountKind.WALLET, null, "0300-1111111") as DataResult.Success).data
        val id = (dues.add(null, "Bilal", null, 100_000, "SAL-000009") as DataResult.Success).data

        // Partial online, then a cash payment, then the rest online: three payments, one bill.
        assertEquals(DataResult.Success(70_000L), dues.receivePayment(id, 30_000, PaymentMethod.BANK, jazz))
        assertEquals(DataResult.Success(50_000L), dues.receivePayment(id, 20_000, PaymentMethod.CASH, null))
        assertEquals(DataResult.Success(0L), dues.receivePayment(id, 50_000, PaymentMethod.BANK, jazz))

        // Cash drawer only has the cash part.
        val drawer = env.cash.observeDay(today).first()
        assertEquals(20_000L, drawer.breakdown.cashInMinor)
        assertEquals(1, drawer.transactions.size)

        // Close Day and the dashboard show each place once; sales are untouched.
        val close = env.reports.buildDayClose(today)
        assertEquals(20_000L, close.duesCollectedMinor)
        assertEquals(80_000L, close.duesCollected.single { it.accountId == jazz }.totalMinor)
        assertEquals("JazzCash", close.duesCollected.single { it.accountId == jazz }.accountName)
        assertEquals(20_000L, close.duesCollected.single { it.paymentMethod == PaymentMethod.CASH }.totalMinor)
        assertEquals(0L, close.sales.totalMinor)
        val summary = env.reports.observePeriod(day).first()
        assertEquals(100_000L, summary.duesCollected.sumOf { it.totalMinor })
        assertTrue(summary.received.isEmpty())

        // History has all three, newest first; the bill is settled and paid in full.
        val history = dues.observePayments(id).first()
        assertEquals(listOf(50_000L, 20_000L, 30_000L).sorted(), history.map { it.amountMinor }.sorted())
        assertEquals(3, history.size)
        val settled = dues.observeSettled().first().single()
        assertEquals(100_000L, settled.paidMinor)
        assertNotNull(settled.settledAt)
        // A settled bill takes no more money (no double counting).
        assertEquals(DataResult.Failure(DataError.INVALID), dues.receivePayment(id, 1_000, PaymentMethod.CASH, null))
        assertEquals(3, dues.observePayments(id).first().size)
    }

    @Test
    fun `each udhaar payment has a receipt with what was still due after it`() = runTest {
        val jazz = (accounts.save(null, "JazzCash", AccountKind.WALLET, null, "0300-1111111") as DataResult.Success).data
        val id = (dues.add(null, "Bilal", "0300-2222222", 100_000, "SAL-000009") as DataResult.Success).data
        dues.receivePayment(id, 30_000, PaymentMethod.BANK, jazz)
        dues.receivePayment(id, 20_000, PaymentMethod.CASH, null)
        val payments = dues.observePayments(id).first().sortedBy { it.id }

        // The latest receipt (shown right after saving): paid 50,000 of 100,000.
        val latest = dues.receiptOf(id, null)!!
        assertEquals(payments.last().id, latest.payment.id)
        assertEquals(50_000L, latest.paidSoFarMinor)
        assertEquals(50_000L, latest.balanceAfterMinor)
        // An earlier receipt shows the bill as it was then.
        val first = dues.receiptOf(id, payments.first().id)!!
        assertEquals(30_000L, first.payment.amountMinor)
        assertEquals("JazzCash", first.payment.paymentAccountName)
        assertEquals(30_000L, first.paidSoFarMinor)
        assertEquals(70_000L, first.balanceAfterMinor)

        // The printed slip: customer, amount, how, and the balance; FULLY PAID after the last one.
        val res = ApplicationProvider.getApplicationContext<Application>()
        val money = com.dtpos.salonmanager.core.util.CurrencyFormatter()
        val partText = com.dtpos.salonmanager.services.printer.Slips.asText(
            com.dtpos.salonmanager.services.printer.Slips.duePayment(first.due, first.payment, first.paidSoFarMinor, "Royal Cuts", res, money),
        )
        assertTrue(partText, partText.contains("Bilal") && partText.contains("Rs. 300") && partText.contains("JazzCash") && partText.contains("700"))
        dues.receivePayment(id, 50_000, PaymentMethod.CASH, null)
        val last = dues.receiptOf(id, null)!!
        assertEquals(0L, last.balanceAfterMinor)
        val fullText = com.dtpos.salonmanager.services.printer.Slips.asText(
            com.dtpos.salonmanager.services.printer.Slips.duePayment(last.due, last.payment, last.paidSoFarMinor, "Royal Cuts", res, money),
        )
        assertTrue(fullText, fullText.contains(res.getString(com.dtpos.salonmanager.R.string.due_slip_full)))
        // No payment yet: no receipt.
        val fresh = (dues.add(null, "Ali", null, 5_000, null) as DataResult.Success).data
        assertNull(dues.receiptOf(fresh, null))
    }

    @Test
    fun `udhaar payment edge cases`() = runTest {
        val id = (dues.add(null, "Kamran", null, 50_000, null) as DataResult.Success).data
        assertEquals(DataResult.Failure(DataError.INVALID), dues.receivePayment(id, 0, PaymentMethod.CASH, null))
        assertEquals(DataResult.Failure(DataError.INVALID), dues.receivePayment(id, -5, PaymentMethod.CASH, null))
        assertEquals(DataResult.Failure(DataError.NOT_FOUND), dues.receivePayment(id, 1_000, PaymentMethod.BANK, 9_999))
        assertEquals(DataResult.Failure(DataError.NOT_FOUND), dues.receivePayment(9_999, 1_000, PaymentMethod.CASH, null))
        assertTrue(dues.observePayments(id).first().isEmpty())
        // Online without an account (the salon has none yet): recorded, not in the drawer.
        assertEquals(DataResult.Success(40_000L), dues.receivePayment(id, 10_000, PaymentMethod.BANK, null))
        assertEquals(0L, env.cash.observeDay(today).first().breakdown.cashInMinor)
        // More than the balance: only the balance is recorded.
        assertEquals(DataResult.Success(0L), dues.receivePayment(id, 90_000, PaymentMethod.CASH, null))
        assertEquals(40_000L, env.cash.observeDay(today).first().breakdown.cashInMinor)
        assertEquals(50_000L, dues.observePayments(id).first().sumOf { it.amountMinor })
    }

    @Test
    fun `udhaar paid after the day is closed counts for the next day`() = runTest {
        val id = (dues.add(null, "Usman", null, 30_000, null) as DataResult.Success).data
        env.cash.closeDay(today, 0, null)
        dues.receivePayment(id, 30_000, PaymentMethod.CASH, null)
        val payment = dues.observePayments(id).first().single()
        assertEquals(today.plusDays(1).toEpochDay(), payment.businessDate)
        assertTrue(env.reports.buildDayClose(today).duesCollected.isEmpty())
        assertEquals(30_000L, env.reports.buildDayClose(today.plusDays(1)).duesCollected.single().totalMinor)
    }

    @Test
    fun `period summary splits cash online and udhaar`() = runTest {
        val bank = (accounts.save(null, "Meezan", AccountKind.BANK, null, null) as DataResult.Success).data
        env.sales.completeSale(env.request(listOf(env.line(env.service("Hair Cut")))), env.at(today))
        env.sales.completeSale(
            env.request(listOf(env.line(env.service("Facial"))), method = PaymentMethod.BANK).copy(paymentAccountId = bank, paymentAccountName = "Meezan"),
            env.at(today),
        )
        val summary = env.reports.observePeriod(day).first()
        assertEquals(200_000L, summary.sales.totalMinor)
        assertEquals(50_000L, summary.sales.cashMinor)
        assertEquals(150_000L, summary.received.single { it.accountName == "Meezan" }.totalMinor)
    }
}
