package com.dtpos.salonmanager.presentation.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.DayCloseReport
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.common.showWhatsAppResult
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.StatusBadge
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.export.ExternalApps
import com.dtpos.salonmanager.services.printer.PrintResult
import com.dtpos.salonmanager.services.printer.Slips
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class CloseDayViewModel(private val container: AppContainer) : BaseViewModel() {

    private val _date = MutableStateFlow(DateTimeUtils.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _report = MutableStateFlow<DayCloseReport?>(null)
    val report: StateFlow<DayCloseReport?> = _report.asStateFlow()

    val printing = MutableStateFlow(false)

    val salonName: StateFlow<String> = kotlinx.coroutines.flow.flow {
        container.businessRepository.profile.collect { emit(it?.name.orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        // Open on the day that is still running (yesterday's late close lands on today).
        viewModelScope.launch {
            val today = DateTimeUtils.today()
            _date.value = today
            refresh()
        }
    }

    fun shift(days: Long) {
        val next = _date.value.plusDays(days)
        if (next.isAfter(DateTimeUtils.today().plusDays(1))) return
        _date.value = next
        refresh()
    }

    fun refresh() = launchSafe {
        _report.value = container.reportRepository.buildDayClose(_date.value)
    }

    fun close(countedInput: String, note: String) {
        val counted = Money.parse(countedInput)
        if (counted == null || counted < 0) {
            showMessage(R.string.error_invalid_amount)
            return
        }
        launchSafe {
            when (val result = container.cashRepository.closeDay(_date.value, counted, note)) {
                is DataResult.Success -> {
                    container.soundEffects.success()
                    showMessage(R.string.close_done)
                    refresh()
                }
                is DataResult.Failure -> showMessage(result.error.messageRes)
            }
        }
    }

    fun reopen() = launchSafe {
        when (val result = container.cashRepository.reopenDay(_date.value)) {
            is DataResult.Success -> {
                showMessage(R.string.close_reopened)
                refresh()
            }
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }

    private suspend fun money(): CurrencyFormatter =
        CurrencyFormatter(container.businessRepository.profile.first()?.currency ?: com.dtpos.salonmanager.core.util.CurrencyConfig())

    fun print() {
        val current = _report.value ?: return
        if (printing.value) return
        printing.value = true
        launchSafe {
            try {
                val lines = Slips.dayClose(current, salonName.value, container.receiptPrinter.slipContext(), money())
                when (val result = container.receiptPrinter.printLines(lines)) {
                    PrintResult.Success -> showMessage(R.string.print_success)
                    is PrintResult.Failure -> showMessage(result.error.messageRes)
                }
            } finally {
                printing.value = false
            }
        }
    }

    suspend fun shareText(): String? {
        val current = _report.value ?: return null
        return Slips.asText(Slips.dayClose(current, salonName.value, container.context, money()))
    }
}

/**
 * Close Day: the day's full account (sales, cash and each account, udhaar, staff commission,
 * owner's own work, expenses, cash drawer). Count the cash, close, print the slip on 58 / 80 mm.
 * After closing, new sales count for the next day so the dashboard starts from zero.
 */
@Composable
fun CloseDayScreen(onBack: () -> Unit) {
    val vm = appViewModel { CloseDayViewModel(it) }
    val date by vm.date.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val printing by vm.printing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)
    val context = LocalContext.current
    var shareRequest by remember { mutableStateOf(0) }
    var askReopen by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.close_share)
    LaunchedEffect(shareRequest) {
        if (shareRequest > 0) {
            vm.shareText()?.let { context.showWhatsAppResult(ExternalApps.whatsAppText(context, null, it, shareTitle), it) }
        }
    }

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_close_day), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val r = report
        if (r == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val money = LocalMoney.current
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (date == DateTimeUtils.today()) stringResource(R.string.period_today) else DateTimeUtils.formatDate(date),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        StatusBadge(
                            stringResource(if (r.isClosed) R.string.close_status_closed else R.string.close_status_open),
                            container = if (r.isClosed) SalonTheme.extended.positiveContainer else SalonTheme.extended.warningContainer,
                            content = if (r.isClosed) SalonTheme.extended.positive else SalonTheme.extended.warning,
                        )
                    }
                    IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                }
            }
            item {
                ContentCard {
                    Text(stringResource(R.string.close_sales_title), style = MaterialTheme.typography.titleMedium)
                    LabeledValueRow(stringResource(R.string.close_total_sales), money.format(r.sales.totalMinor), emphasize = true)
                    LabeledValueRow(stringResource(R.string.close_customers), r.sales.saleCount.toString())
                    LabeledValueRow(stringResource(R.string.close_services), r.sales.serviceCount.toString())
                    if (r.sales.discountMinor > 0) LabeledValueRow(stringResource(R.string.close_discounts), money.format(r.sales.discountMinor))
                }
            }
            item {
                ContentCard {
                    Text(stringResource(R.string.close_money_title), style = MaterialTheme.typography.titleMedium)
                    val cash = r.received.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.totalMinor }
                    LabeledValueRow(stringResource(R.string.payment_cash), money.format(cash))
                    r.received.filter { it.paymentMethod != PaymentMethod.CASH }.forEach {
                        LabeledValueRow(it.accountName ?: stringResource(it.paymentMethod.labelRes), money.format(it.totalMinor))
                    }
                    if (r.sales.creditMinor > 0) {
                        LabeledValueRow(stringResource(R.string.close_udhaar_given), money.format(r.sales.creditMinor), valueColor = SalonTheme.extended.warning)
                    }
                    if (r.duesCollectedMinor > 0) {
                        LabeledValueRow(stringResource(R.string.close_udhaar_received), money.format(r.duesCollectedMinor), valueColor = SalonTheme.extended.positive)
                    }
                }
            }
            if (r.staff.isNotEmpty()) {
                item {
                    ContentCard {
                        Text(stringResource(R.string.close_staff_title), style = MaterialTheme.typography.titleMedium)
                        r.staff.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (s.name ?: stringResource(R.string.pos_no_staff)) + if (s.isOwner) " (${stringResource(R.string.role_owner)})" else "",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        if (s.isOwner) stringResource(R.string.close_staff_owner_line, s.customers)
                                        else stringResource(R.string.close_staff_line, s.customers, money.format(s.commissionMinor)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(money.format(s.salesMinor), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LabeledValueRow(stringResource(R.string.close_commission_total), money.format(r.commissionMinor), emphasize = true)
                        if (r.ownerWorkMinor > 0) LabeledValueRow(stringResource(R.string.close_owner_work), money.format(r.ownerWorkMinor))
                    }
                }
            }
            item {
                ContentCard {
                    Text(stringResource(R.string.close_costs_title), style = MaterialTheme.typography.titleMedium)
                    LabeledValueRow(stringResource(R.string.close_expenses), money.format(r.businessExpensesMinor))
                    LabeledValueRow(stringResource(R.string.close_staff_paid), money.format(r.staffPaidMinor))
                    LabeledValueRow(
                        stringResource(R.string.close_profit),
                        money.format(r.profit.businessProfitMinor),
                        emphasize = true,
                        valueColor = if (r.profit.isLoss) SalonTheme.extended.negative else SalonTheme.extended.positive,
                    )
                }
            }
            item {
                val c = r.cash
                ContentCard {
                    Text(stringResource(R.string.close_drawer_title), style = MaterialTheme.typography.titleMedium)
                    LabeledValueRow(stringResource(R.string.cash_opening), money.format(c.openingMinor))
                    LabeledValueRow("+ " + stringResource(R.string.cash_sales), money.format(c.cashSalesMinor))
                    if (c.cashInMinor > 0) LabeledValueRow("+ " + stringResource(R.string.close_cash_in), money.format(c.cashInMinor))
                    if (c.voidRefundsMinor > 0) LabeledValueRow("- " + stringResource(R.string.close_refunds), money.format(c.voidRefundsMinor))
                    if (c.cashExpensesMinor > 0) LabeledValueRow("- " + stringResource(R.string.close_expenses), money.format(c.cashExpensesMinor))
                    if (c.staffPaymentsMinor > 0) LabeledValueRow("- " + stringResource(R.string.close_staff_paid), money.format(c.staffPaymentsMinor))
                    if (c.cashOutMinor > 0) LabeledValueRow("- " + stringResource(R.string.close_cash_out), money.format(c.cashOutMinor))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabeledValueRow(stringResource(R.string.close_expected_cash), money.format(c.expectedClosingMinor), emphasize = true)
                    r.session?.actualClosingMinor?.let { counted ->
                        LabeledValueRow(stringResource(R.string.close_counted_cash), money.format(counted))
                        val diff = r.session.differenceMinor ?: (counted - c.expectedClosingMinor)
                        LabeledValueRow(
                            stringResource(R.string.close_difference),
                            (if (diff > 0) "+" else "") + money.format(diff),
                            valueColor = when {
                                diff < 0 -> SalonTheme.extended.negative
                                diff > 0 -> SalonTheme.extended.positive
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::print, enabled = !printing, modifier = Modifier.weight(1f)) {
                        if (printing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.close_print))
                        }
                    }
                    OutlinedButton(onClick = { shareRequest++ }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.close_share))
                    }
                }
            }
            if (!r.isClosed) {
                item { CloseForm(r, money, onClose = vm::close) }
            } else {
                item {
                    TextButton(onClick = { askReopen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.close_reopen))
                    }
                }
            }
        }
    }
    if (askReopen) {
        ConfirmDialog(
            title = stringResource(R.string.close_reopen),
            message = stringResource(R.string.close_reopen_message),
            confirmLabel = stringResource(R.string.close_reopen),
            onConfirm = {
                askReopen = false
                vm.reopen()
            },
            onDismiss = { askReopen = false },
        )
    }
}

@Composable
private fun CloseForm(report: DayCloseReport, money: CurrencyFormatter, onClose: (String, String) -> Unit) {
    var counted by remember(report.day) { mutableStateOf(Money.toInput(report.cash.expectedClosingMinor.coerceAtLeast(0))) }
    var note by remember(report.day) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    ContentCard {
        Text(stringResource(R.string.close_count_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.close_count_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        AmountField(counted, { counted = it }, stringResource(R.string.close_counted_cash), currencySymbol = money.config.symbol)
        Spacer(Modifier.height(6.dp))
        FormTextField(note, { note = it.take(200) }, stringResource(R.string.field_note_optional))
        Spacer(Modifier.height(10.dp))
        Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.close_button), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.close_button),
            message = stringResource(R.string.close_confirm_message),
            confirmLabel = stringResource(R.string.close_button),
            onConfirm = {
                confirm = false
                onClose(counted, note)
            },
            onDismiss = { confirm = false },
        )
    }
}
