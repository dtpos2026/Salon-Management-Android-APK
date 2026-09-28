package com.dtpos.salonmanager.presentation.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.entities.ExpenseCategoryEntity
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.ExpenseInput
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.expenseCategoryLabelRes
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ChoiceChips
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.DateField
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SegmentedChoice
import com.dtpos.salonmanager.presentation.customers.EditEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseForm(
    val type: ExpenseType = ExpenseType.BUSINESS,
    val categoryId: Long? = null,
    val amount: String = "",
    val date: LocalDate = DateTimeUtils.today(),
    val method: PaymentMethod = PaymentMethod.CASH,
    val fromCounter: Boolean = true,
    val note: String = "",
    val amountError: ValidationError? = null,
    val categoryMissing: Boolean = false,
    val saving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseEditViewModel(container: AppContainer, private val expenseId: Long?, initialType: ExpenseType) : BaseViewModel() {
    private val repo = container.expenseRepository
    private val _form = MutableStateFlow(ExpenseForm(type = initialType))
    val form: StateFlow<ExpenseForm> = _form.asStateFlow()
    val events = MutableSharedFlow<EditEvent>(extraBufferCapacity = 1)

    val categories: StateFlow<List<ExpenseCategoryEntity>> = _form.map { it.type }.distinctUntilChanged()
        .flatMapLatest { repo.observeCategories(it) }
        .map { list -> list.filter { it.isActive || it.id == _form.value.categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                repo.get(expenseId)?.let { e ->
                    _form.value = ExpenseForm(
                        type = e.type,
                        categoryId = e.categoryId,
                        amount = Money.toInput(e.amountMinor),
                        date = LocalDate.ofEpochDay(e.expenseDate),
                        method = e.paymentMethod,
                        fromCounter = e.paidFromCounter,
                        note = e.note.orEmpty(),
                    )
                }
            }
        }
    }

    fun update(transform: (ExpenseForm) -> ExpenseForm) = _form.update(transform)

    fun onType(type: ExpenseType) = _form.update { it.copy(type = type, categoryId = null) }

    fun save() {
        val f = _form.value
        val amount = Validators.amount(f.amount)
        val note = Validators.optionalText(f.note)
        _form.update { it.copy(amountError = amount.errorOrNull, categoryMissing = f.categoryId == null) }
        if (amount !is FieldResult.Valid || f.categoryId == null || note !is FieldResult.Valid) return
        _form.update { it.copy(saving = true) }
        launchSafe {
            val input = ExpenseInput(f.type, f.categoryId, amount.value, f.date.toEpochDay(), f.method, f.fromCounter, note.value)
            when (val result = repo.save(input, expenseId)) {
                is DataResult.Success -> events.tryEmit(EditEvent.Saved)
                is DataResult.Failure -> showMessage(result.error.messageRes)
            }
            _form.update { it.copy(saving = false) }
        }
    }

    fun delete() = launchSafe {
        val id = expenseId ?: return@launchSafe
        when (val result = repo.delete(id)) {
            is DataResult.Success -> events.tryEmit(EditEvent.Deleted)
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }
}

@Composable
fun ExpenseCategoryName(category: ExpenseCategoryEntity): String =
    expenseCategoryLabelRes(category.systemKey)?.let { stringResource(it) } ?: category.name

@Composable
fun ExpenseEditScreen(expenseId: Long?, initialType: ExpenseType, onBack: () -> Unit, onManageCategories: (ExpenseType) -> Unit) {
    val vm = appViewModel(key = "expense_edit_$expenseId") { ExpenseEditViewModel(it, expenseId, initialType) }
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(vm) { vm.events.collect { onBack() } }

    Scaffold(
        topBar = {
            SalonTopBar(
                title = stringResource(if (expenseId == null) R.string.expenses_add else R.string.expenses_edit),
                onBack = onBack,
                actions = {
                    if (expenseId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedChoice(
                options = ExpenseType.entries.toList(),
                selected = form.type,
                label = { stringResource(it.labelRes) },
                onSelect = vm::onType,
            )
            Text(
                stringResource(if (form.type == ExpenseType.BUSINESS) R.string.expenses_business_hint else R.string.expenses_personal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AmountField(
                form.amount,
                { v -> vm.update { it.copy(amount = v, amountError = null) } },
                stringResource(R.string.field_amount),
                money.config.symbol,
                error = form.amountError?.let { stringResource(it.messageRes) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { onManageCategories(form.type) }) { Text(stringResource(R.string.expenses_manage_categories)) }
            }
            ChoiceChips(
                options = categories.map { it.id },
                selected = form.categoryId,
                label = { id -> categories.firstOrNull { it.id == id }?.let { ExpenseCategoryName(it) } ?: "" },
                onSelect = { id -> vm.update { it.copy(categoryId = id, categoryMissing = false) } },
            )
            if (form.categoryMissing) {
                Text(stringResource(R.string.expenses_pick_category), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            DateField(stringResource(R.string.field_date), form.date, { d -> d?.let { vm.update { f -> f.copy(date = it) } } }, maxDate = DateTimeUtils.today())
            Text(stringResource(R.string.pos_payment_method), style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(
                options = PaymentMethod.entries.toList(),
                selected = form.method,
                label = { stringResource(it.labelRes) },
                onSelect = { m -> vm.update { it.copy(method = m) } },
            )
            if (form.method == PaymentMethod.CASH) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_from_counter))
                        Text(
                            stringResource(R.string.expenses_from_counter_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = form.fromCounter, onCheckedChange = { v -> vm.update { it.copy(fromCounter = v) } })
                }
            }
            FormTextField(form.note, { v -> vm.update { it.copy(note = v) } }, stringResource(R.string.field_note_optional), singleLine = false)
            Button(onClick = vm::save, enabled = !form.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.expenses_delete_title),
            message = stringResource(R.string.expenses_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
