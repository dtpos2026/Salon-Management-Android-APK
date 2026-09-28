package com.dtpos.salonmanager.domain.insights

import java.time.DayOfWeek
import java.time.LocalDate

/*
 * AI Insights architecture
 * ------------------------
 * The app ships with [RuleBasedInsightEngine]: deterministic, explainable and 100% offline.
 * A future AI provider (on-device model or an online LLM) implements [InsightEngine] and is
 * plugged in through [CompositeInsightEngine], which always falls back to the offline engine.
 * AI is therefore optional and can never block daily operation.
 */

enum class InsightSeverity { POSITIVE, INFO, WARNING }

data class NamedAmount(val name: String, val amountMinor: Long, val count: Int = 0)

data class WeekdayStat(val totalMinor: Long, val dayCount: Int) {
    val averageMinor: Long get() = if (dayCount == 0) 0L else totalMinor / dayCount
}

/** Aggregated numbers an insight engine may look at. Produced by the repository layer. */
data class BusinessSnapshot(
    val today: LocalDate,
    val monthlyTargetMinor: Long,
    val monthToDateSalesMinor: Long,
    val previousMonthSamePeriodSalesMinor: Long,
    val monthToDateBusinessExpensesMinor: Long,
    val previousMonthSamePeriodBusinessExpensesMinor: Long,
    val monthToDateStaffPaymentsMinor: Long,
    val monthToDatePersonalExpensesMinor: Long,
    val topServices: List<NamedAmount>,
    val topStaff: List<NamedAmount>,
    /** Sales per weekday over the last few complete weeks. */
    val salesByWeekday: Map<DayOfWeek, WeekdayStat>,
    val customersLast60Days: Int,
    val returningCustomersLast60Days: Int,
    val salesCountMonthToDate: Int,
) {
    val monthToDateProfitMinor: Long
        get() = monthToDateSalesMinor - monthToDateBusinessExpensesMinor - monthToDateStaffPaymentsMinor
}

sealed interface Insight {
    val severity: InsightSeverity

    data class TargetAchieved(val targetMinor: Long, val achievedMinor: Long) : Insight {
        override val severity = InsightSeverity.POSITIVE
    }

    data class TargetOnTrack(val targetMinor: Long, val projectedMinor: Long) : Insight {
        override val severity = InsightSeverity.POSITIVE
    }

    data class TargetBehind(
        val targetMinor: Long,
        val remainingMinor: Long,
        val requiredPerDayMinor: Long,
        val daysLeft: Int,
    ) : Insight {
        override val severity = InsightSeverity.WARNING
    }

    data class SalesTrend(val changePercent: Double, val currentMinor: Long, val previousMinor: Long) : Insight {
        override val severity = if (changePercent >= 0) InsightSeverity.POSITIVE else InsightSeverity.WARNING
    }

    data class ExpenseTrend(val changePercent: Double, val currentMinor: Long, val previousMinor: Long) : Insight {
        override val severity = if (changePercent > 0) InsightSeverity.WARNING else InsightSeverity.POSITIVE
    }

    data class BestService(val name: String, val revenueMinor: Long, val count: Int) : Insight {
        override val severity = InsightSeverity.INFO
    }

    data class TopStaff(val name: String, val salesMinor: Long) : Insight {
        override val severity = InsightSeverity.INFO
    }

    data class SlowestDay(val day: DayOfWeek, val averageMinor: Long, val overallAverageMinor: Long) : Insight {
        override val severity = InsightSeverity.INFO
    }

    data class BusiestDay(val day: DayOfWeek, val averageMinor: Long) : Insight {
        override val severity = InsightSeverity.INFO
    }

    data class PersonalSpendingHigh(val percentOfProfit: Double, val personalMinor: Long, val profitMinor: Long) : Insight {
        override val severity = InsightSeverity.WARNING
    }

    data class ReturningCustomers(val percent: Double, val returning: Int, val total: Int) : Insight {
        override val severity = if (percent >= 40.0) InsightSeverity.POSITIVE else InsightSeverity.INFO
    }

    data class LossWarning(val lossMinor: Long) : Insight {
        override val severity = InsightSeverity.WARNING
    }

    data object NotEnoughData : Insight {
        override val severity = InsightSeverity.INFO
    }
}

fun interface InsightEngine {
    suspend fun generate(snapshot: BusinessSnapshot): List<Insight>
}

/**
 * Uses an optional [primary] engine (future AI) and falls back to the offline [fallback]
 * whenever the primary is absent, fails or returns nothing.
 */
class CompositeInsightEngine(
    private val fallback: InsightEngine,
    private val primary: InsightEngine? = null,
) : InsightEngine {
    override suspend fun generate(snapshot: BusinessSnapshot): List<Insight> {
        val fromPrimary = primary?.let { engine ->
            try {
                engine.generate(snapshot)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
        return fromPrimary?.takeIf { it.isNotEmpty() } ?: fallback.generate(snapshot)
    }
}
