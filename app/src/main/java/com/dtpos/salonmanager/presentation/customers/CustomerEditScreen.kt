package com.dtpos.salonmanager.presentation.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.repository.CustomerInput
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.DateField
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SegmentedChoice
import com.dtpos.salonmanager.core.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CustomerForm(
    val name: String = "",
    val phone: String = "",
    val gender: Gender = Gender.UNSPECIFIED,
    val dateOfBirth: LocalDate? = null,
    val address: String = "",
    val notes: String = "",
    val nameError: ValidationError? = null,
    val phoneError: ValidationError? = null,
    val saving: Boolean = false,
)

sealed interface EditEvent {
    data object Saved : EditEvent
    data object Deleted : EditEvent
}

class CustomerEditViewModel(container: AppContainer, private val customerId: Long?) : BaseViewModel() {
    private val repo = container.customerRepository
    private val _form = MutableStateFlow(CustomerForm())
    val form: StateFlow<CustomerForm> = _form.asStateFlow()
    val events = MutableSharedFlow<EditEvent>(extraBufferCapacity = 1)

    init {
        if (customerId != null) {
            viewModelScope.launch {
                repo.get(customerId)?.let { c ->
                    _form.value = CustomerForm(
                        name = c.name,
                        phone = c.phone.orEmpty(),
                        gender = c.gender,
                        dateOfBirth = c.dateOfBirth?.let(LocalDate::ofEpochDay),
                        address = c.address.orEmpty(),
                        notes = c.notes.orEmpty(),
                    )
                }
            }
        }
    }

    fun update(transform: (CustomerForm) -> CustomerForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        val name = Validators.requiredName(f.name)
        val phone = Validators.phone(f.phone)
        val address = Validators.optionalText(f.address, 200)
        val notes = Validators.optionalText(f.notes)
        _form.update { it.copy(nameError = name.errorOrNull, phoneError = phone.errorOrNull) }
        if (name !is FieldResult.Valid || phone !is FieldResult.Valid || address !is FieldResult.Valid || notes !is FieldResult.Valid) {
            if (address is FieldResult.Invalid || notes is FieldResult.Invalid) showMessage(R.string.error_too_long)
            return
        }
        _form.update { it.copy(saving = true) }
        launchSafe {
            val input = CustomerInput(name.value, phone.value, f.gender, f.dateOfBirth?.toEpochDay(), address.value, notes.value)
            when (val result = repo.save(input, customerId)) {
                is DataResult.Success -> events.tryEmit(EditEvent.Saved)
                is DataResult.Failure -> {
                    if (result.error == DataError.DUPLICATE_PHONE) _form.update { it.copy(phoneError = ValidationError.DUPLICATE_PHONE) }
                    showMessage(result.error.messageRes)
                }
            }
            _form.update { it.copy(saving = false) }
        }
    }

    fun delete() = launchSafe {
        val id = customerId ?: return@launchSafe
        when (val result = repo.delete(id)) {
            is DataResult.Success -> events.tryEmit(EditEvent.Deleted)
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }
}

@Composable
fun CustomerEditScreen(customerId: Long?, onBack: () -> Unit, onDeleted: () -> Unit) {
    val vm = appViewModel(key = "customer_edit_$customerId") { CustomerEditViewModel(it, customerId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(vm) {
        vm.events.collect { if (it == EditEvent.Deleted) onDeleted() else onBack() }
    }

    Scaffold(
        topBar = {
            SalonTopBar(
                title = stringResource(if (customerId == null) R.string.customers_add else R.string.customers_edit),
                onBack = onBack,
                actions = {
                    if (customerId != null) {
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
            Text(stringResource(R.string.field_gender))
            SegmentedChoice(
                options = Gender.entries.toList(),
                selected = form.gender,
                label = { stringResource(it.labelRes) },
                onSelect = { g -> vm.update { it.copy(gender = g) } },
            )
            DateField(
                label = stringResource(R.string.field_dob_optional),
                date = form.dateOfBirth,
                onDateChange = { d -> vm.update { it.copy(dateOfBirth = d) } },
                allowClear = true,
                maxDate = DateTimeUtils.today(),
            )
            FormTextField(form.address, { v -> vm.update { it.copy(address = v) } }, stringResource(R.string.field_address_optional), singleLine = false)
            FormTextField(form.notes, { v -> vm.update { it.copy(notes = v) } }, stringResource(R.string.field_notes_optional), singleLine = false, minLines = 2)
            Button(onClick = vm::save, enabled = !form.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.customers_delete_title),
            message = stringResource(R.string.customers_delete_message),
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
