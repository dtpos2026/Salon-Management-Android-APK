package com.dtpos.salonmanager.presentation.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.FormTextField
import androidx.compose.ui.graphics.Brush
import com.dtpos.salonmanager.presentation.components.BrandMonogram
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SetupState(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val currencyCode: String = CurrencyConfig.DEFAULT_CODE,
    val currencySymbol: String = CurrencyConfig.DEFAULT_SYMBOL,
    val addServices: Boolean = true,
    val demoData: Boolean = false,
    val nameError: ValidationError? = null,
    val phoneError: ValidationError? = null,
    val saving: Boolean = false,
)

class SetupViewModel(private val container: AppContainer) : BaseViewModel() {
    // Pre-filled from the salon's approved account so the owner does not type it twice.
    private val _state = MutableStateFlow(
        container.accountManager.cachedAccount()?.account?.let { account ->
            SetupState(
                name = account.salonName,
                phone = account.phone,
                address = listOf(account.address, account.city).filter { it.isNotBlank() }.joinToString(", "),
            )
        } ?: SetupState(),
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun update(transform: (SetupState) -> SetupState) = _state.update(transform)

    fun finish() {
        val s = _state.value
        val name = Validators.requiredName(s.name)
        val phone = Validators.phone(s.phone)
        _state.update { it.copy(nameError = name.errorOrNull, phoneError = phone.errorOrNull) }
        if (name !is FieldResult.Valid || phone !is FieldResult.Valid) return
        _state.update { it.copy(saving = true) }
        launchSafe {
            try {
                // Seed first: completing setup switches the UI and clears this ViewModel.
                if (s.demoData) container.demoDataSeeder.seed()
                container.businessRepository.completeSetup(
                    name = name.value,
                    phone = phone.value,
                    address = s.address.trim().ifEmpty { null },
                    currency = CurrencyConfig(
                        s.currencyCode.trim().uppercase().ifEmpty { CurrencyConfig.DEFAULT_CODE },
                        s.currencySymbol.trim().ifEmpty { CurrencyConfig.DEFAULT_SYMBOL },
                    ),
                    addDefaultServices = s.addServices || s.demoData,
                )
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

/** First-run onboarding: salon name, contact details, currency and optional starter data. */
@Composable
fun SetupScreen() {
    val vm = appViewModel { SetupViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val glass = SalonTheme.glass
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(glass.backgroundTop, glass.backgroundBottom)))
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMonogram(width = 120.dp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.setup_welcome), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormTextField(
                value = state.name,
                onValueChange = { v -> vm.update { it.copy(name = v, nameError = null) } },
                label = stringResource(R.string.field_salon_name),
                error = state.nameError?.let { stringResource(it.messageRes) },
                capitalization = KeyboardCapitalization.Words,
            )
            FormTextField(
                value = state.phone,
                onValueChange = { v -> vm.update { it.copy(phone = v, phoneError = null) } },
                label = stringResource(R.string.field_phone_optional),
                error = state.phoneError?.let { stringResource(it.messageRes) },
                keyboardType = KeyboardType.Phone,
            )
            FormTextField(
                value = state.address,
                onValueChange = { v -> vm.update { it.copy(address = v) } },
                label = stringResource(R.string.field_address_optional),
                singleLine = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    value = state.currencyCode,
                    onValueChange = { v -> vm.update { it.copy(currencyCode = v.take(3).uppercase()) } },
                    label = stringResource(R.string.field_currency_code),
                    modifier = Modifier.weight(1f),
                    capitalization = KeyboardCapitalization.Characters,
                )
                FormTextField(
                    value = state.currencySymbol,
                    onValueChange = { v -> vm.update { it.copy(currencySymbol = v.take(4)) } },
                    label = stringResource(R.string.field_currency_symbol),
                    modifier = Modifier.weight(1f),
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(8.dp)) {
                    CheckRow(
                        checked = state.addServices,
                        title = stringResource(R.string.setup_add_services),
                        subtitle = stringResource(R.string.setup_add_services_hint),
                        onChange = { v -> vm.update { it.copy(addServices = v) } },
                    )
                    CheckRow(
                        checked = state.demoData,
                        title = stringResource(R.string.setup_demo),
                        subtitle = stringResource(R.string.setup_demo_hint),
                        onChange = { v -> vm.update { it.copy(demoData = v) } },
                    )
                }
            }

            Button(
                onClick = vm::finish,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(stringResource(R.string.setup_start))
            }
            Text(
                stringResource(R.string.setup_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, subtitle: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
