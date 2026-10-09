package com.dtpos.salonmanager.presentation.tokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.components.DateField
import com.dtpos.salonmanager.presentation.components.DropdownField
import com.dtpos.salonmanager.presentation.components.InitialsAvatar
import com.dtpos.salonmanager.presentation.components.SearchField
import java.time.LocalDate

/** What the token / booking form collects. [customerId] is set when a saved customer was chosen. */
data class TokenDraft(
    val name: String,
    val phone: String,
    val customerId: Long?,
    val services: List<String>,
) {
    companion object {
        /** Several services are kept on the token as one line: "Haircut, Beard". */
        fun joinServices(services: List<String>): String =
            services.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
    }
}

/** A name (2+ letters) or a phone number (3+ digits) is enough to suggest saved customers. */
fun String.isSuggestable(): Boolean = trim().let { q -> q.count { it.isLetter() } >= 2 || q.count { it.isDigit() } >= 3 }

/**
 * Menu services for the typed text: names containing it (those starting with it first), without
 * the ones already chosen. With nothing typed, the first services of the menu (quick picks).
 */
fun serviceSuggestions(services: List<ServiceEntity>, typed: String, chosen: List<String>, limit: Int = 8): List<ServiceEntity> {
    val q = typed.trim().lowercase()
    val taken = chosen.map { it.lowercase() }.toSet()
    return services.asSequence()
        .filter { it.name.lowercase() !in taken }
        .filter { q.isEmpty() || it.name.lowercase().contains(q) }
        .sortedBy { if (q.isNotEmpty() && it.name.lowercase().startsWith(q)) 0 else 1 }
        .take(limit)
        .toList()
}

internal val SLOTS: List<Int> = (8 * 60 until 24 * 60 step 15).toList()

/**
 * New walk-in token ([booking] false: quick, today) or advance booking (date and time). Name and
 * phone suggest saved customers as you type; the list icon opens all customers. Services come
 * from the menu (several allowed) and anything else can be typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewTokenDialog(
    booking: Boolean,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    vm: TokensViewModel,
    onSave: (LocalDate, TokenDraft, Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var visits by remember { mutableStateOf(0) }
    var typing by remember { mutableStateOf<String?>(null) } // "name" or "phone": where suggestions show
    val chosen = remember { mutableStateListOf<String>() }
    var serviceText by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(initialDate) }
    var slot by remember { mutableStateOf(17 * 60) }
    var listOpen by remember { mutableStateOf(false) }
    val suggestions by vm.customerSuggestions.collectAsStateWithLifecycle()
    val services by vm.services.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    LaunchedEffect(Unit) { vm.customerQuery.value = "" }

    fun pick(row: CustomerListRow) {
        customer = row.customer
        visits = row.visitCount
        name = row.customer.name
        phone = row.customer.phone.orEmpty()
        typing = null
        vm.customerQuery.value = ""
    }

    fun addService(value: String) {
        val clean = value.trim()
        if (clean.isNotEmpty() && chosen.none { it.equals(clean, ignoreCase = true) }) chosen += clean
        serviceText = ""
    }

    fun save() {
        if (serviceText.isNotBlank()) addService(serviceText)
        onSave(if (booking) day else DateTimeUtils.today(), TokenDraft(name, phone, customer?.id, chosen.toList()), if (booking) slot else null)
    }

    @Composable
    fun SuggestionList() {
        if (customer != null || suggestions.isEmpty()) return
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            suggestions.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider()
                CustomerLine(row, Modifier.clickable { pick(row) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (booking) R.string.tokens_book else R.string.tokens_new)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val chosenCustomer = customer
                if (chosenCustomer != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(chosenCustomer.name)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chosenCustomer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                chosenCustomer.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text(stringResource(R.string.tokens_customer_saved, visits), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { customer = null }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tokens_customer_unlink))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; typing = "name"; vm.customerQuery.value = it },
                        label = { Text(stringResource(R.string.tokens_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        trailingIcon = {
                            IconButton(onClick = { vm.pickerQuery.value = ""; listOpen = true }) {
                                Icon(Icons.Filled.Contacts, contentDescription = stringResource(R.string.tokens_customer_list))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (typing == "name") SuggestionList()
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; typing = "phone"; vm.customerQuery.value = it },
                        label = { Text(stringResource(R.string.tokens_phone)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (typing == "phone") SuggestionList()
                }

                Text(stringResource(R.string.tokens_services), style = MaterialTheme.typography.labelLarge)
                if (chosen.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chosen.toList().forEach { service ->
                            InputChip(
                                selected = true,
                                onClick = { chosen.remove(service) },
                                label = { Text(service) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = serviceText,
                    onValueChange = { serviceText = it },
                    label = { Text(stringResource(R.string.tokens_service_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addService(serviceText) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                val matches = serviceSuggestions(services, serviceText, chosen)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    matches.forEach { s ->
                        AssistChip(
                            onClick = { addService(s.name) },
                            label = { Text(s.name + " · " + money.format(s.priceMinor), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                    val typed = serviceText.trim()
                    if (typed.isNotEmpty() && services.none { it.name.equals(typed, ignoreCase = true) } && chosen.none { it.equals(typed, ignoreCase = true) }) {
                        AssistChip(
                            onClick = { addService(typed) },
                            label = { Text(stringResource(R.string.tokens_service_add, typed), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
                if (booking) {
                    DateField(stringResource(R.string.tokens_date), day, { it?.let { d -> day = d } })
                    DropdownField(stringResource(R.string.tokens_time), SLOTS, slot, { formatSlot(it) }, { slot = it })
                }
            }
        },
        confirmButton = { Button(onClick = ::save) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (listOpen) {
        CustomerListDialog(vm, onPick = { pick(it); listOpen = false }, onDismiss = { listOpen = false })
    }
}

@Composable
private fun CustomerLine(row: CustomerListRow, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(row.customer.name, Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.customer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.customer.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(stringResource(R.string.customers_visits_count, row.visitCount), style = MaterialTheme.typography.labelSmall)
    }
}

/** All saved customers, searchable by name or phone; tap one to fill the token. */
@Composable
private fun CustomerListDialog(vm: TokensViewModel, onPick: (CustomerListRow) -> Unit, onDismiss: () -> Unit) {
    val query by vm.pickerQuery.collectAsStateWithLifecycle()
    val results by vm.pickerResults.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tokens_customer_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { vm.pickerQuery.value = it }, stringResource(R.string.tokens_customer_search))
                if (results.isEmpty()) {
                    Text(stringResource(R.string.tokens_customer_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(results, key = { it.customer.id }) { row ->
                        CustomerLine(row, Modifier.clickable { onPick(row) }.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
