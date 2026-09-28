package com.dtpos.salonmanager.domain.insights

import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.domain.calc.ProfitCalculator
import com.dtpos.salonmanager.domain.calc.TargetCalculator
import kotlin.math.abs

/** Offline, explainable business insights computed from local data only. */
class RuleBasedInsightEngine : InsightEngine {

    override suspend fun generate(snapshot: BusinessSnapshot): List<Insight> = buildList {
        addAll(targetInsights(snapshot))

        ProfitCalculator.changePercent(snapshot.monthToDateSalesMinor, snapshot.previousMonthSamePeriodSalesMinor)
            ?.takeIf { abs(it) >= MIN_TREND_PERCENT }
            ?.let { add(Insight.SalesTrend(it, snapshot.monthToDateSalesMinor, snapshot.previousMonthSamePeriodSalesMinor)) }

        ProfitCalculator.changePercent(
            snapshot.monthToDateBusinessExpensesMinor,
            snapshot.previousMonthSamePeriodBusinessExpensesMinor,
        )?.takeIf { abs(it) >= MIN_TREND_PERCENT }?.let {
            add(
                Insight.ExpenseTrend(
                    it,
                    snapshot.monthToDateBusinessExpensesMinor,
                    snapshot.previousMonthSamePeriodBusinessExpensesMinor,
                ),
            )
        }

        val profit = snapshot.monthToDateProfitMinor
        if (snapshot.monthToDateSalesMinor > 0 && profit < 0) add(Insight.LossWarning(-profit))

        if (profit > 0 && snapshot.monthToDatePersonalExpensesMinor > 0) {
            val share = Percent.ratio(snapshot.monthToDatePersonalExpensesMinor, profit)
            if (share >= PERSONAL_SPENDING_WARNING_PERCENT) {
                add(Insight.PersonalSpendingHigh(share, snapshot.monthToDatePersonalExpensesMinor, profit))
            }
        }

        snapshot.topServices.firstOrNull()?.let { add(Insight.BestService(it.name, it.amountMinor, it.count)) }
        snapshot.topStaff.firstOrNull()?.takeIf { snapshot.topStaff.size > 1 }?.let {
            add(Insight.TopStaff(it.name, it.amountMinor))
        }

        addAll(weekdayInsights(snapshot))

        if (snapshot.customersLast60Days >= MIN_CUSTOMERS_FOR_RETENTION) {
            val percent = Percent.ratio(snapshot.returningCustomersLast60Days.toLong(), snapshot.customersLast60Days.toLong())
            add(Insight.ReturningCustomers(percent, snapshot.returningCustomersLast60Days, snapshot.customersLast60Days))
        }

        if (isEmpty()) add(Insight.NotEnoughData)
    }

    private fun targetInsights(s: BusinessSnapshot): List<Insight> {
        if (s.monthlyTargetMinor <= 0) return emptyList()
        val progress = TargetCalculator.progress(s.monthlyTargetMinor, s.monthToDateSalesMinor)
        if (progress.isAchieved) return listOf(Insight.TargetAchieved(progress.targetMinor, progress.achievedMinor))

        val daysInMonth = s.today.lengthOfMonth()
        val daysElapsed = s.today.dayOfMonth
        val daysLeft = daysInMonth - daysElapsed + 1
        val projected = TargetCalculator.projectedTotal(s.monthToDateSalesMinor, daysElapsed, daysInMonth)
        return if (daysElapsed >= 3 && projected >= progress.targetMinor) {
            listOf(Insight.TargetOnTrack(progress.targetMinor, projected))
        } else {
            listOf(
                Insight.TargetBehind(
                    targetMinor = progress.targetMinor,
                    remainingMinor = progress.remainingMinor,
                    requiredPerDayMinor = TargetCalculator.requiredPerDay(progress, daysLeft),
                    daysLeft = daysLeft,
                ),
            )
        }
    }

    private fun weekdayInsights(s: BusinessSnapshot): List<Insight> {
        val stats = s.salesByWeekday.filterValues { it.dayCount > 0 }
        if (stats.size < 4) return emptyList()
        val overallAverage = stats.values.sumOf { it.totalMinor } / stats.values.sumOf { it.dayCount }.coerceAtLeast(1)
        val slowest = stats.minByOrNull { it.value.averageMinor } ?: return emptyList()
        val busiest = stats.maxByOrNull { it.value.averageMinor } ?: return emptyList()
        if (slowest.key == busiest.key || overallAverage <= 0) return emptyList()
        val result = mutableListOf<Insight>(Insight.BusiestDay(busiest.key, busiest.value.averageMinor))
        // Only call out a slow day when it is clearly below average (at least 25% lower).
        if (slowest.value.averageMinor * 4 <= overallAverage * 3) {
            result += Insight.SlowestDay(slowest.key, slowest.value.averageMinor, overallAverage)
        }
        return result
    }

    companion object {
        const val MIN_TREND_PERCENT = 5.0
        const val PERSONAL_SPENDING_WARNING_PERCENT = 60.0
        const val MIN_CUSTOMERS_FOR_RETENTION = 10
    }
}
