package com.dtpos.salonmanager.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.data.database.entities.PaymentAccountEntity
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.AccountKind
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PaymentAccountsViewModel(private val container: AppContainer) : BaseViewModel() {
    private val repo = container.paymentAccountRepository

    val accounts: StateFlow<List<PaymentAccountEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, kind: AccountKind, title: String, number: String, onDone: () -> Unit) = launchSafe {
        when (val result = repo.save(id, name, kind, title, number)) {
            is DataResult.Success -> {
                showMessage(R.string.accounts_saved)
                onDone()
            }
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }

    fun setActive(account: PaymentAccountEntity, active: Boolean) = launchSafe { repo.setActive(account.id, active) }

    fun delete(account: PaymentAccountEntity) = launchSafe {
        repo.delete(account.id)
        showMessage(R.string.accounts_deleted)
    }
}

/**
 * JazzCash, EasyPaisa, bank accounts and card machines the salon receives money in. Each one
 * becomes a payment button at checkout and a line on the dashboard and the Close Day report.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentAccountsScreen(onBack: () -> Unit) {
    val vm = appViewModel { PaymentAccountsViewModel(it) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)
    var editing by remember { mutableStateOf<PaymentAccountEntity?>(null) }
    var creating by remember { mutableStateOf<Pair<String, AccountKind>?>(null) }
    var deleting by remember { mutableStateOf<PaymentAccountEntity?>(null) }

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_accounts), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = "" to AccountKind.WALLET },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.accounts_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ContentCard {
                    Text(stringResource(R.string.accounts_hint), style = MaterialTheme.typography.bodyMedium)
                    if (accounts.isEmpty()) {
                        Text(
                            stringResource(R.string.accounts_quick_add),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QUICK.forEach { (name, kind) ->
                                AssistChip(onClick = { creating = name to kind }, label = { Text(name) })
                            }
                        }
                    }
                }
            }
            if (accounts.isEmpty()) {
                item { EmptyState(Icons.Filled.AccountBalance, stringResource(R.string.accounts_empty_title), stringResource(R.string.accounts_empty_message)) }
            }
            items(accounts, key = { it.id }) { account ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (account.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { editing = account },
                ) {
                    ListItem(
                        leadingContent = { Icon(iconFor(account.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(account.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(stringResource(account.kind.labelRes), account.accountTitle, account.accountNumber).joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            androidx.compose.foundation.layout.Row {
                                Switch(checked = account.isActive, onCheckedChange = { vm.setActive(account, it) })
                                IconButton(onClick = { deleting = account }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    editing?.let { account ->
        AccountDialog(
            initial = account,
            initialName = account.name,
            initialKind = account.kind,
            onDismiss = { editing = null },
            onSave = { name, kind, title, number -> vm.save(account.id, name, kind, title, number) { editing = null } },
        )
    }
    creating?.let { (name, kind) ->
        AccountDialog(
            initial = null,
            initialName = name,
            initialKind = kind,
            onDismiss = { creating = null },
            onSave = { n, k, title, number -> vm.save(null, n, k, title, number) { creating = null } },
        )
    }
    deleting?.let { account ->
        ConfirmDialog(
            title = stringResource(R.string.accounts_delete_title),
            message = stringResource(R.string.accounts_delete_message, account.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                vm.delete(account)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

private val QUICK = listOf(
    "JazzCash" to AccountKind.WALLET,
    "EasyPaisa" to AccountKind.WALLET,
    "Bank" to AccountKind.BANK,
    "Card" to AccountKind.CARD,
)

private fun iconFor(kind: AccountKind) = when (kind) {
    AccountKind.WALLET -> Icons.Filled.PhoneAndroid
    AccountKind.BANK -> Icons.Filled.AccountBalance
    AccountKind.CARD -> Icons.Filled.CreditCard
    AccountKind.OTHER -> Icons.Filled.Wallet
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountDialog(
    initial: PaymentAccountEntity?,
    initialName: String,
    initialKind: AccountKind,
    onDismiss: () -> Unit,
    onSave: (String, AccountKind, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    var title by remember { mutableStateOf(initial?.accountTitle.orEmpty()) }
    var number by remember { mutableStateOf(initial?.accountNumber.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.accounts_add else R.string.accounts_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(name, { name = it.take(40) }, stringResource(R.string.accounts_name), capitalization = KeyboardCapitalization.Words)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.labelRes)) })
                    }
                }
                FormTextField(title, { title = it.take(60) }, stringResource(R.string.accounts_title_field), capitalization = KeyboardCapitalization.Words)
                FormTextField(number, { number = it.take(40) }, stringResource(R.string.accounts_number), keyboardType = KeyboardType.Text)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, kind, title, number) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
