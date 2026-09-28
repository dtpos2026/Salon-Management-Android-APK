package com.dtpos.salonmanager.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileForm(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val currencyCode: String = CurrencyConfig.DEFAULT_CODE,
    val currencySymbol: String = CurrencyConfig.DEFAULT_SYMBOL,
    val logoPath: String? = null,
    val logoVersion: Int = 0,
    val nameError: ValidationError? = null,
    val phoneError: ValidationError? = null,
)

class BusinessProfileViewModel(private val container: AppContainer) : BaseViewModel() {
    private val repo = container.businessRepository
    private val _form = MutableStateFlow(ProfileForm())
    val form: StateFlow<ProfileForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getProfile()?.let { p ->
                _form.value = ProfileForm(p.name, p.phone.orEmpty(), p.address.orEmpty(), p.currency.code, p.currency.symbol, p.logoPath)
            }
        }
    }

    fun update(transform: (ProfileForm) -> ProfileForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        val name = Validators.requiredName(f.name)
        val phone = Validators.phone(f.phone)
        _form.update { it.copy(nameError = name.errorOrNull, phoneError = phone.errorOrNull) }
        if (name !is FieldResult.Valid || phone !is FieldResult.Valid) return
        launchSafe {
            repo.updateProfile(name.value, phone.value, f.address.trim().ifEmpty { null })
            repo.updateCurrency(
                CurrencyConfig(
                    f.currencyCode.trim().uppercase().ifEmpty { CurrencyConfig.DEFAULT_CODE },
                    f.currencySymbol.trim().ifEmpty { CurrencyConfig.DEFAULT_SYMBOL },
                ),
            )
            showMessage(R.string.saved)
        }
    }

    fun importLogo(uri: android.net.Uri) = launchSafe {
        val path = container.logoStore.importFrom(uri)
        if (path == null) {
            showMessage(R.string.profile_logo_failed)
        } else {
            repo.setLogoPath(path)
            _form.update { it.copy(logoPath = path, logoVersion = it.logoVersion + 1) }
            showMessage(R.string.profile_logo_saved)
        }
    }

    fun removeLogo() = launchSafe {
        container.logoStore.delete()
        repo.setLogoPath(null)
        _form.update { it.copy(logoPath = null, logoVersion = it.logoVersion + 1) }
    }
}

@Composable
fun BusinessProfileScreen(onBack: () -> Unit) {
    val vm = appViewModel { BusinessProfileViewModel(it) }
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val logoStore = LocalAppContainer.current.logoStore
    MessageEffect(vm.messages, snackbar)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::importLogo) }
    val logo = remember(form.logoPath, form.logoVersion) { logoStore.loadBitmap(form.logoPath, 256)?.asImageBitmap() }

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.settings_business), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.profile_logo))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logo != null) {
                        Image(logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.ContentCut, contentDescription = null, tint = SalonTheme.extended.gold, modifier = Modifier.size(40.dp))
                    }
                }
                Column {
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.profile_change_logo)) }
                    if (form.logoPath != null) {
                        TextButton(onClick = vm::removeLogo) { Text(stringResource(R.string.profile_remove_logo)) }
                    }
                }
            }
            SectionHeader(stringResource(R.string.profile_details))
            FormTextField(
                form.name,
                { v -> vm.update { it.copy(name = v, nameError = null) } },
                stringResource(R.string.field_salon_name),
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
            FormTextField(form.address, { v -> vm.update { it.copy(address = v) } }, stringResource(R.string.field_address_optional), singleLine = false)
            SectionHeader(stringResource(R.string.profile_currency))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    form.currencyCode,
                    { v -> vm.update { it.copy(currencyCode = v.take(3).uppercase()) } },
                    stringResource(R.string.field_currency_code),
                    modifier = Modifier.weight(1f),
                    capitalization = KeyboardCapitalization.Characters,
                )
                FormTextField(
                    form.currencySymbol,
                    { v -> vm.update { it.copy(currencySymbol = v.take(4)) } },
                    stringResource(R.string.field_currency_symbol),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}
