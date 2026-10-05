package com.dtpos.salonmanager.presentation.services

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.ServiceInput
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SearchField
import com.dtpos.salonmanager.presentation.components.SectionHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ServiceForm(
    val id: Long? = null,
    val name: String = "",
    val category: String = "",
    val price: String = "",
    val duration: String = "",
    val active: Boolean = true,
    val imagePath: String? = null,
    val boldName: Boolean = false,
    val boldPrice: Boolean = false,
    val nameError: ValidationError? = null,
    val categoryError: ValidationError? = null,
    val priceError: ValidationError? = null,
    val durationError: ValidationError? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ServicesViewModel(private val container: AppContainer) : BaseViewModel() {
    private val repo = container.serviceRepository
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query

    val services: StateFlow<List<ServiceEntity>?> = query.flatMapLatest { repo.observeAll(it) }
        .map<List<ServiceEntity>, List<ServiceEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<String>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _form = MutableStateFlow<ServiceForm?>(null)
    val form: StateFlow<ServiceForm?> = _form

    fun onQuery(value: String) {
        query.value = value
    }

    fun newService() {
        _form.value = ServiceForm(category = categories.value.firstOrNull() ?: "")
    }

    fun edit(service: ServiceEntity) {
        _form.value = ServiceForm(
            id = service.id,
            name = service.name,
            category = service.category,
            price = Money.toInput(service.priceMinor),
            duration = if (service.durationMinutes > 0) service.durationMinutes.toString() else "",
            active = service.isActive,
            imagePath = service.imagePath,
            boldName = service.boldName,
            boldPrice = service.boldPrice,
        )
    }

    /** Saves the picked photo into the app and shows it in the form (kept when the form is saved). */
    fun pickImage(uri: android.net.Uri) = launchSafe {
        val path = container.serviceImageStore.importFrom(uri)
        if (path == null) showMessage(R.string.services_photo_failed) else updateForm { it.copy(imagePath = path) }
    }

    fun updateForm(transform: (ServiceForm) -> ServiceForm) {
        _form.value = _form.value?.let(transform)
    }

    fun closeForm() {
        _form.value = null
    }

    fun save() {
        val f = _form.value ?: return
        val name = Validators.requiredName(f.name)
        val category = Validators.requiredName(f.category, 30)
        val price = Validators.amount(f.price)
        val duration = Validators.durationMinutes(f.duration)
        _form.value = f.copy(
            nameError = name.errorOrNull,
            categoryError = category.errorOrNull,
            priceError = price.errorOrNull,
            durationError = duration.errorOrNull,
        )
        if (name !is FieldResult.Valid || category !is FieldResult.Valid || price !is FieldResult.Valid || duration !is FieldResult.Valid) return
        launchSafe {
            val previousImage = f.id?.let { repo.get(it)?.imagePath }
            val input = ServiceInput(name.value, category.value, price.value, duration.value, f.active, f.imagePath, f.boldName, f.boldPrice)
            when (val result = repo.save(input, f.id)) {
                is DataResult.Success -> {
                    if (previousImage != null && previousImage != f.imagePath) container.serviceImageStore.delete(previousImage)
                    closeForm()
                    showMessage(R.string.saved)
                }
                is DataResult.Failure -> {
                    if (result.error == DataError.DUPLICATE_NAME) updateForm { it.copy(nameError = ValidationError.DUPLICATE_NAME) }
                    else showMessage(result.error.messageRes)
                }
            }
        }
    }

    fun toggleActive(service: ServiceEntity) = launchSafe { repo.setActive(service.id, !service.isActive) }

    fun delete(id: Long) = launchSafe {
        val image = repo.get(id)?.imagePath
        when (val result = repo.delete(id)) {
            is DataResult.Success -> {
                container.serviceImageStore.delete(image)
                closeForm()
                showMessage(R.string.deleted)
            }
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }

    fun addDefaults() = launchSafe {
        repo.addDefaultServices()
        showMessage(R.string.services_defaults_added)
    }
}

@Composable
fun ServicesScreen(onBack: () -> Unit) {
    val vm = appViewModel { ServicesViewModel(it) }
    val services by vm.services.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = {
            SalonTopBar(
                stringResource(R.string.nav_services),
                onBack = onBack,
                actions = {
                    IconButton(onClick = vm::addDefaults) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.services_add_defaults))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::newService,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.services_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SearchField(query, vm::onQuery, stringResource(R.string.services_search)) }
            val list = services
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.ContentCut,
                        title = stringResource(R.string.pos_no_services_title),
                        message = stringResource(R.string.services_empty_message),
                        actionLabel = stringResource(R.string.services_add_defaults),
                        onAction = vm::addDefaults,
                    )
                }
            }
            list.orEmpty().groupBy { it.category }.forEach { (category, items) ->
                item(key = "header_$category") { SectionHeader(category) }
                items(items, key = { it.id }) { service ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { vm.edit(service) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        ListItem(
                            leadingContent = {
                                ServicePhoto(service.imagePath, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), maxSize = 160)
                            },
                            headlineContent = { Text(service.name, fontWeight = if (service.boldName) FontWeight.Bold else null) },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append(money.format(service.priceMinor))
                                        if (service.durationMinutes > 0) append(" · ").append(stringResource(R.string.services_minutes, service.durationMinutes))
                                        if (!service.isActive) append(" · ").append(stringResource(R.string.status_inactive))
                                    },
                                )
                            },
                            trailingContent = { Switch(checked = service.isActive, onCheckedChange = { vm.toggleActive(service) }) },
                        )
                    }
                }
            }
        }
    }

    form?.let { f -> ServiceDialog(f, categories, money.config.symbol, vm) }
}

@Composable
private fun ServiceDialog(form: ServiceForm, categories: List<String>, symbol: String, vm: ServicesViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::pickImage) }
    AlertDialog(
        onDismissRequest = vm::closeForm,
        title = { Text(stringResource(if (form.id == null) R.string.services_add else R.string.services_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServicePhoto(form.imagePath, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), maxSize = 200)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(if (form.imagePath == null) R.string.services_photo_add else R.string.services_photo_change))
                        }
                        if (form.imagePath != null) {
                            TextButton(onClick = { vm.updateForm { it.copy(imagePath = null) } }) {
                                Text(stringResource(R.string.services_photo_remove), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                FormTextField(
                    form.name,
                    { v -> vm.updateForm { it.copy(name = v, nameError = null) } },
                    stringResource(R.string.field_service_name),
                    error = form.nameError?.let { stringResource(it.messageRes) },
                    capitalization = KeyboardCapitalization.Words,
                )
                FormTextField(
                    form.category,
                    { v -> vm.updateForm { it.copy(category = v, categoryError = null) } },
                    stringResource(R.string.field_category),
                    error = form.categoryError?.let { stringResource(it.messageRes) },
                    capitalization = KeyboardCapitalization.Words,
                )
                if (categories.isNotEmpty()) {
                    com.dtpos.salonmanager.presentation.components.ChoiceChips(
                        options = categories,
                        selected = form.category,
                        label = { it },
                        onSelect = { c -> vm.updateForm { it.copy(category = c, categoryError = null) } },
                    )
                }
                AmountField(
                    form.price,
                    { v -> vm.updateForm { it.copy(price = v, priceError = null) } },
                    stringResource(R.string.field_price),
                    symbol,
                    error = form.priceError?.let { stringResource(it.messageRes) },
                )
                FormTextField(
                    form.duration,
                    { v -> vm.updateForm { it.copy(duration = v.filter(Char::isDigit).take(3), durationError = null) } },
                    stringResource(R.string.field_duration),
                    error = form.durationError?.let { stringResource(it.messageRes) },
                    keyboardType = KeyboardType.Number,
                    suffix = stringResource(R.string.unit_minutes),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_active), modifier = Modifier.weight(1f))
                    Switch(checked = form.active, onCheckedChange = { v -> vm.updateForm { it.copy(active = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.services_bold_name), modifier = Modifier.weight(1f))
                    Switch(checked = form.boldName, onCheckedChange = { v -> vm.updateForm { it.copy(boldName = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.services_bold_price), modifier = Modifier.weight(1f))
                    Switch(checked = form.boldPrice, onCheckedChange = { v -> vm.updateForm { it.copy(boldPrice = v) } })
                }
                if (form.id != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = vm::save) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = vm::closeForm) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmDelete && form.id != null) {
        ConfirmDialog(
            title = stringResource(R.string.services_delete_title),
            message = stringResource(R.string.services_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete(form.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
