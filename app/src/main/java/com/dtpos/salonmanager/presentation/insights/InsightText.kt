package com.dtpos.salonmanager.presentation.insights

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.domain.insights.Insight
import com.dtpos.salonmanager.presentation.common.LocalMoney
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class InsightContent(val title: String, val message: String, val icon: ImageVector)

/** Turns a structured [Insight] into translated, human-readable text. */
@Composable
fun Insight.content(): InsightContent {
    val money = LocalMoney.current
    return when (this) {
        is Insight.TargetAchieved -> InsightContent(
            stringResource(R.string.insight_target_achieved_title),
            stringResource(R.string.insight_target_achieved_msg, money.format(targetMinor), money.format(achievedMinor)),
            Icons.Filled.EmojiEvents,
        )
        is Insight.TargetOnTrack -> InsightContent(
            stringResource(R.string.insight_target_on_track_title),
            stringResource(R.string.insight_target_on_track_msg, money.format(projectedMinor), money.format(targetMinor)),
            Icons.Filled.Flag,
        )
        is Insight.TargetBehind -> InsightContent(
            stringResource(R.string.insight_target_behind_title),
            stringResource(R.string.insight_target_behind_msg, money.format(targetMinor), money.format(requiredPerDayMinor), daysLeft),
            Icons.Filled.Flag,
        )
        is Insight.SalesTrend -> InsightContent(
            stringResource(if (changePercent >= 0) R.string.insight_sales_up_title else R.string.insight_sales_down_title),
            stringResource(
                if (changePercent >= 0) R.string.insight_sales_up_msg else R.string.insight_sales_down_msg,
                Percent.formatRatio(abs(changePercent)),
                money.format(currentMinor),
                money.format(previousMinor),
            ),
            if (changePercent >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
        )
        is Insight.ExpenseTrend -> InsightContent(
            stringResource(if (changePercent > 0) R.string.insight_expenses_up_title else R.string.insight_expenses_down_title),
            stringResource(
                if (changePercent > 0) R.string.insight_expenses_up_msg else R.string.insight_expenses_down_msg,
                Percent.formatRatio(abs(changePercent)),
                money.format(currentMinor),
                money.format(previousMinor),
            ),
            if (changePercent > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
        )
        is Insight.BestService -> InsightContent(
            stringResource(R.string.insight_best_service_title),
            stringResource(R.string.insight_best_service_msg, name, money.format(revenueMinor), count),
            Icons.Filled.Star,
        )
        is Insight.TopStaff -> InsightContent(
            stringResource(R.string.insight_top_staff_title),
            stringResource(R.string.insight_top_staff_msg, name, money.format(salesMinor)),
            Icons.Filled.EmojiEvents,
        )
        is Insight.SlowestDay -> InsightContent(
            stringResource(R.string.insight_slow_day_title),
            stringResource(R.string.insight_slow_day_msg, day.getDisplayName(TextStyle.FULL, Locale.getDefault()), money.format(averageMinor), money.format(overallAverageMinor)),
            Icons.Filled.CalendarMonth,
        )
        is Insight.BusiestDay -> InsightContent(
            stringResource(R.string.insight_busy_day_title),
            stringResource(R.string.insight_busy_day_msg, day.getDisplayName(TextStyle.FULL, Locale.getDefault()), money.format(averageMinor)),
            Icons.Filled.CalendarMonth,
        )
        is Insight.PersonalSpendingHigh -> InsightContent(
            stringResource(R.string.insight_personal_high_title),
            stringResource(R.string.insight_personal_high_msg, Percent.formatRatio(percentOfProfit), money.format(personalMinor), money.format(profitMinor)),
            Icons.Filled.Savings,
        )
        is Insight.ReturningCustomers -> InsightContent(
            stringResource(R.string.insight_returning_title),
            stringResource(R.string.insight_returning_msg, Percent.formatRatio(percent), returning, total),
            Icons.Filled.People,
        )
        is Insight.LossWarning -> InsightContent(
            stringResource(R.string.insight_loss_title),
            stringResource(R.string.insight_loss_msg, money.format(lossMinor)),
            Icons.Filled.Warning,
        )
        Insight.NotEnoughData -> InsightContent(
            stringResource(R.string.insight_no_data_title),
            stringResource(R.string.insight_no_data_msg),
            Icons.Filled.Lightbulb,
        )
    }
}
