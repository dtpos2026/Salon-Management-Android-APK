package com.dtpos.salonmanager.presentation.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceiptForm(
    val prefix: String = "SAL",
    val header: String = "",
    val footer: String = "",
    val showLogo: Boolean = true,
    val showStaff: Boolean = true,
    val nextNumber: String = "",
    val prefixError: ValidationError? = null,
)

class ReceiptSettingsViewModel(container: AppContainer) : BaseViewModel() {
    private val repo = container.businessRepository
    private val _form = MutableStateFlow(ReceiptForm())
    val form: StateFlow<ReceiptForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getProfile()?.let { p ->
                _form.value = ReceiptForm(
                    prefix = p.receiptPrefix,
                    header = p.receiptHeaderNote.orEmpty(),
                    footer = p.receiptFooter.orEmpty(),
                    showLogo = p.showLogoOnReceipt,
                    showStaff = p.showStaffOnReceipt,
                    nextNumber = repo.nextReceiptNumberPreview(),
                )
            }
        }
    }

    fun update(transform: (ReceiptForm) -> ReceiptForm) = _form.update(transform)

    fun onPrefix(value: String) {
        _form.update { it.copy(prefix = value.uppercase().filter(Char::isLetterOrDigit).take(8), prefixError = null) }
        viewModelScope.launch {
            val preview = Validators.receiptPrefix(_form.value.prefix).valueOrNull?.let { repo.nextReceiptNumberPreview(it) }
            _form.update { it.copy(nextNumber = preview ?: "") }
        }
    }

    fun save() {
        val f = _form.value
        val prefix = Validators.receiptPrefix(f.prefix)
        val header = Validators.optionalText(f.header, 120)
        val footer = Validators.optionalText(f.footer, 200)
        _form.update { it.copy(prefixError = prefix.errorOrNull) }
        if (prefix !is FieldResult.Valid || header !is FieldResult.Valid || footer !is FieldResult.Valid) {
            if (header is FieldResult.Invalid || footer is FieldResult.Invalid) showMessage(R.string.error_too_long)
            return
        }
        launchSafe {
            repo.updateReceiptSettings(prefix.value, header.value, footer.value, f.showLogo, f.showStaff)
            showMessage(R.string.saved)
        }
    }
}

@Composable
fun ReceiptSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReceiptSettingsViewModel(it) }
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.settings_receipt), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormTextField(
                form.prefix,
                vm::onPrefix,
                stringResource(R.string.receipt_prefix),
                error = form.prefixError?.let { stringResource(it.messageRes) },
                supporting = if (form.nextNumber.isNotEmpty()) stringResource(R.string.receipt_next_number, form.nextNumber) else null,
                capitalization = KeyboardCapitalization.Characters,
            )
            Text(
                stringResource(R.string.receipt_prefix_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(form.header, { v -> vm.update { it.copy(header = v) } }, stringResource(R.string.receipt_header_note), singleLine = false)
            FormTextField(
                form.footer,
                { v -> vm.update { it.copy(footer = v) } },
                stringResource(R.string.receipt_footer),
                singleLine = false,
                supporting = stringResource(R.string.receipt_footer_hint),
            )
            ContentCard {
                ToggleRow(stringResource(R.string.receipt_show_logo), form.showLogo) { v -> vm.update { it.copy(showLogo = v) } }
                ToggleRow(stringResource(R.string.receipt_show_staff), form.showStaff) { v -> vm.update { it.copy(showStaff = v) } }
            }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
