package com.dtpos.salonmanager.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.data.repository.ReportData
import com.dtpos.salonmanager.data.repository.TrendGranularity
import com.dtpos.salonmanager.domain.calc.ProfitCalculator
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.components.BarChart
import com.dtpos.salonmanager.presentation.components.ChartBar
import com.dtpos.salonmanager.presentation.components.ChoiceChips
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.ShareBars
import com.dtpos.salonmanager.presentation.components.StatCard
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.export.ShareHelper
import java.time.format.TextStyle
import java.util.Locale

private val REPORT_PRESETS = listOf(
    PeriodPreset.TODAY, PeriodPreset.YESTERDAY, PeriodPreset.THIS_WEEK, PeriodPreset.LAST_WEEK,
    PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.THIS_YEAR, PeriodPreset.CUSTOM,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onOpenInsights: () -> Unit) {
    val vm = appViewModel { ReportsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pickRange by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)
    val shareTitle = stringResource(R.string.share_report)
    LaunchedEffect(vm) { vm.shareFile.collect { ShareHelper.shareFile(context, it, "application/pdf", shareTitle) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    Scaffold(
        topBar = {
            SalonTopBar(
                stringResource(R.string.nav_reports),
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh)) }
                    IconButton(onClick = vm::exportPdf, enabled = state.report != null && !state.exporting) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = stringResource(R.string.report_export_pdf))
                    }
                    IconButton(onClick = { csvLauncher.launch(vm.csvFileName()) }, enabled = !state.exporting) {
                        Icon(Icons.Filled.TableView, contentDescription = stringResource(R.string.report_export_csv))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ChoiceChips(
                    options = REPORT_PRESETS,
                    selected = state.preset,
                    label = { stringResource(it.labelRes) },
                    onSelect = { if (it == PeriodPreset.CUSTOM) pickRange = true else vm.onPreset(it) },
                )
                Text(
                    if (state.range.dayCount == 1) DateTimeUtils.formatDate(state.range.start)
                    else "${DateTimeUtils.formatDate(state.range.start)} – ${DateTimeUtils.formatDate(state.range.end)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.loading || state.exporting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            val report = state.report
            if (report == null) {
                item { if (state.loading) LoadingState(Modifier.height(200.dp)) }
            } else {
                reportContent(report, onOpenInsights)
            }
        }
    }

    if (pickRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = DateTimeUtils.toPickerMillis(state.range.start),
            initialSelectedEndDateMillis = DateTimeUtils.toPickerMillis(state.range.end),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.let(DateTimeUtils::fromPickerMillis)
                        val end = pickerState.selectedEndDateMillis?.let(DateTimeUtils::fromPickerMillis) ?: start
                        if (start != null && end != null && !end.isBefore(start)) vm.onCustomRange(DateRange(start, end))
                        pickRange = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(520.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportContent(report: ReportData, onOpenInsights: () -> Unit) {
    item { SummaryCards(report) }
    item { ProfitCard(report) }
    item { TrendCard(report) }
    item { CustomersCard(report) }
    item {
        ContentCard {
            Row {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SalonTheme.extended.gold)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenInsights) { Text(stringResource(R.string.action_open)) }
            }
            Text(stringResource(R.string.report_insights_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (report.sales.saleCount == 0 && report.profit.totalBusinessCostsMinor == 0L && report.profit.personalExpensesMinor == 0L) {
        item {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = stringResource(R.string.report_empty_title),
                message = stringResource(R.string.report_empty_message),
            )
        }
        return
    }
    if (report.paymentBreakdown.isNotEmpty()) {
        item {
            val money = LocalMoney.current
            ContentCard {
                Text(stringResource(R.string.report_payment_methods), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ShareBars(report.paymentBreakdown.map { stringResource(it.paymentMethod.labelRes) to it.totalMinor }, { money.format(it) })
            }
        }
    }
    if (report.topServices.isNotEmpty()) {
        item {
            val money = LocalMoney.current
            ContentCard {
                Text(stringResource(R.string.report_top_services), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                report.topServices.forEachIndexed { index, row ->
                    LabeledValueRow("${index + 1}. ${row.name} (${row.quantity})", money.format(row.totalMinor))
                }
            }
        }
    }
    if (report.staffPerformance.isNotEmpty()) {
        item { StaffCard(report) }
    }
    if (report.businessExpensesByCategory.isNotEmpty()) {
        item {
            val money = LocalMoney.current
            ContentCard {
                Text(stringResource(R.string.report_business_expense_categories), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ShareBars(report.businessExpensesByCategory.map { it.name to it.totalMinor }, { money.format(it) }, color = SalonTheme.extended.negative)
            }
        }
    }
    if (report.personalExpensesByCategory.isNotEmpty()) {
        item {
            val money = LocalMoney.current
            ContentCard {
                Text(stringResource(R.string.report_personal_expense_categories), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ShareBars(report.personalExpensesByCategory.map { it.name to it.totalMinor }, { money.format(it) }, color = SalonTheme.extended.warning)
            }
        }
    }
}

@Composable
private fun SummaryCards(r: ReportData) {
    val money = LocalMoney.current
    val ext = SalonTheme.extended
    val change = ProfitCalculator.changePercent(r.sales.totalMinor, r.previousSalesMinor)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                stringResource(R.string.report_gross_sales),
                money.format(r.sales.totalMinor),
                Icons.AutoMirrored.Filled.TrendingUp,
                Modifier.weight(1f),
                subtitle = change?.let {
                    stringResource(
                        if (it >= 0) R.string.report_vs_previous_up else R.string.report_vs_previous_down,
                        Percent.formatRatio(kotlin.math.abs(it)),
                    )
                },
            )
            StatCard(
                stringResource(R.string.report_business_expenses),
                money.format(r.profit.businessExpensesMinor),
                Icons.Filled.MoneyOff,
                Modifier.weight(1f),
                accent = ext.negative,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                stringResource(R.string.report_staff_payments),
                money.format(r.profit.staffPaymentsMinor),
                Icons.Filled.Badge,
                Modifier.weight(1f),
                accent = ext.warning,
            )
            StatCard(
                stringResource(R.string.report_business_profit),
                money.format(r.profit.businessProfitMinor),
                Icons.Filled.MonetizationOn,
                Modifier.weight(1f),
                accent = if (r.profit.isLoss) ext.negative else ext.positive,
                valueColor = if (r.profit.isLoss) ext.negative else ext.positive,
                subtitle = stringResource(R.string.report_margin_value, Percent.formatRatio(r.profit.profitMarginPercent)),
            )
        }
    }
}

/** Transparent profit formula plus the optional combined cash-flow view. */
@Composable
private fun ProfitCard(r: ReportData) {
    val money = LocalMoney.current
    val ext = SalonTheme.extended
    val p = r.profit
    ContentCard {
        Text(stringResource(R.string.report_profit_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LabeledValueRow(stringResource(R.string.report_gross_sales), money.format(p.grossSalesMinor))
        LabeledValueRow("− " + stringResource(R.string.report_business_expenses), money.format(p.businessExpensesMinor), valueColor = ext.negative)
        LabeledValueRow("− " + stringResource(R.string.report_staff_payments), money.format(p.staffPaymentsMinor), valueColor = ext.negative)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        LabeledValueRow(
            stringResource(R.string.report_business_profit),
            money.format(p.businessProfitMinor),
            emphasize = true,
            valueColor = if (p.isLoss) ext.negative else ext.positive,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.report_cash_flow_title), style = MaterialTheme.typography.titleSmall)
        LabeledValueRow("− " + stringResource(R.string.report_personal_expenses), money.format(p.personalExpensesMinor), valueColor = ext.warning)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        LabeledValueRow(stringResource(R.string.report_remaining_cash), money.format(p.remainingCashMinor), emphasize = true)
        Text(
            stringResource(R.string.report_profit_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendCard(r: ReportData) {
    if (r.salesTrend.size < 2) return
    val money = LocalMoney.current
    val locale = Locale.getDefault()
    ContentCard {
        Text(
            stringResource(
                when (r.trendGranularity) {
                    TrendGranularity.DAY -> R.string.report_trend_daily
                    TrendGranularity.WEEK -> R.string.report_trend_weekly
                    TrendGranularity.MONTH -> R.string.report_trend_monthly
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        val points = r.salesTrend.takeLast(14)
        BarChart(
            bars = points.map { point ->
                ChartBar(
                    label = when (r.trendGranularity) {
                        TrendGranularity.DAY -> if (points.size <= 7) point.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else point.start.dayOfMonth.toString()
                        TrendGranularity.WEEK -> DateTimeUtils.formatShortDate(point.start)
                        TrendGranularity.MONTH -> point.start.month.getDisplayName(TextStyle.SHORT, locale)
                    },
                    value = point.amountMinor,
                )
            },
            valueLabel = { money.compact(it) },
        )
        if (r.salesTrend.size > points.size) {
            Text(stringResource(R.string.report_trend_last_points, points.size), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CustomersCard(r: ReportData) {
    val money = LocalMoney.current
    ContentCard {
        Text(stringResource(R.string.report_sales_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LabeledValueRow(stringResource(R.string.sales_count), r.sales.saleCount.toString())
        LabeledValueRow(stringResource(R.string.report_services_done), r.sales.serviceCount.toString())
        LabeledValueRow(stringResource(R.string.report_average_sale), money.format(r.averageSaleMinor))
        LabeledValueRow(stringResource(R.string.report_discounts), money.format(r.sales.discountMinor))
        LabeledValueRow(stringResource(R.string.report_customers_served), r.distinctCustomers.toString())
        LabeledValueRow(stringResource(R.string.report_new_customers), r.newCustomers.toString())
        LabeledValueRow(stringResource(R.string.dashboard_cash_received), money.format(r.sales.cashMinor))
    }
}

@Composable
private fun StaffCard(r: ReportData) {
    val money = LocalMoney.current
    ContentCard {
        Text(stringResource(R.string.report_staff_performance), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        r.staffPerformance.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.staffName ?: stringResource(R.string.report_unassigned), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.report_staff_line, row.customerCount, row.serviceCount, money.format(row.commissionMinor)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(money.format(row.salesMinor), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
