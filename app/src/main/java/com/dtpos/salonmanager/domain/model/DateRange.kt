package com.dtpos.salonmanager.domain.model

import com.dtpos.salonmanager.core.util.DateTimeUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Inclusive range of business days. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end must not be before start" }
    }

    val startEpochDay: Long get() = start.toEpochDay()
    val endEpochDay: Long get() = end.toEpochDay()
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun days(): List<LocalDate> = (0 until dayCount).map { start.plusDays(it.toLong()) }

    /** The equally long range immediately before this one (for "vs previous period" comparisons). */
    fun previous(): DateRange = DateRange(start.minusDays(dayCount.toLong()), start.minusDays(1))

    companion object {
        fun single(date: LocalDate) = DateRange(date, date)
    }
}

enum class PeriodPreset { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, CUSTOM }

object Periods {
    fun range(preset: PeriodPreset, today: LocalDate, custom: DateRange? = null): DateRange = when (preset) {
        PeriodPreset.TODAY -> DateRange.single(today)
        PeriodPreset.YESTERDAY -> DateRange.single(today.minusDays(1))
        PeriodPreset.THIS_WEEK -> DateRange(DateTimeUtils.weekStart(today), today)
        PeriodPreset.LAST_WEEK -> {
            val start = DateTimeUtils.weekStart(today).minusWeeks(1)
            DateRange(start, start.plusDays(6))
        }
        PeriodPreset.THIS_MONTH -> DateRange(DateTimeUtils.monthStart(today), today)
        PeriodPreset.LAST_MONTH -> {
            val start = DateTimeUtils.monthStart(today).minusMonths(1)
            DateRange(start, DateTimeUtils.monthEnd(start))
        }
        PeriodPreset.THIS_YEAR -> DateRange(today.withDayOfYear(1), today)
        PeriodPreset.CUSTOM -> custom ?: DateRange.single(today)
    }

    /** Full calendar period that contains [date] for a target period type. */
    fun targetRange(period: TargetPeriod, date: LocalDate): DateRange = when (period) {
        TargetPeriod.DAILY -> DateRange.single(date)
        TargetPeriod.WEEKLY -> DateTimeUtils.weekStart(date).let { DateRange(it, it.plusDays(6)) }
        TargetPeriod.MONTHLY -> DateRange(DateTimeUtils.monthStart(date), DateTimeUtils.monthEnd(date))
    }
}
