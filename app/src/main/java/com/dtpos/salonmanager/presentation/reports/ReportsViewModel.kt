package com.dtpos.salonmanager.presentation.reports

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.data.repository.ReportData
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.services.export.DataExporter
import com.dtpos.salonmanager.services.export.ReportBlock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReportsUiState(
    val preset: PeriodPreset = PeriodPreset.TODAY,
    val range: DateRange = DateRange.single(DateTimeUtils.today()),
    val report: ReportData? = null,
    val loading: Boolean = true,
    val exporting: Boolean = false,
)

class ReportsViewModel(private val container: AppContainer) : BaseViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()
    val shareFile = MutableSharedFlow<File>(extraBufferCapacity = 1)

    init {
        load()
    }

    fun onPreset(preset: PeriodPreset) {
        if (preset == PeriodPreset.CUSTOM) return
        _state.update { it.copy(preset = preset, range = Periods.range(preset, DateTimeUtils.today())) }
        load()
    }

    fun onCustomRange(range: DateRange) {
        _state.update { it.copy(preset = PeriodPreset.CUSTOM, range = range) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        val range = _state.value.range
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val report = container.reportRepository.buildReport(range)
                _state.update { if (it.range == range) it.copy(report = report, loading = false) else it }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false) }
                showMessage(R.string.error_unexpected)
            }
        }
    }

    fun exportCsv(uri: Uri) {
        _state.update { it.copy(exporting = true) }
        launchSafe {
            val ok = container.dataExporter.exportCsvZip(uri, _state.value.range)
            showMessage(if (ok) R.string.export_done else R.string.export_failed)
            _state.update { it.copy(exporting = false) }
        }
    }

    fun csvFileName(): String = DataExporter.suggestedFileName(_state.value.range)

    fun exportPdf() {
        val report = _state.value.report ?: return
        _state.update { it.copy(exporting = true) }
        launchSafe {
            val profile = container.businessRepository.getProfile()
            val money = CurrencyFormatter(profile?.currency ?: com.dtpos.salonmanager.core.util.CurrencyConfig())
            val blocks = buildPdf(report, profile?.name.orEmpty(), money)
            val name = "SalonReport_${DateTimeUtils.formatIso(report.range.start)}_${DateTimeUtils.formatIso(report.range.end)}.pdf"
            val file = container.pdfExporter.reportPdf(blocks, name)
            if (file == null) showMessage(R.string.export_failed) else shareFile.tryEmit(file)
            _state.update { it.copy(exporting = false) }
        }
    }

    private fun buildPdf(r: ReportData, salonName: String, money: CurrencyFormatter): List<ReportBlock> {
        val c = container.context
        fun s(id: Int, vararg args: Any) = c.getString(id, *args)
        val period = if (r.range.dayCount == 1) DateTimeUtils.formatDate(r.range.start)
        else "${DateTimeUtils.formatDate(r.range.start)} - ${DateTimeUtils.formatDate(r.range.end)}"
        val p = r.profit
        return buildList {
            add(ReportBlock.Title(s(R.string.report_pdf_title, salonName.ifBlank { s(R.string.app_name) }), period))
            add(ReportBlock.Heading(s(R.string.report_profit_title)))
            add(ReportBlock.KeyValue(s(R.string.report_gross_sales), money.format(p.grossSalesMinor)))
            add(ReportBlock.KeyValue(s(R.string.report_business_expenses), "-" + money.format(p.businessExpensesMinor)))
            add(ReportBlock.KeyValue(s(R.string.report_staff_payments), "-" + money.format(p.staffPaymentsMinor)))
            add(ReportBlock.KeyValue(s(R.string.report_business_profit), money.format(p.businessProfitMinor), emphasize = true))
            add(ReportBlock.KeyValue(s(R.string.report_profit_margin), "${Percent.formatRatio(p.profitMarginPercent)}%"))
            add(ReportBlock.Spacer)
            add(ReportBlock.KeyValue(s(R.string.report_personal_expenses), "-" + money.format(p.personalExpensesMinor)))
            add(ReportBlock.KeyValue(s(R.string.report_remaining_cash), money.format(p.remainingCashMinor), emphasize = true))
            add(ReportBlock.Note(s(R.string.report_profit_note)))

            add(ReportBlock.Heading(s(R.string.report_sales_title)))
            add(ReportBlock.KeyValue(s(R.string.sales_count), r.sales.saleCount.toString()))
            add(ReportBlock.KeyValue(s(R.string.report_services_done), r.sales.serviceCount.toString()))
            add(ReportBlock.KeyValue(s(R.string.report_discounts), money.format(r.sales.discountMinor)))
            add(ReportBlock.KeyValue(s(R.string.report_average_sale), money.format(r.averageSaleMinor)))
            add(ReportBlock.KeyValue(s(R.string.report_customers_served), r.distinctCustomers.toString()))
            add(ReportBlock.KeyValue(s(R.string.report_new_customers), r.newCustomers.toString()))
            add(ReportBlock.KeyValue(s(R.string.report_previous_period), money.format(r.previousSalesMinor)))

            if (r.paymentBreakdown.isNotEmpty()) {
                add(ReportBlock.Heading(s(R.string.report_payment_methods)))
                r.paymentBreakdown.forEach { add(ReportBlock.KeyValue(s(it.paymentMethod.labelRes), money.format(it.totalMinor))) }
            }
            if (r.topServices.isNotEmpty()) {
                add(ReportBlock.Heading(s(R.string.report_top_services)))
                add(
                    ReportBlock.Table(
                        headers = listOf(s(R.string.report_col_service), s(R.string.report_col_qty), s(R.string.report_col_revenue)),
                        rows = r.topServices.map { listOf(it.name, it.quantity.toString(), money.format(it.totalMinor)) },
                        weights = listOf(3f, 1f, 1.5f),
                    ),
                )
            }
            if (r.staffPerformance.isNotEmpty()) {
                add(ReportBlock.Heading(s(R.string.report_staff_performance)))
                add(
                    ReportBlock.Table(
                        headers = listOf(
                            s(R.string.report_col_staff), s(R.string.report_col_customers), s(R.string.report_col_services),
                            s(R.string.report_col_sales), s(R.string.report_col_commission),
                        ),
                        rows = r.staffPerformance.map {
                            listOf(
                                it.staffName ?: s(R.string.report_unassigned), it.customerCount.toString(), it.serviceCount.toString(),
                                money.format(it.salesMinor), money.format(it.commissionMinor),
                            )
                        },
                        weights = listOf(2.5f, 1f, 1f, 1.5f, 1.5f),
                    ),
                )
            }
            if (r.businessExpensesByCategory.isNotEmpty()) {
                add(ReportBlock.Heading(s(R.string.report_business_expense_categories)))
                r.businessExpensesByCategory.forEach { add(ReportBlock.KeyValue(it.name, money.format(it.totalMinor))) }
            }
            if (r.personalExpensesByCategory.isNotEmpty()) {
                add(ReportBlock.Heading(s(R.string.report_personal_expense_categories)))
                r.personalExpensesByCategory.forEach { add(ReportBlock.KeyValue(it.name, money.format(it.totalMinor))) }
            }
            add(ReportBlock.Spacer)
            add(ReportBlock.Note(s(R.string.report_generated_on, DateTimeUtils.formatDateTime(System.currentTimeMillis()))))
        }
    }
}
