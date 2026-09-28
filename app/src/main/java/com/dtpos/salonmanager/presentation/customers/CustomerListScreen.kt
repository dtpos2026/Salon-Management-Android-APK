package com.dtpos.salonmanager.presentation.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.InitialsAvatar
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SearchField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CustomerListViewModel(container: AppContainer) : BaseViewModel() {
    private val query = MutableStateFlow("")
    private val limit = MutableStateFlow(PAGE)
    private val repo = container.customerRepository

    val queryText: StateFlow<String> = query

    /** Null while loading. Search runs directly on the indexed local database. */
    val customers: StateFlow<List<CustomerListRow>?> = combine(query.debounce(150), limit) { q, l -> q to l }
        .flatMapLatest { (q, l) -> repo.search(q, l) }
        .map<List<CustomerListRow>, List<CustomerListRow>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val total: StateFlow<Int> = repo.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onQuery(value: String) {
        query.value = value
        limit.value = PAGE
    }

    fun loadMore() {
        limit.value += PAGE
    }

    companion object {
        const val PAGE = 100
    }
}

@Composable
fun CustomerListScreen(onOpenCustomer: (Long) -> Unit, onAddCustomer: () -> Unit) {
    val vm = appViewModel { CustomerListViewModel(it) }
    val customers by vm.customers.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val money = LocalMoney.current

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_customers), subtitle = stringResource(R.string.customers_total, total)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCustomer,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.customers_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SearchField(query, vm::onQuery, stringResource(R.string.customers_search)) }
            val list = customers
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.People,
                        title = stringResource(if (query.isBlank()) R.string.customers_empty_title else R.string.search_no_results),
                        message = stringResource(if (query.isBlank()) R.string.customers_empty_message else R.string.search_try_other),
                        actionLabel = if (query.isBlank()) stringResource(R.string.customers_add) else null,
                        onAction = if (query.isBlank()) onAddCustomer else null,
                    )
                }
            }
            items(list.orEmpty(), key = { it.customer.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenCustomer(row.customer.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    ListItem(
                        leadingContent = { InitialsAvatar(row.customer.name) },
                        headlineContent = { Text(row.customer.name) },
                        supportingContent = {
                            Column {
                                row.customer.phone?.let { Text(it) }
                                Text(
                                    row.lastVisitAt?.let { stringResource(R.string.customers_last_visit, DateTimeUtils.formatDate(it)) }
                                        ?: stringResource(R.string.customers_no_visits),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money.format(row.totalSpentMinor), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.customers_visits_count, row.visitCount), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
                }
            }
            if ((list?.size ?: 0) >= CustomerListViewModel.PAGE && list!!.size % CustomerListViewModel.PAGE == 0) {
                item {
                    androidx.compose.material3.TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}
