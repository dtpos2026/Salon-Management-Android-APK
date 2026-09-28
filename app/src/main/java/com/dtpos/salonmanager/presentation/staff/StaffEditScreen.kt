package com.dtpos.salonmanager.presentation.staff

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.StaffInput
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffRole
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.DateField
import com.dtpos.salonmanager.presentation.components.DropdownField
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.customers.EditEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StaffForm(
    val name: String = "",
    val phone: String = "",
    val role: StaffRole = StaffRole.BARBER,
    val salaryType: SalaryType = SalaryType.COMMISSION,
    val commission: String = "",
    val fixedSalary: String = "",
    val active: Boolean = true,
    val joinedOn: LocalDate? = null,
    val nameError: ValidationError? = null,
    val phoneError: ValidationError? = null,
    val commissionError: ValidationError? = null,
    val salaryError: ValidationError? = null,
    val saving: Boolean = false,
)

class StaffEditViewModel(container: AppContainer, private val staffId: Long?) : BaseViewModel() {
    private val repo = container.staffRepository
    private val _form = MutableStateFlow(StaffForm())
    val form: StateFlow<StaffForm> = _form.asStateFlow()
    val events = MutableSharedFlow<EditEvent>(extraBufferCapacity = 1)

    init {
        if (staffId != null) {
            viewModelScope.launch {
                repo.get(staffId)?.let { s ->
                    _form.value = StaffForm(
                        name = s.name,
                        phone = s.phone.orEmpty(),
                        role = s.role,
                        salaryType = s.salaryType,
                        commission = if (s.commissionBps > 0) Percent.formatBps(s.commissionBps) else "",
                        fixedSalary = Money.toInput(s.fixedSalaryMinor),
                        active = s.isActive,
                        joinedOn = s.joinedOn?.let(LocalDate::ofEpochDay),
                    )
                }
            }
        }
    }

    fun update(transform: (StaffForm) -> StaffForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        val name = Validators.requiredName(f.name)
        val phone = Validators.phone(f.phone)
        val commission = if (f.salaryType.hasCommission) Validators.percentBps(f.commission, required = true) else FieldResult.Valid(0)
        val salary = if (f.salaryType.hasFixed) Validators.amount(f.fixedSalary) else FieldResult.Valid(0L)
        _form.update {
            it.copy(
                nameError = name.errorOrNull,
                phoneError = phone.errorOrNull,
                commissionError = commission.errorOrNull,
                salaryError = salary.errorOrNull,
            )
        }
        if (name !is FieldResult.Valid || phone !is FieldResult.Valid || commission !is FieldResult.Valid || salary !is FieldResult.Valid) return
        _form.update { it.copy(saving = true) }
        launchSafe {
            val input = StaffInput(name.value, phone.value, f.role, f.salaryType, commission.value, salary.value, f.active, f.joinedOn?.toEpochDay())
            when (val result = repo.save(input, staffId)) {
                is DataResult.Success -> events.tryEmit(EditEvent.Saved)
                is DataResult.Failure -> {
                    if (result.error == DataError.DUPLICATE_NAME) _form.update { it.copy(nameError = ValidationError.DUPLICATE_NAME) }
                    else showMessage(result.error.messageRes)
                }
            }
            _form.update { it.copy(saving = false) }
        }
    }

    fun delete() = launchSafe {
        val id = staffId ?: return@launchSafe
        when (val result = repo.delete(id)) {
            is DataResult.Success -> events.tryEmit(EditEvent.Deleted)
            is DataResult.Failure -> showMessage(if (result.error == DataError.IN_USE) R.string.staff_delete_in_use else result.error.messageRes)
        }
    }
}

@Composable
fun StaffEditScreen(staffId: Long?, onBack: () -> Unit, onDeleted: () -> Unit) {
    val vm = appViewModel(key = "staff_edit_$staffId") { StaffEditViewModel(it, staffId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(vm) { vm.events.collect { if (it == EditEvent.Deleted) onDeleted() else onBack() } }

    Scaffold(
        topBar = {
            SalonTopBar(
                title = stringResource(if (staffId == null) R.string.staff_add else R.string.staff_edit),
                onBack = onBack,
                actions = {
                    if (staffId != null) {
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
            FormTextField(
                form.name,
                { v -> vm.update { it.copy(name = v, nameError = null) } },
                stringResource(R.string.field_name),
                error = form.nameError?.let { stringResource(it.messageRes) },
                capitalization = KeyboardCapitalization.Words,
            )
            FormTextField(
                form.phone,
                { v -> vm.update { it.copy(phone = v, phoneError = null) } },
                stringResource(R.string.field_phone_optional),
                error = form.phoneError?.let { stringResource(it.messageRes) },
                keyboardType = KeyboardType.Phone,
            )
            DropdownField(
                label = stringResource(R.string.field_role),
                options = StaffRole.entries.toList(),
                selected = form.role,
                optionLabel = { stringResource(it.labelRes) },
                onSelected = { r -> vm.update { it.copy(role = r) } },
            )
            DropdownField(
                label = stringResource(R.string.field_salary_type),
                options = SalaryType.entries.toList(),
                selected = form.salaryType,
                optionLabel = { stringResource(it.labelRes) },
                onSelected = { t -> vm.update { it.copy(salaryType = t) } },
            )
            if (form.salaryType.hasCommission) {
                FormTextField(
                    form.commission,
                    { v -> vm.update { it.copy(commission = v.filter { c -> c.isDigit() || c == '.' }.take(6), commissionError = null) } },
                    stringResource(R.string.field_commission),
                    error = form.commissionError?.let { stringResource(it.messageRes) },
                    keyboardType = KeyboardType.Decimal,
                    suffix = "%",
                )
            }
            if (form.salaryType.hasFixed) {
                AmountField(
                    form.fixedSalary,
                    { v -> vm.update { it.copy(fixedSalary = v, salaryError = null) } },
                    stringResource(R.string.field_fixed_salary),
                    money.config.symbol,
                    error = form.salaryError?.let { stringResource(it.messageRes) },
                )
            }
            DateField(
                stringResource(R.string.field_joined_optional),
                form.joinedOn,
                { d -> vm.update { it.copy(joinedOn = d) } },
                allowClear = true,
                maxDate = DateTimeUtils.today(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_active), modifier = Modifier.weight(1f))
                Switch(checked = form.active, onCheckedChange = { v -> vm.update { it.copy(active = v) } })
            }
            Button(onClick = vm::save, enabled = !form.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.staff_delete_title),
            message = stringResource(R.string.staff_delete_message),
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
