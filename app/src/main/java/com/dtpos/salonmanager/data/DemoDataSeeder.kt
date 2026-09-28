package com.dtpos.salonmanager.data

import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.repository.CashRepository
import com.dtpos.salonmanager.data.repository.CustomerInput
import com.dtpos.salonmanager.data.repository.CustomerRepository
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.ExpenseInput
import com.dtpos.salonmanager.data.repository.ExpenseRepository
import com.dtpos.salonmanager.data.repository.SaleRepository
import com.dtpos.salonmanager.data.repository.ServiceRepository
import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.data.repository.SettingsRepository
import com.dtpos.salonmanager.data.repository.StaffInput
import com.dtpos.salonmanager.data.repository.StaffPaymentInput
import com.dtpos.salonmanager.data.repository.StaffRepository
import com.dtpos.salonmanager.data.repository.TargetRepository
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.NewSaleRequest
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.SaleDiscount
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import com.dtpos.salonmanager.domain.model.StaffRole
import com.dtpos.salonmanager.domain.model.TargetPeriod
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Optional DEMO data so a new owner can explore every screen. Only runs when the owner picks
 * "Explore with demo data" during setup; the dashboard then shows a demo badge and all data
 * can be erased from Settings. Never used for a real salon.
 */
class DemoDataSeeder(
    private val settings: SettingsRepository,
    private val services: ServiceRepository,
    private val staff: StaffRepository,
    private val customers: CustomerRepository,
    private val sales: SaleRepository,
    private val expenses: ExpenseRepository,
    private val cash: CashRepository,
    private val targets: TargetRepository,
) {
    suspend fun seed(today: LocalDate = DateTimeUtils.today()) {
        val random = Random(2026)
        services.addDefaultServices()
        val serviceList = services.observeActive().first()
        if (serviceList.isEmpty()) return

        val staffIds = listOf(
            StaffInput("Ahmed Raza", "0300-1112233", StaffRole.BARBER, SalaryType.COMMISSION, 4_000, 0, true, null),
            StaffInput("Bilal Hussain", "0301-2223344", StaffRole.BARBER, SalaryType.FIXED_PLUS_COMMISSION, 1_000, 2_500_000, true, null),
            StaffInput("Sana Iqbal", "0302-3334455", StaffRole.BEAUTICIAN, SalaryType.FIXED, 0, 3_000_000, true, null),
        ).mapNotNull { (staff.save(it) as? DataResult.Success)?.data }
        val staffList = staffIds.mapNotNull { staff.get(it) }

        val names = listOf(
            "Ali Khan", "Usman Tariq", "Hamza Sheikh", "Zain Abbas", "Fahad Malik", "Imran Qureshi", "Saad Butt",
            "Ayesha Noor", "Hira Aslam", "Omer Farooq", "Kashif Mehmood", "Danish Ali", "Rizwan Ahmed", "Yasir Nawaz",
        )
        val customerIds = names.mapIndexedNotNull { i, name ->
            val input = CustomerInput(
                name = name,
                phone = "03%02d-%07d".format(10 + i, 1_000_000 + i * 7_919),
                gender = if (name in setOf("Ayesha Noor", "Hira Aslam")) Gender.FEMALE else Gender.MALE,
                dateOfBirthEpochDay = null,
                address = null,
                notes = null,
            )
            (customers.save(input) as? DataResult.Success)?.data
        }

        targets.setTarget(TargetPeriod.DAILY, 15_000_00)
        targets.setTarget(TargetPeriod.WEEKLY, 100_000_00)
        targets.setTarget(TargetPeriod.MONTHLY, 400_000_00)

        for (daysAgo in 29 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            cash.setOpeningCash(date, 2_000_00)
            val count = if (date.dayOfWeek.value >= 6) random.nextInt(10, 16) else random.nextInt(4, 11)
            val limit = if (daysAgo == 0) LocalTime.now().hour.coerceIn(10, 21) else 21
            repeat(count) {
                val time = LocalTime.of(random.nextInt(10, limit.coerceAtLeast(11)), random.nextInt(0, 60))
                val at = DateTimeUtils.startOfDayMillis(date) + time.toSecondOfDay() * 1000L
                val member = staffList[random.nextInt(staffList.size)]
                val picks = serviceList.shuffled(random).take(if (random.nextInt(10) < 7) 1 else 2)
                val lines = picks.mapIndexed { i, s ->
                    CartLine(
                        key = "$daysAgo-$it-$i",
                        serviceId = s.id,
                        serviceName = s.name,
                        staffId = member.id,
                        staffName = member.name,
                        unitPriceMinor = s.priceMinor,
                        quantity = 1,
                        commissionBps = if (member.salaryType.hasCommission) member.commissionBps else 0,
                    )
                }
                val customerId = if (random.nextInt(10) < 7 && customerIds.isNotEmpty()) customerIds[random.nextInt(customerIds.size)] else null
                val customer = customerId?.let { customers.get(it) }
                val method = when (random.nextInt(10)) {
                    in 0..6 -> PaymentMethod.CASH
                    7, 8 -> PaymentMethod.BANK
                    else -> PaymentMethod.CARD
                }
                sales.completeSale(
                    NewSaleRequest(
                        customerId = customerId,
                        customerName = customer?.name,
                        customerPhone = customer?.phone,
                        lines = lines,
                        discount = if (random.nextInt(10) == 0) SaleDiscount.amount(50_00) else SaleDiscount.NONE,
                        paymentMethod = method,
                        amountTenderedMinor = null,
                        note = null,
                    ),
                    atMillis = at,
                )
            }
            addDemoExpenses(date, random)
            if (daysAgo > 0) {
                val state = cash.observeDay(date).first()
                val drift = if (random.nextInt(8) == 0) -random.nextInt(1, 5) * 100_00L else 0L
                cash.closeDay(date, (state.breakdown.expectedClosingMinor + drift).coerceAtLeast(0), null)
            }
        }

        staffList.firstOrNull()?.let {
            staff.recordPayment(
                StaffPaymentInput(it.id, 5_000_00, StaffPaymentType.ADVANCE, today.minusDays(10).toEpochDay(), PaymentMethod.CASH, true, null),
            )
        }
        settings.putBoolean(SettingKeys.DEMO_DATA, true)
    }

    private suspend fun addDemoExpenses(date: LocalDate, random: Random) {
        val business = expenses.observeCategories(ExpenseType.BUSINESS).first().associateBy { it.systemKey }
        val personal = expenses.observeCategories(ExpenseType.PERSONAL).first().associateBy { it.systemKey }
        suspend fun add(type: ExpenseType, categoryId: Long?, amount: Long, method: PaymentMethod = PaymentMethod.CASH) {
            categoryId ?: return
            expenses.save(ExpenseInput(type, categoryId, amount, date.toEpochDay(), method, method == PaymentMethod.CASH, null))
        }
        if (date.dayOfMonth == 1) add(ExpenseType.BUSINESS, business["rent"]?.id, 60_000_00, PaymentMethod.BANK)
        if (date.dayOfMonth == 10) add(ExpenseType.BUSINESS, business["electricity"]?.id, 14_500_00, PaymentMethod.BANK)
        if (date.dayOfWeek.value == 1) add(ExpenseType.BUSINESS, business["supplies"]?.id, random.nextLong(1_500, 4_000) * 100)
        if (random.nextInt(4) == 0) add(ExpenseType.BUSINESS, business["cosmetics"]?.id, random.nextLong(500, 2_500) * 100)
        if (random.nextInt(3) == 0) add(ExpenseType.PERSONAL, personal["food"]?.id, random.nextLong(800, 2_500) * 100)
        if (date.dayOfMonth == 5) add(ExpenseType.PERSONAL, personal["education"]?.id, 12_000_00, PaymentMethod.BANK)
    }
}
