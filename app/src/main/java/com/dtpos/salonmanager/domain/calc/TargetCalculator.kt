package com.dtpos.salonmanager.domain.calc

import com.dtpos.salonmanager.core.util.Percent
import java.math.BigDecimal
import java.math.RoundingMode

data class TargetProgress(
    val targetMinor: Long,
    val achievedMinor: Long,
) {
    val hasTarget: Boolean get() = targetMinor > 0L
    val remainingMinor: Long get() = (targetMinor - achievedMinor).coerceAtLeast(0L)
    val achievedPercent: Double get() = Percent.ratio(achievedMinor, targetMinor)
    val isAchieved: Boolean get() = hasTarget && achievedMinor >= targetMinor

    /** 0f..1f for progress bars (capped at 100%). */
    val fraction: Float get() = if (!hasTarget) 0f else (achievedMinor.toFloat() / targetMinor.toFloat()).coerceIn(0f, 1f)
}

object TargetCalculator {

    fun progress(targetMinor: Long, achievedMinor: Long) = TargetProgress(targetMinor.coerceAtLeast(0L), achievedMinor)

    /**
     * Average sales needed per remaining day (including today) to reach the target.
     * E.g. target 500,000, achieved 350,000 with 20 days left -> 7,500 per day.
     */
    fun requiredPerDay(progress: TargetProgress, daysLeftIncludingToday: Int): Long {
        if (progress.remainingMinor == 0L || daysLeftIncludingToday <= 0) return 0L
        return BigDecimal.valueOf(progress.remainingMinor)
            .divide(BigDecimal.valueOf(daysLeftIncludingToday.toLong()), 0, RoundingMode.CEILING)
            .toLong()
    }

    /** Straight-line projection of the period total from the pace so far. */
    fun projectedTotal(achievedMinor: Long, daysElapsed: Int, totalDays: Int): Long {
        if (daysElapsed <= 0 || totalDays <= 0) return achievedMinor
        return BigDecimal.valueOf(achievedMinor)
            .multiply(BigDecimal.valueOf(totalDays.toLong()))
            .divide(BigDecimal.valueOf(daysElapsed.toLong()), 0, RoundingMode.HALF_UP)
            .toLong()
    }
}
