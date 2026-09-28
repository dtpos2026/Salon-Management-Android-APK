package com.dtpos.salonmanager.presentation.sales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.DiscountType
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ChoiceChips
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.DropdownField
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.InfoBanner
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SearchField
import com.dtpos.salonmanager.presentation.components.SegmentedChoice
import com.dtpos.salonmanager.presentation.theme.SalonTheme

@Composable
fun PosScreen(initialCustomerId: Long?, onBack: () -> Unit, onSaleCompleted: (Long) -> Unit) {
    val vm = appViewModel { PosViewModel(it, initialCustomerId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCustomerSheet by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(vm) { vm.completed.collect { onSaleCompleted(it) } }

    val exit: () -> Unit = {
        if (state.draft.cart.isNotEmpty()) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = state.draft.step == PosStep.CHECKOUT) { vm.goTo(PosStep.SERVICES) }
    BackHandler(enabled = state.draft.step == PosStep.SERVICES && state.draft.cart.isNotEmpty()) { confirmDiscard = true }

    Scaffold(
        topBar = {
            SalonTopBar(
                title = stringResource(if (state.draft.step == PosStep.SERVICES) R.string.pos_title else R.string.pos_checkout),
                onBack = { if (state.draft.step == PosStep.CHECKOUT) vm.goTo(PosStep.SERVICES) else exit() },
                actions = {
                    if (state.draft.cart.isNotEmpty()) {
                        IconButton(onClick = { confirmDiscard = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.pos_clear))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val wide = maxWidth >= 840.dp
            Column(Modifier.fillMaxSize()) {
                if (state.readOnly) {
                    InfoBanner(
                        text = stringResource(R.string.error_read_only),
                        icon = Icons.Filled.Close,
                        container = SalonTheme.extended.negativeContainer,
                        content = SalonTheme.extended.negative,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        ServicesPane(state, vm, { showCustomerSheet = true }, Modifier.weight(1.3f), showCartBar = false)
                        VerticalDivider()
                        CheckoutPane(state, vm, { showCustomerSheet = true }, Modifier.weight(1f).fillMaxHeight())
                    }
                } else if (state.draft.step == PosStep.SERVICES) {
                    ServicesPane(state, vm, { showCustomerSheet = true }, Modifier.fillMaxSize(), showCartBar = true)
                } else {
                    CheckoutPane(state, vm, { showCustomerSheet = true }, Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showCustomerSheet) CustomerPickerSheet(vm, onDismiss = { showCustomerSheet = false })
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.pos_discard_title),
            message = stringResource(R.string.pos_discard_message),
            confirmLabel = stringResource(R.string.pos_discard_confirm),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                vm.clearCart()
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun CustomerChip(state: PosUiState, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                state.draft.customer?.name ?: stringResource(R.string.walk_in_customer),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun ServicesPane(
    state: PosUiState,
    vm: PosViewModel,
    onPickCustomer: () -> Unit,
    modifier: Modifier,
    showCartBar: Boolean,
) {
    val money = LocalMoney.current
    Column(modifier) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pos_customer), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                CustomerChip(state, onPickCustomer)
            }
            if (state.staff.isNotEmpty()) {
                Text(stringResource(R.string.pos_served_by), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    options = listOf<Long?>(null) + state.staff.map { it.id },
                    selected = state.draft.defaultStaffId,
                    label = { id -> id?.let { i -> state.staff.firstOrNull { it.id == i }?.name } ?: stringResource(R.string.pos_no_staff) },
                    onSelect = vm::onDefaultStaff,
                )
            }
            SearchField(state.draft.serviceQuery, vm::onServiceQuery, stringResource(R.string.pos_search_services))
            if (state.categories.size > 1) {
                ChoiceChips(
                    options = listOf<String?>(null) + state.categories,
                    selected = state.draft.category,
                    label = { it ?: stringResource(R.string.filter_all) },
                    onSelect = vm::onCategory,
                )
            }
        }
        if (state.services.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.ContentCut,
                title = stringResource(R.string.pos_no_services_title),
                message = stringResource(R.string.pos_no_services_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filteredServices, key = { it.id }) { service ->
                    ServiceTile(service, state.quantityOf(service.id), money.format(service.priceMinor)) { vm.addService(service) }
                }
            }
        }
        if (showCartBar) {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgedBox(badge = { if (state.itemCount > 0) Badge { Text(state.itemCount.toString()) } }) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pos_total), style = MaterialTheme.typography.labelMedium)
                        Text(money.format(state.totals.totalMinor), style = MaterialTheme.typography.titleLarge)
                    }
                    Button(onClick = { vm.goTo(PosStep.CHECKOUT) }, enabled = state.draft.cart.isNotEmpty()) {
                        Text(stringResource(R.string.pos_checkout))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTile(service: ServiceEntity, quantity: Int, price: String, onClick: () -> Unit) {
    val selected = quantity > 0
    Card(
        modifier = Modifier.fillMaxWidth().height(96.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    service.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) Badge { Text(quantity.toString()) }
            }
            Text(price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CheckoutPane(state: PosUiState, vm: PosViewModel, onPickCustomer: () -> Unit, modifier: Modifier) {
    val money = LocalMoney.current
    var editing by remember { mutableStateOf<CartLine?>(null) }
    val symbol = money.config.symbol
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.draft.customer?.name ?: stringResource(R.string.walk_in_customer), style = MaterialTheme.typography.titleMedium)
                        state.draft.customer?.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = onPickCustomer) { Text(stringResource(R.string.action_change)) }
                }
            }
        }
        item { Text(stringResource(R.string.pos_cart), style = MaterialTheme.typography.titleMedium) }
        if (state.draft.cart.isEmpty()) {
            item { Text(stringResource(R.string.pos_cart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.draft.cart, key = { it.key }) { line ->
            CartLineCard(line, state, vm, onEdit = { editing = line })
        }
        item {
            ContentCard {
                Text(stringResource(R.string.pos_discount), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedChoice(
                    options = DiscountType.entries.toList(),
                    selected = state.draft.discountType,
                    label = { stringResource(if (it == DiscountType.AMOUNT) R.string.pos_discount_amount else R.string.pos_discount_percent) },
                    onSelect = vm::onDiscountType,
                )
                Spacer(Modifier.height(8.dp))
                if (state.draft.discountType == DiscountType.AMOUNT) {
                    AmountField(
                        value = state.draft.discountInput,
                        onValueChange = vm::onDiscountInput,
                        label = stringResource(R.string.pos_discount_value),
                        currencySymbol = symbol,
                        error = if (!state.discountValid) stringResource(R.string.error_invalid_amount) else null,
                    )
                } else {
                    FormTextField(
                        value = state.draft.discountInput,
                        onValueChange = { v -> vm.onDiscountInput(v.filter { it.isDigit() || it == '.' }.take(6)) },
                        label = stringResource(R.string.pos_discount_value),
                        suffix = "%",
                        keyboardType = KeyboardType.Decimal,
                        error = if (!state.discountValid) stringResource(R.string.error_invalid_percent) else null,
                    )
                }
            }
        }
        item {
            ContentCard {
                LabeledValueRow(stringResource(R.string.pos_subtotal), money.format(state.totals.subtotalMinor))
                if (state.totals.totalDiscountMinor > 0) {
                    LabeledValueRow(
                        stringResource(R.string.pos_discount),
                        "-" + money.format(state.totals.totalDiscountMinor),
                        valueColor = SalonTheme.extended.negative,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                LabeledValueRow(stringResource(R.string.pos_total), money.format(state.totals.totalMinor), emphasize = true)
            }
        }
        item {
            ContentCard {
                Text(stringResource(R.string.pos_payment_method), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedChoice(
                    options = PaymentMethod.entries.toList(),
                    selected = state.draft.paymentMethod,
                    label = { stringResource(it.labelRes) },
                    onSelect = vm::onPaymentMethod,
                )
                if (state.draft.paymentMethod == PaymentMethod.CASH) {
                    Spacer(Modifier.height(8.dp))
                    AmountField(
                        value = state.draft.tenderedInput,
                        onValueChange = vm::onTendered,
                        label = stringResource(R.string.pos_cash_received),
                        currencySymbol = symbol,
                        supporting = state.tenderedMinor?.takeIf { it >= state.totals.totalMinor }?.let {
                            stringResource(R.string.pos_change_due, money.format(state.changeMinor))
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                FormTextField(
                    value = state.draft.note,
                    onValueChange = vm::onNote,
                    label = stringResource(R.string.field_note_optional),
                    singleLine = false,
                )
            }
        }
        item {
            Button(
                onClick = vm::completeSale,
                enabled = !state.draft.saving && state.draft.cart.isNotEmpty() && !state.readOnly,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.draft.saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        stringResource(R.string.pos_complete, money.format(state.totals.totalMinor)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    editing?.let { line ->
        LineEditDialog(
            line = line,
            currencySymbol = symbol,
            onSave = { price, discount -> vm.updateLine(line.key, price, discount).also { ok -> if (ok) editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CartLineCard(line: CartLine, state: PosUiState, vm: PosViewModel, onEdit: () -> Unit) {
    val money = LocalMoney.current
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.serviceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.pos_line_price, money.format(line.unitPriceMinor), line.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (line.discountMinor > 0) {
                    Text(
                        stringResource(R.string.pos_line_discount, money.format(line.discountMinor)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SalonTheme.extended.negative,
                    )
                }
            }
            Text(money.format(line.grossMinor - line.discountMinor), style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.changeQuantity(line.key, -1) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.pos_decrease))
            }
            Text(line.quantity.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.changeQuantity(line.key, 1) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pos_increase))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
            IconButton(onClick = { vm.removeLine(line.key) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
            }
        }
        if (state.staff.isNotEmpty()) {
            DropdownField(
                label = stringResource(R.string.pos_staff),
                options = listOf<Long?>(null) + state.staff.map { it.id },
                selected = line.staffId,
                optionLabel = { id -> id?.let { i -> state.staff.firstOrNull { it.id == i }?.name ?: line.staffName } ?: stringResource(R.string.pos_no_staff) },
                onSelected = { vm.setLineStaff(line.key, it) },
            )
        }
    }
}

@Composable
private fun LineEditDialog(line: CartLine, currencySymbol: String, onSave: (String, String) -> Boolean, onDismiss: () -> Unit) {
    var price by remember { mutableStateOf(Money.toInput(line.unitPriceMinor)) }
    var discount by remember { mutableStateOf(Money.toInput(line.discountMinor)) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.serviceName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(price, { price = it; error = false }, stringResource(R.string.pos_unit_price), currencySymbol)
                AmountField(
                    discount,
                    { discount = it; error = false },
                    stringResource(R.string.pos_line_discount_label),
                    currencySymbol,
                    error = if (error) stringResource(R.string.pos_line_invalid) else null,
                )
            }
        },
        confirmButton = { Button(onClick = { error = !onSave(price, discount) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerPickerSheet(vm: PosViewModel, onDismiss: () -> Unit) {
    val query by vm.customerQueryText.collectAsStateWithLifecycle()
    val results by vm.customerResults.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding()) {
            Text(stringResource(R.string.pos_select_customer), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (adding) {
                FormTextField(newName, { newName = it }, stringResource(R.string.field_name), capitalization = KeyboardCapitalization.Words)
                Spacer(Modifier.height(8.dp))
                FormTextField(newPhone, { newPhone = it }, stringResource(R.string.field_phone_optional), keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { adding = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = { vm.quickAddCustomer(newName, newPhone, onDismiss) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                Spacer(Modifier.height(24.dp))
            } else {
                SearchField(query, vm::onCustomerQuery, stringResource(R.string.customers_search))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.selectCustomer(null)
                        onDismiss()
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.walk_in_customer)) }
                    Button(onClick = {
                        newName = query.takeIf { q -> q.any { it.isLetter() } } ?: ""
                        newPhone = query.takeIf { q -> q.isNotEmpty() && q.all { it.isDigit() || it in "+- " } } ?: ""
                        adding = true
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pos_new_customer))
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(results, key = { it.customer.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.customer.name) },
                            supportingContent = { row.customer.phone?.let { Text(it) } },
                            trailingContent = {
                                Text(
                                    stringResource(R.string.customers_visits_count, row.visitCount),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.clickable {
                                vm.selectCustomer(row.customer)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
