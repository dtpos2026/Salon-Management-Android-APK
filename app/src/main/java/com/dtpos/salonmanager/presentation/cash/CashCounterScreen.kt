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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.entities.CashSessionEntity
import com.dtpos.salonmanager.data.repository.CashDayState
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.calc.CashDifferenceStatus
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.components.StatusBadge
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class CashCounterViewModel(container: AppContainer) : BaseViewModel() {
    private val repo = container.cashRepository
    private val date = MutableStateFlow(DateTimeUtils.today())
    val selectedDate: StateFlow<LocalDate> = date

    val day: StateFlow<CashDayState?> = date.flatMapLatest { repo.observeDay(it) }
        .map<CashDayState, CashDayState?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<CashSessionEntity>> = repo.observeHistory(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _suggestedOpening = MutableStateFlow<Long?>(null)
    val suggestedOpening: StateFlow<Long?> = _suggestedOpening

    init {
        viewModelScope.launch { date.collect { _suggestedOpening.value = repo.suggestedOpening(it) } }
    }

    fun shiftDay(delta: Long) {
        val next = date.value.plusDays(delta)
        if (!next.isAfter(DateTimeUtils.today())) date.value = next
    }

    fun setOpening(input: String, onDone: () -> Unit) {
        val amount = Validators.amount(input, allowZero = true)
        if (amount !is FieldResult.Valid) return showMessage(amount.errorOrNull!!.messageRes)
        launchSafe {
            when (val r = repo.setOpeningCash(date.value, amount.value)) {
                is DataResult.Success -> onDone()
                is DataResult.Failure -> showMessage(r.error.messageRes)
            }
        }
    }

    fun addMovement(type: CashTxType, input: String, note: String, onDone: () -> Unit) {
        val amount = Validators.amount(input)
        if (amount !is FieldResult.Valid) return showMessage(amount.errorOrNull!!.messageRes)
        launchSafe {
            when (val r = repo.addManualMovement(date.value, type, amount.value, note)) {
                is DataResult.Success -> onDone()
                is DataResult.Failure -> showMessage(r.error.messageRes)
            }
        }
    }

    fun deleteMovement(id: Long) = launchSafe { repo.deleteManualMovement(id) }

    fun closeDay(input: String, note: String, onDone: () -> Unit) {
        val amount = Validators.amount(input, allowZero = true)
        if (amount !is FieldResult.Valid) return showMessage(amount.errorOrNull!!.messageRes)
        launchSafe {
            when (val r = repo.closeDay(date.value, amount.value, note)) {
                is DataResult.Success -> {
                    showMessage(R.string.cash_closed_message)
                    onDone()
                }
                is DataResult.Failure -> showMessage(r.error.messageRes)
            }
        }
    }

    fun reopen() = launchSafe { repo.reopenDay(date.value) }
}

private enum class CashDialog { OPENING, CASH_IN, CASH_OUT, CLOSE }

@Composable
fun CashCounterScreen(onBack: () -> Unit) {
    val vm = appViewModel { CashCounterViewModel(it) }
    val day by vm.day.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val suggested by vm.suggestedOpening.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<CashDialog?>(null) }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_cash), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val state = day
        if (state == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val b = state.breakdown
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(DateTimeUtils.formatDate(date), style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            stringResource(if (state.isClosed) R.string.cash_status_closed else R.string.cash_status_open),
                            container = if (state.isClosed) MaterialTheme.colorScheme.surfaceVariant else SalonTheme.extended.positiveContainer,
                            content = if (state.isClosed) MaterialTheme.colorScheme.onSurfaceVariant else SalonTheme.extended.positive,
                        )
                    }
                    IconButton(onClick = { vm.shiftDay(1) }, enabled = date.isBefore(DateTimeUtils.today())) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next))
                    }
                }
            }
            item {
                ContentCard {
                    Text(stringResource(R.string.cash_expected_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LabeledValueRow(stringResource(R.string.cash_opening), money.format(b.openingMinor))
                    LabeledValueRow("+ " + stringResource(R.string.cash_sales), money.format(b.cashSalesMinor), valueColor = SalonTheme.extended.positive)
                    if (b.voidRefundsMinor > 0) LabeledValueRow("- " + stringResource(R.string.cash_voids), money.format(b.voidRefundsMinor))
                    LabeledValueRow("- " + stringResource(R.string.cash_expenses), money.format(b.cashExpensesMinor), valueColor = SalonTheme.extended.negative)
                    LabeledValueRow("- " + stringResource(R.string.cash_staff_payments), money.format(b.staffPaymentsMinor), valueColor = SalonTheme.extended.negative)
                    if (b.cashInMinor > 0) LabeledValueRow("+ " + stringResource(R.string.cash_tx_in), money.format(b.cashInMinor))
                    if (b.cashOutMinor > 0) LabeledValueRow("- " + stringResource(R.string.cash_tx_out), money.format(b.cashOutMinor))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabeledValueRow(stringResource(R.string.cash_expected), money.format(b.expectedClosingMinor), emphasize = true)
                    state.session?.actualClosingMinor?.let { actual ->
                        val difference = state.session?.differenceMinor ?: b.differenceFor(actual)
                        LabeledValueRow(stringResource(R.string.cash_actual), money.format(actual), emphasize = true)
                        DifferenceRow(difference)
                    }
                }
            }
            item {
                if (state.isClosed) {
                    OutlinedButton(onClick = vm::reopen, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cash_reopen))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { dialog = CashDialog.OPENING }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cash_set_opening))
                            }
                            OutlinedButton(onClick = { dialog = CashDialog.CASH_IN }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cash_tx_in))
                            }
                            OutlinedButton(onClick = { dialog = CashDialog.CASH_OUT }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cash_tx_out))
                            }
                        }
                        Button(onClick = { dialog = CashDialog.CLOSE }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.cash_close_day))
                        }
                    }
                }
            }
            if (state.transactions.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.cash_movements)) }
                items(state.transactions, key = { it.id }) { tx ->
                    ContentCard {
                        ListItem(
                            headlineContent = { Text(stringResource(tx.type.labelRes)) },
                            supportingContent = {
                                Text(listOfNotNull(DateTimeUtils.formatTime(tx.createdAt), tx.note).joinToString(" · "))
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        (if (tx.amountMinor >= 0) "+" else "-") + money.format(abs(tx.amountMinor)),
                                        color = if (tx.amountMinor >= 0) SalonTheme.extended.positive else SalonTheme.extended.negative,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    if (tx.referenceType == null && !state.isClosed) {
                                        IconButton(onClick = { vm.deleteMovement(tx.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val closed = history.filter { it.actualClosingMinor != null }
            if (closed.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.cash_history)) }
                items(closed, key = { "h${it.id}" }) { session ->
                    ContentCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(DateTimeUtils.formatDate(LocalDate.ofEpochDay(session.sessionDate)), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.cash_history_line,
                                        money.format(session.expectedClosingMinor ?: 0),
                                        money.format(session.actualClosingMinor ?: 0),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            DifferenceBadge(session.differenceMinor ?: 0)
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        CashDialog.OPENING -> AmountDialog(
            title = stringResource(R.string.cash_set_opening),
            initial = Money.toInput(day?.session?.openingCashMinor ?: suggested ?: 0L),
            hint = suggested?.let { stringResource(R.string.cash_opening_hint, money.format(it)) },
            withNote = false,
            onSave = { amount, _ -> vm.setOpening(amount) { dialog = null } },
            onDismiss = { dialog = null },
        )
        CashDialog.CASH_IN, CashDialog.CASH_OUT -> {
            val type = if (dialog == CashDialog.CASH_IN) CashTxType.CASH_IN else CashTxType.CASH_OUT
            AmountDialog(
                title = stringResource(type.labelRes),
                initial = "",
                hint = stringResource(if (type == CashTxType.CASH_IN) R.string.cash_in_hint else R.string.cash_out_hint),
                withNote = true,
                onSave = { amount, note -> vm.addMovement(type, amount, note) { dialog = null } },
                onDismiss = { dialog = null },
            )
        }
        CashDialog.CLOSE -> AmountDialog(
            title = stringResource(R.string.cash_close_day),
            initial = "",
            hint = stringResource(R.string.cash_close_hint, money.format(day?.breakdown?.expectedClosingMinor ?: 0)),
            withNote = true,
            onSave = { amount, note -> vm.closeDay(amount, note) { dialog = null } },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun DifferenceRow(difference: Long) {
    val money = LocalMoney.current
    val status = CashCalculator.status(difference)
    val color = when (status) {
        CashDifferenceStatus.BALANCED -> SalonTheme.extended.positive
        CashDifferenceStatus.SHORT -> SalonTheme.extended.negative
        CashDifferenceStatus.EXCESS -> SalonTheme.extended.warning
    }
    val label = when (status) {
        CashDifferenceStatus.BALANCED -> stringResource(R.string.cash_balanced)
        CashDifferenceStatus.SHORT -> stringResource(R.string.cash_short)
        CashDifferenceStatus.EXCESS -> stringResource(R.string.cash_excess)
    }
    LabeledValueRow(label, money.format(abs(difference)), emphasize = true, valueColor = color)
}

@Composable
private fun DifferenceBadge(difference: Long) {
    val money = LocalMoney.current
    val ext = SalonTheme.extended
    when (CashCalculator.status(difference)) {
        CashDifferenceStatus.BALANCED -> StatusBadge(stringResource(R.string.cash_balanced), ext.positiveContainer, ext.positive)
        CashDifferenceStatus.SHORT -> StatusBadge(stringResource(R.string.cash_short_amount, money.format(abs(difference))), ext.negativeContainer, ext.negative)
        CashDifferenceStatus.EXCESS -> StatusBadge(stringResource(R.string.cash_excess_amount, money.format(difference)), ext.warningContainer, ext.warning)
    }
}

@Composable
private fun AmountDialog(
    title: String,
    initial: String,
    hint: String?,
    withNote: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    var amount by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start) }
                AmountField(amount, { amount = it }, stringResource(R.string.field_amount), money.config.symbol)
                if (withNote) FormTextField(note, { note = it }, stringResource(R.string.field_note_optional))
            }
        },
        confirmButton = { Button(onClick = { onSave(amount, note) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
