package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.database.model.CategoryTotalRow
import com.dtpos.salonmanager.data.database.model.DayTotalRow
import com.dtpos.salonmanager.data.database.model.NamedTotalRow
import com.dtpos.salonmanager.data.database.model.PaymentMethodTotalRow
import com.dtpos.salonmanager.data.database.model.SalesSummaryRow
import com.dtpos.salonmanager.data.database.model.StaffPerformanceRow
import com.dtpos.salonmanager.domain.calc.BudgetCalculator
import com.dtpos.salonmanager.domain.calc.BudgetLine
import com.dtpos.salonmanager.domain.calc.ProfitCalculator
import com.dtpos.salonmanager.domain.calc.ProfitSummary
import com.dtpos.salonmanager.domain.calc.TargetCalculator
import com.dtpos.salonmanager.domain.calc.TargetProgress
import com.dtpos.salonmanager.domain.insights.BusinessSnapshot
import com.dtpos.salonmanager.domain.insights.NamedAmount
import com.dtpos.salonmanager.domain.insights.WeekdayStat
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.domain.model.TargetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

data class TrendPoint(val start: LocalDate, val end: LocalDate, val amountMinor: Long)

enum class TrendGranularity { DAY, WEEK, MONTH }

data class DashboardData(
    val today: LocalDate,
    val todaySales: SalesSummaryRow,
    val todayProfit: ProfitSummary,
    val dailyTarget: TargetProgress,
    val weeklyTarget: TargetProgress,
    val monthlyTarget: TargetProgress,
    val last7Days: List<TrendPoint>,
    val outstandingStaffPayMinor: Long,
    val recentSales: List<SaleEntity>,
)

data class ReportData(
    val range: DateRange,
    val sales: SalesSummaryRow,
    val previousSalesMinor: Long,
    val profit: ProfitSummary,
    val newCustomers: Int,
    val distinctCustomers: Int,
    val paymentBreakdown: List<PaymentMethodTotalRow>,
    val salesTrend: List<TrendPoint>,
    val expenseTrend: List<TrendPoint>,
    val trendGranularity: TrendGranularity,
    val topServices: List<NamedTotalRow>,
    val staffPerformance: List<StaffPerformanceRow>,
    val businessExpensesByCategory: List<CategoryTotalRow>,
    val personalExpensesByCategory: List<CategoryTotalRow>,
) {
    val averageSaleMinor: Long get() = if (sales.saleCount == 0) 0L else sales.totalMinor / sales.saleCount
}

class ReportRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
    private val staffRepository: StaffRepository,
) {
    private val saleDao = db.saleDao()
    private val expenseDao = db.expenseDao()
    private val staffDao = db.staffDao()

    /** Live dashboard numbers; every part re-emits when its underlying table changes. */
    fun observeDashboard(today: LocalDate): Flow<DashboardData> {
        val day = today.toEpochDay()
        val week = Periods.targetRange(TargetPeriod.WEEKLY, today)
        val month = Periods.targetRange(TargetPeriod.MONTHLY, today)
        val last7 = DateRange(today.minusDays(6), today)

        val todayFlow = combine(
            saleDao.observeSummary(businessId, day, day),
            expenseDao.observeTotal(businessId, ExpenseType.BUSINESS, day, day),
            expenseDao.observeTotal(businessId, ExpenseType.PERSONAL, day, day),
            staffDao.observeTotalPaid(businessId, day, day),
        ) { sales, business, personal, staffPaid ->
            sales to ProfitCalculator.summarize(sales.totalMinor, business, staffPaid, personal)
        }

        val targetsFlow = combine(
            db.targetDao().observeAll(businessId),
            saleDao.observeSummary(businessId, week.startEpochDay, week.endEpochDay),
            saleDao.observeSummary(businessId, month.startEpochDay, month.endEpochDay),
        ) { targets, weekSales, monthSales -> Triple(targets.associate { it.period to it.amountMinor }, weekSales, monthSales) }

        return combine(
            todayFlow,
            targetsFlow,
            saleDao.observeDailyTotals(businessId, last7.startEpochDay, last7.endEpochDay),
            staffRepository.observeSettlements(month),
            saleDao.observeRecent(businessId, 5),
        ) { (todaySales, profit), (targets, weekSales, monthSales), daily, settlements, recent ->
            DashboardData(
                today = today,
                todaySales = todaySales,
                todayProfit = profit,
                dailyTarget = TargetCalculator.progress(targets[TargetPeriod.DAILY] ?: 0L, todaySales.totalMinor),
                weeklyTarget = TargetCalculator.progress(targets[TargetPeriod.WEEKLY] ?: 0L, weekSales.totalMinor),
                monthlyTarget = TargetCalculator.progress(targets[TargetPeriod.MONTHLY] ?: 0L, monthSales.totalMinor),
                last7Days = fillDaily(last7, daily),
                outstandingStaffPayMinor = settlements.sumOf { it.settlement.outstandingMinor },
                recentSales = recent,
            )
        }
    }

    suspend fun buildReport(range: DateRange): ReportData {
        val from = range.startEpochDay
        val to = range.endEpochDay
        val sales = saleDao.summary(businessId, from, to)
        val previous = range.previous()
        val previousSales = saleDao.summary(businessId, previous.startEpochDay, previous.endEpochDay).totalMinor
        val business = expenseDao.total(businessId, ExpenseType.BUSINESS, from, to)
        val personal = expenseDao.total(businessId, ExpenseType.PERSONAL, from, to)
        val staffPaid = staffDao.totalPaid(businessId, from, to)
        val granularity = when {
            range.dayCount <= 31 -> TrendGranularity.DAY
            range.dayCount <= 120 -> TrendGranularity.WEEK
            else -> TrendGranularity.MONTH
        }
        return ReportData(
            range = range,
            sales = sales,
            previousSalesMinor = previousSales,
            profit = ProfitCalculator.summarize(sales.totalMinor, business, staffPaid, personal),
            newCustomers = db.customerDao().countCreatedBetween(
                businessId,
                DateTimeUtils.startOfDayMillis(range.start),
                DateTimeUtils.endOfDayMillis(range.end),
            ),
            distinctCustomers = saleDao.distinctCustomers(businessId, from, to),
            paymentBreakdown = saleDao.paymentBreakdown(businessId, from, to),
            salesTrend = bucket(range, saleDao.dailyTotals(businessId, from, to), granularity),
            expenseTrend = bucket(range, expenseDao.dailyTotals(businessId, ExpenseType.BUSINESS, from, to), granularity),
            trendGranularity = granularity,
            topServices = saleDao.topServices(businessId, from, to, 10),
            staffPerformance = staffDao.observePerformance(businessId, from, to).first(),
            businessExpensesByCategory = expenseDao.totalsByCategory(businessId, ExpenseType.BUSINESS, from, to),
            personalExpensesByCategory = expenseDao.totalsByCategory(businessId, ExpenseType.PERSONAL, from, to),
        )
    }

    /** Numbers the insight engine looks at (month to date vs the same days last month). */
    suspend fun buildSnapshot(today: LocalDate): BusinessSnapshot {
        val month = DateRange(DateTimeUtils.monthStart(today), today)
        val previousMonthStart = month.start.minusMonths(1)
        val previousMonthEnd = minOf(
            previousMonthStart.plusDays((today.dayOfMonth - 1).toLong()),
            DateTimeUtils.monthEnd(previousMonthStart),
        )
        val previous = DateRange(previousMonthStart, previousMonthEnd)

        val weekStart = DateTimeUtils.weekStart(today)
        val weekdayRange = DateRange(weekStart.minusWeeks(8), weekStart.minusDays(1))
        val weekdayStats = saleDao.dailyTotals(businessId, weekdayRange.startEpochDay, weekdayRange.endEpochDay)
            .filter { it.totalMinor > 0 }
            .groupBy { LocalDate.ofEpochDay(it.day).dayOfWeek }
            .mapValues { (_, days) -> WeekdayStat(days.sumOf { it.totalMinor }, days.size) }

        val sixtyDays = DateRange(today.minusDays(59), today)
        val target = db.targetDao().get(businessId, TargetPeriod.MONTHLY)?.amountMinor ?: 0L
        val monthSales = saleDao.summary(businessId, month.startEpochDay, month.endEpochDay)

        return BusinessSnapshot(
            today = today,
            monthlyTargetMinor = target,
            monthToDateSalesMinor = monthSales.totalMinor,
            previousMonthSamePeriodSalesMinor = saleDao.summary(businessId, previous.startEpochDay, previous.endEpochDay).totalMinor,
            monthToDateBusinessExpensesMinor = expenseDao.total(businessId, ExpenseType.BUSINESS, month.startEpochDay, month.endEpochDay),
            previousMonthSamePeriodBusinessExpensesMinor = expenseDao.total(businessId, ExpenseType.BUSINESS, previous.startEpochDay, previous.endEpochDay),
            monthToDateStaffPaymentsMinor = staffDao.totalPaid(businessId, month.startEpochDay, month.endEpochDay),
            monthToDatePersonalExpensesMinor = expenseDao.total(businessId, ExpenseType.PERSONAL, month.startEpochDay, month.endEpochDay),
            topServices = saleDao.topServices(businessId, month.startEpochDay, month.endEpochDay, 3)
                .map { NamedAmount(it.name, it.totalMinor, it.quantity) },
            topStaff = staffDao.observePerformance(businessId, month.startEpochDay, month.endEpochDay).first()
                .filter { it.staffId != null }
                .map { NamedAmount(it.staffName.orEmpty(), it.salesMinor, it.customerCount) },
            salesByWeekday = weekdayStats,
            customersLast60Days = saleDao.distinctCustomers(businessId, sixtyDays.startEpochDay, sixtyDays.endEpochDay),
            returningCustomersLast60Days = saleDao.returningCustomers(businessId, sixtyDays.startEpochDay, sixtyDays.endEpochDay),
            salesCountMonthToDate = monthSales.saleCount,
        )
    }

    suspend fun buildBudget(month: DateRange, budgets: Map<BudgetGroup, Long>): List<BudgetLine> {
        val from = month.startEpochDay
        val to = month.endEpochDay
        val sales = saleDao.summary(businessId, from, to).totalMinor
        val businessExpenses = expenseDao.total(businessId, ExpenseType.BUSINESS, from, to)
        val staffPaid = staffDao.totalPaid(businessId, from, to)
        val personalTotal = expenseDao.total(businessId, ExpenseType.PERSONAL, from, to)
        val personalByGroup = expenseDao.totalsByBudgetGroup(businessId, ExpenseType.PERSONAL, from, to)
            .mapNotNull { row -> row.budgetGroup?.let { it to row.totalMinor } }
            .toMap()
        val profit = ProfitCalculator.summarize(sales, businessExpenses, staffPaid, personalTotal)
        return BudgetCalculator.lines(
            budgets = budgets,
            businessExpensesMinor = businessExpenses + staffPaid,
            personalByGroup = personalByGroup,
            personalTotalMinor = personalTotal,
            businessProfitMinor = profit.businessProfitMinor,
        )
    }

    companion object {
        fun fillDaily(range: DateRange, rows: List<DayTotalRow>): List<TrendPoint> {
            val byDay = rows.associate { it.day to it.totalMinor }
            return range.days().map { TrendPoint(it, it, byDay[it.toEpochDay()] ?: 0L) }
        }

        fun bucket(range: DateRange, rows: List<DayTotalRow>, granularity: TrendGranularity): List<TrendPoint> {
            val daily = fillDaily(range, rows)
            return when (granularity) {
                TrendGranularity.DAY -> daily
                TrendGranularity.WEEK -> daily.groupBy { DateTimeUtils.weekStart(it.start) }
                    .map { (_, points) -> TrendPoint(points.first().start, points.last().end, points.sumOf { it.amountMinor }) }
                TrendGranularity.MONTH -> daily.groupBy { DateTimeUtils.monthStart(it.start) }
                    .map { (_, points) -> TrendPoint(points.first().start, points.last().end, points.sumOf { it.amountMinor }) }
            }
        }
    }
}
