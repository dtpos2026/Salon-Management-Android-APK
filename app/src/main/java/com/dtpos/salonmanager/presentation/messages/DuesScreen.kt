package com.dtpos.salonmanager.presentation.messages

import com.dtpos.salonmanager.services.export.WhatsAppResult
import com.dtpos.salonmanager.presentation.common.showWhatsAppResult
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.BusinessProfile
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SegmentedChoice
import com.dtpos.salonmanager.services.export.ExternalApps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** WhatsApp green, used for every "send on WhatsApp" button. */
val WhatsAppGreen = Color(0xFF1FA855)

@OptIn(ExperimentalCoroutinesApi::class)
class DuesViewModel(container: AppContainer) : BaseViewModel() {
    private val repo = container.dueRepository
    private val customers = container.customerRepository

    val open: StateFlow<List<DueEntity>> = repo.observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settled: StateFlow<List<DueEntity>> = repo.observeSettled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val total: StateFlow<Long> = repo.observeOpenTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val profile: StateFlow<BusinessProfile?> = container.businessRepository.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val customerQuery = MutableStateFlow("")
    val suggestions: StateFlow<List<CustomerListRow>> = customerQuery
        .flatMapLatest { q -> customers.search(q, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(customerId: Long?, name: String, phone: String, amount: String, note: String, onDone: () -> Unit) {
        val minor = Money.parse(amount)
        if (name.isBlank()) return showMessage(R.string.dues_error_name)
        if (minor == null || minor <= 0) return showMessage(R.string.dues_error_amount)
        launchSafe {
            when (val r = repo.add(customerId, name, phone, minor, note)) {
                is DataResult.Success -> { showMessage(R.string.dues_added); onDone() }
                is DataResult.Failure -> showMessage(r.error.messageRes)
            }
        }
    }

    fun pay(due: DueEntity, amount: String, inCash: Boolean, onDone: () -> Unit) {
        val minor = Money.parse(amount)
        if (minor == null || minor <= 0) return showMessage(R.string.dues_error_amount)
        launchSafe {
            when (val r = repo.recordPayment(due.id, minor, intoCashDrawer = inCash)) {
                is DataResult.Success -> { showMessage(if (r.data == 0L) R.string.dues_settled else R.string.dues_payment_saved); onDone() }
                is DataResult.Failure -> showMessage(r.error.messageRes)
            }
        }
    }

    fun reminded(due: DueEntity) = launchSafe { repo.markReminded(due.id) }

    fun delete(due: DueEntity) = launchSafe { repo.delete(due.id) }
}

@Composable
fun DuesScreen(onBack: () -> Unit) {
    val vm = appViewModel { DuesViewModel(it) }
    val open by vm.open.collectAsStateWithLifecycle()
    val settled by vm.settled.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val snackbar = remember { SnackbarHostState() }
    var showPaid by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<DueEntity?>(null) }
    var deleting by remember { mutableStateOf<DueEntity?>(null) }
    val context = LocalContext.current
    MessageEffect(vm.messages, snackbar)

    val salon = profile?.name.orEmpty()

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.dues_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.dues_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(stringResource(R.string.dues_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(money.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.dues_count, open.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                SegmentedChoice(
                    options = listOf(false, true),
                    selected = showPaid,
                    label = { stringResource(if (it) R.string.dues_tab_paid else R.string.dues_tab_open) },
                    onSelect = { showPaid = it },
                )
            }
            val list = if (showPaid) settled else open
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        stringResource(if (showPaid) R.string.dues_empty_paid else R.string.dues_empty_title),
                        stringResource(R.string.dues_empty_message),
                    )
                }
            }
            items(list, key = { it.id }) { due ->
                DueCard(
                    due = due,
                    format = money::format,
                    onRemind = {
                        val text = context.getString(R.string.dues_reminder_message, due.customerName, money.format(due.balanceMinor), salon)
                        val result = ExternalApps.whatsAppText(context, due.customerPhone, text)
                        if (result != WhatsAppResult.FAILED) vm.reminded(due)
                        context.showWhatsAppResult(result)
                    },
                    onSms = {
                        val text = context.getString(R.string.dues_reminder_message, due.customerName, money.format(due.balanceMinor), salon)
                        if (ExternalApps.sms(context, due.customerPhone, text)) vm.reminded(due) else context.showWhatsAppResult(WhatsAppResult.FAILED)
                    },
                    onPay = { paying = due },
                    onDelete = { deleting = due },
                )
            }
        }
    }

    if (adding) AddDueDialog(vm, money.config.symbol, onDismiss = { adding = false })
    paying?.let { due ->
        var amount by remember(due.id) { mutableStateOf(Money.toInput(due.balanceMinor)) }
        var inCash by remember(due.id) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { paying = null },
            icon = { Icon(Icons.Filled.Payments, contentDescription = null) },
            title = { Text(stringResource(R.string.dues_receive_title, due.customerName)) },
            text = {
                Column {
                    Text(stringResource(R.string.dues_balance, money.format(due.balanceMinor)))
                    Spacer(Modifier.height(10.dp))
                    AmountField(amount, { amount = it }, stringResource(R.string.dues_amount_received), money.config.symbol)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = inCash, onCheckedChange = { inCash = it })
                        Text(stringResource(R.string.dues_in_cash), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.pay(due, amount, inCash) { paying = null } }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { paying = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleting?.let { due ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dues_delete_title)) },
            text = { Text(stringResource(R.string.dues_delete_message, due.customerName)) },
            confirmButton = {
                Button(onClick = { vm.delete(due); deleting = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DueCard(due: DueEntity, format: (Long) -> String, onRemind: () -> Unit, onSms: () -> Unit, onPay: () -> Unit, onDelete: () -> Unit) {
    val settled = due.settledAt != null
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(due.customerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    due.customerPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        format(if (settled) due.amountMinor else due.balanceMinor),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (settled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    if (!settled && due.paidMinor > 0) {
                        Text(stringResource(R.string.dues_paid_of, format(due.paidMinor), format(due.amountMinor)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    DateTimeUtils.formatDate(due.createdAt),
                    due.note,
                    due.settledAt?.let { stringResource(R.string.dues_paid_on, DateTimeUtils.formatDate(it)) },
                    due.lastReminderAt?.takeIf { !settled }?.let { stringResource(R.string.dues_reminded_on, DateTimeUtils.formatDate(it)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!settled) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onRemind, colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White)) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.dues_remind))
                    }
                    OutlinedButton(onClick = onSms) { Text(stringResource(R.string.tokens_send_sms)) }
                    OutlinedButton(onClick = onPay) { Text(stringResource(R.string.dues_received)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun AddDueDialog(vm: DuesViewModel, symbol: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dues_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    name,
                    { name = it; customerId = null; vm.customerQuery.value = it },
                    stringResource(R.string.dues_customer),
                    capitalization = KeyboardCapitalization.Words,
                )
                if (customerId == null && name.length >= 2) {
                    suggestions.filter { it.customer.name != name }.take(4).forEach { row ->
                        Text(
                            "${row.customer.name}  ${row.customer.phone.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable {
                                name = row.customer.name
                                phone = row.customer.phone.orEmpty()
                                customerId = row.customer.id
                            }.padding(vertical = 6.dp),
                        )
                    }
                }
                FormTextField(phone, { phone = it }, stringResource(R.string.dues_phone), keyboardType = KeyboardType.Phone)
                AmountField(amount, { amount = it }, stringResource(R.string.dues_amount), symbol)
                FormTextField(note, { note = it }, stringResource(R.string.dues_note))
            }
        },
        confirmButton = { Button(onClick = { vm.add(customerId, name, phone, amount, note, onDismiss) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
