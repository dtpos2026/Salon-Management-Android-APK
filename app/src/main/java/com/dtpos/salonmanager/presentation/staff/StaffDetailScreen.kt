package com.dtpos.salonmanager.presentation.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.entities.StaffPaymentEntity
import com.dtpos.salonmanager.data.database.model.StaffPerformanceRow
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.StaffPaymentInput
import com.dtpos.salonmanager.domain.calc.StaffSettlement
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.DateField
import com.dtpos.salonmanager.presentation.components.DropdownField
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.components.StatCard
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StaffDetailState(
    val loaded: Boolean = false,
    val staff: StaffEntity? = null,
    val month: LocalDate = DateTimeUtils.monthStart(DateTimeUtils.today()),
    val performance: StaffPerformanceRow? = null,
    val settlement: StaffSettlement? = null,
    val payments: List<StaffPaymentEntity> = emptyList(),
)


@OptIn(ExperimentalCoroutinesApi::class)
class StaffDetailViewModel(private val container: AppContainer, private val staffId: Long) : BaseViewModel() {
    private val repo = container.staffRepository

    /** The staff slip shown before printing / sending (the picture the printer prints). */
    val slips = com.dtpos.salonmanager.presentation.common.SlipPreviews(container.receiptPrinter, container.context, viewModelScope)

    /** Builds the slip exactly as printed and shows it for Print / WhatsApp / Share. */
    fun openSlip(payment: StaffPaymentEntity) = launchSafe {
        val staff = repo.get(staffId) ?: return@launchSafe showMessage(R.string.staff_slip_failed)
        val lines = slip(payment, container.receiptPrinter.slipContext()) ?: return@launchSafe showMessage(R.string.staff_slip_failed)
        val text = com.dtpos.salonmanager.services.printer.Slips.asText(slip(payment, container.context) ?: lines)
        slips.show(container.context.getString(R.string.staff_slip_title), lines, text, staff.phone, "StaffSlip_${payment.id}")
    }

    private suspend fun slip(payment: StaffPaymentEntity, res: android.content.Context): List<com.dtpos.salonmanager.services.printer.PrintLine>? {
        val staff = repo.get(staffId) ?: return null
        val day = LocalDate.ofEpochDay(payment.paymentDate)
        val month = DateRange(DateTimeUtils.monthStart(day), DateTimeUtils.monthEnd(day))
        val settlement = repo.observeSettlement(staffId, month).first()
        val profile = container.businessRepository.profile.first()
        val money = com.dtpos.salonmanager.core.util.CurrencyFormatter(profile?.currency ?: com.dtpos.salonmanager.core.util.CurrencyConfig())
        return com.dtpos.salonmanager.services.printer.Slips.staffPayment(payment, staff, settlement, profile?.name.orEmpty(), res, money)
    }

    private val month = MutableStateFlow(DateTimeUtils.monthStart(DateTimeUtils.today()))

    val state: StateFlow<StaffDetailState> = month.flatMapLatest { m ->
        val range = DateRange(m, DateTimeUtils.monthEnd(m))
        combine(
            repo.observe(staffId),
            repo.observePerformance(staffId, range),
            repo.observeSettlement(staffId, range),
            repo.observePayments(staffId),
        ) { staff, perf, settlement, payments -> StaffDetailState(true, staff, m, perf, settlement, payments) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffDetailState())

    fun shiftMonth(delta: Long) {
        val next = month.value.plusMonths(delta)
        if (!next.isAfter(DateTimeUtils.today())) month.value = next
    }

    fun recordPayment(
        amountInput: String,
        type: StaffPaymentType,
        date: LocalDate,
        method: PaymentMethod,
        fromCounter: Boolean,
        note: String,
        onDone: () -> Unit,
    ) {
        val amount = Validators.amount(amountInput)
        if (amount !is FieldResult.Valid) {
            showMessage(amount.errorOrNull!!.messageRes)
            return
        }
        launchSafe {
            val result = repo.recordPayment(
                StaffPaymentInput(staffId, amount.value, type, date.toEpochDay(), method, fromCounter, note.trim().ifEmpty { null }),
            )
            when (result) {
                is DataResult.Success -> {
                    showMessage(R.string.staff_payment_saved)
                    onDone()
                    repo.observePayments(staffId, 20).first().firstOrNull { it.id == result.data }?.let { openSlip(it) }
                }
                is DataResult.Failure -> showMessage(result.error.messageRes)
            }
        }
    }

    fun deletePayment(id: Long) = launchSafe {
        when (val result = repo.deletePayment(id)) {
            is DataResult.Success -> showMessage(R.string.deleted)
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }
}

@Composable
fun StaffDetailScreen(staffId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = appViewModel(key = "staff_$staffId") { StaffDetailViewModel(it, staffId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPayment by remember { mutableStateOf(false) }
    var deletePayment by remember { mutableStateOf<StaffPaymentEntity?>(null) }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = {
            SalonTopBar(
                title = state.staff?.name ?: stringResource(R.string.nav_staff),
                onBack = onBack,
                subtitle = state.staff?.let { stringResource(it.role.labelRes) },
                actions = { IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPayment = true },
                icon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                text = { Text(stringResource(R.string.staff_record_payment)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val perf = state.performance
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous))
                    }
                    Text(
                        DateTimeUtils.formatMonth(state.month),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.shiftMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(stringResource(R.string.staff_sales), money.format(perf?.salesMinor ?: 0), Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
                        StatCard(stringResource(R.string.dashboard_customers), (perf?.customerCount ?: 0).toString(), Icons.Filled.People, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(stringResource(R.string.dashboard_services), (perf?.serviceCount ?: 0).toString(), Icons.Filled.Spa, Modifier.weight(1f))
                        StatCard(
                            stringResource(R.string.staff_commission),
                            money.format(perf?.commissionMinor ?: 0),
                            Icons.Filled.Percent,
                            Modifier.weight(1f),
                            accent = SalonTheme.extended.gold,
                            subtitle = state.staff?.takeIf { it.salaryType.hasCommission }?.let { "${Percent.formatBps(it.commissionBps)}%" },
                        )
                    }
                }
            }
            state.settlement?.let { s ->
                item {
                    ContentCard {
                        Text(stringResource(R.string.staff_settlement), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LabeledValueRow(stringResource(R.string.staff_fixed_salary), money.format(s.fixedSalaryMinor))
                        LabeledValueRow(stringResource(R.string.staff_commission_earned), money.format(s.commissionEarnedMinor))
                        LabeledValueRow(stringResource(R.string.staff_total_earned), money.format(s.earnedMinor), emphasize = true)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LabeledValueRow(stringResource(R.string.staff_paid_against), "-" + money.format(s.settledMinor))
                        if (s.extrasPaidMinor > 0) LabeledValueRow(stringResource(R.string.staff_extras_paid), money.format(s.extrasPaidMinor))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        if (s.overpaidMinor > 0) {
                            LabeledValueRow(
                                stringResource(R.string.staff_overpaid),
                                money.format(s.overpaidMinor),
                                emphasize = true,
                                valueColor = SalonTheme.extended.warning,
                            )
                        } else {
                            LabeledValueRow(
                                stringResource(R.string.staff_outstanding),
                                money.format(s.outstandingMinor),
                                emphasize = true,
                                valueColor = if (s.outstandingMinor > 0) SalonTheme.extended.negative else SalonTheme.extended.positive,
                            )
                        }
                        Text(
                            stringResource(R.string.staff_settlement_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.staff_payments)) }
            if (state.payments.isEmpty()) {
                item { Text(stringResource(R.string.staff_no_payments), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.payments, key = { it.id }) { payment ->
                ContentCard {
                    ListItem(
                        headlineContent = { Text(stringResource(payment.type.labelRes)) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(DateTimeUtils.formatDate(LocalDate.ofEpochDay(payment.paymentDate)))
                                    append(" · ").append(stringResource(payment.paymentMethod.labelRes))
                                    payment.note?.let { append(" · ").append(it) }
                                },
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(money.format(payment.amountMinor), style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { vm.openSlip(payment) }) {
                                    Icon(Icons.Filled.Receipt, contentDescription = stringResource(R.string.staff_slip_title))
                                }
                                IconButton(onClick = { deletePayment = payment }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    com.dtpos.salonmanager.presentation.common.SlipPreviewDialog(vm.slips)

    if (showPayment) {
        PaymentDialog(
            currencySymbol = money.config.symbol,
            onSave = { amount, type, date, method, fromCounter, note ->
                vm.recordPayment(amount, type, date, method, fromCounter, note) { showPayment = false }
            },
            onDismiss = { showPayment = false },
        )
    }
    deletePayment?.let { payment ->
        ConfirmDialog(
            title = stringResource(R.string.staff_delete_payment_title),
            message = stringResource(R.string.staff_delete_payment_message, money.format(payment.amountMinor)),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                vm.deletePayment(payment.id)
                deletePayment = null
            },
            onDismiss = { deletePayment = null },
        )
    }
}

@Composable
private fun PaymentDialog(
    currencySymbol: String,
    onSave: (String, StaffPaymentType, LocalDate, PaymentMethod, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(StaffPaymentType.SALARY) }
    var date by remember { mutableStateOf(DateTimeUtils.today()) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var fromCounter by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.staff_record_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount, { amount = it }, stringResource(R.string.field_amount), currencySymbol)
                DropdownField(
                    label = stringResource(R.string.field_payment_type),
                    options = StaffPaymentType.entries.toList(),
                    selected = type,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelected = { type = it },
                )
                DateField(stringResource(R.string.field_date), date, { it?.let { d -> date = d } }, maxDate = DateTimeUtils.today())
                DropdownField(
                    label = stringResource(R.string.pos_payment_method),
                    options = PaymentMethod.entries.toList(),
                    selected = method,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelected = { method = it },
                )
                if (method == PaymentMethod.CASH) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.field_from_counter), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = fromCounter, onCheckedChange = { fromCounter = it })
                    }
                }
                FormTextField(note, { note = it }, stringResource(R.string.field_note_optional))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(amount, type, date, method, fromCounter, note) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

