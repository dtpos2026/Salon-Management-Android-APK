package com.dtpos.salonmanager.presentation.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.data.database.model.CustomerStatsRow
import com.dtpos.salonmanager.data.database.model.NamedTotalRow
import com.dtpos.salonmanager.data.database.model.VisitRow
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.SaleStatus
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.InitialsAvatar
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.components.StatCard
import com.dtpos.salonmanager.presentation.components.StatusBadge
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CustomerDetailState(
    val loaded: Boolean = false,
    val customer: CustomerEntity? = null,
    val stats: CustomerStatsRow = CustomerStatsRow(0, 0, null, null),
    val favorites: List<NamedTotalRow> = emptyList(),
    val visits: List<VisitRow> = emptyList(),
)

class CustomerDetailViewModel(container: AppContainer, customerId: Long) : BaseViewModel() {
    private val repo = container.customerRepository
    val state: StateFlow<CustomerDetailState> = combine(
        repo.observe(customerId),
        repo.observeStats(customerId),
        repo.observeFavoriteServices(customerId),
        repo.observeVisits(customerId),
    ) { customer, stats, favorites, visits -> CustomerDetailState(true, customer, stats, favorites, visits) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailState())
}

@Composable
fun CustomerDetailScreen(
    customerId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSale: (Long) -> Unit,
    onNewSale: () -> Unit,
) {
    val vm = appViewModel(key = "customer_$customerId") { CustomerDetailViewModel(it, customerId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val customer = state.customer

    Scaffold(
        topBar = {
            SalonTopBar(
                title = customer?.name ?: stringResource(R.string.customers_profile),
                onBack = onBack,
                actions = {
                    if (customer != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (customer != null) {
                ExtendedFloatingActionButton(
                    onClick = onNewSale,
                    icon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
                    text = { Text(stringResource(R.string.dashboard_new_sale)) },
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingState(Modifier.padding(padding))
            customer == null -> EmptyState(
                icon = Icons.Filled.History,
                title = stringResource(R.string.error_not_found),
                message = stringResource(R.string.customers_deleted),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ProfileCard(customer) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            stringResource(R.string.customers_total_visits),
                            state.stats.visitCount.toString(),
                            Icons.Filled.History,
                            Modifier.weight(1f),
                        )
                        StatCard(
                            stringResource(R.string.customers_total_spent),
                            money.format(state.stats.totalSpentMinor),
                            Icons.Filled.Payments,
                            Modifier.weight(1f),
                            accent = SalonTheme.extended.gold,
                        )
                    }
                }
                item {
                    ContentCard {
                        Text(stringResource(R.string.customers_last_visit_label), style = MaterialTheme.typography.labelMedium)
                        Text(
                            state.stats.lastVisitAt?.let { DateTimeUtils.formatDateTime(it) } ?: stringResource(R.string.customers_no_visits),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.favorites.isNotEmpty()) {
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(R.string.customers_favorites), style = MaterialTheme.typography.labelMedium)
                            state.favorites.forEach { fav ->
                                Text(
                                    stringResource(R.string.customers_favorite_line, fav.name, fav.quantity),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.customers_history)) }
                if (state.visits.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = stringResource(R.string.customers_no_visits),
                            message = stringResource(R.string.customers_history_empty),
                        )
                    }
                }
                items(state.visits, key = { it.saleId }) { visit -> VisitCard(visit) { onOpenSale(visit.saleId) } }
            }
        }
    }
}

@Composable
private fun ProfileCard(customer: CustomerEntity) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(customer.name)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(customer.name, style = MaterialTheme.typography.titleLarge)
                if (customer.gender != Gender.UNSPECIFIED) {
                    Text(stringResource(customer.gender.labelRes), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.customers_since, DateTimeUtils.formatDate(customer.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        customer.phone?.let { InfoLine(Icons.Filled.Phone, it) }
        customer.dateOfBirth?.let { InfoLine(Icons.Filled.Cake, DateTimeUtils.formatDate(LocalDate.ofEpochDay(it))) }
        customer.address?.let { InfoLine(Icons.Filled.LocationOn, it) }
        customer.notes?.let { InfoLine(Icons.AutoMirrored.Filled.StickyNote2, it) }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VisitCard(visit: VisitRow, onClick: () -> Unit) {
    val money = LocalMoney.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        ListItem(
            headlineContent = { Text(visit.servicesSummary) },
            supportingContent = {
                Column {
                    Text("${DateTimeUtils.formatDateTime(visit.visitAt)} · ${visit.receiptNumber}")
                    val staff = visit.staffSummary
                    Text(
                        if (staff != null) stringResource(R.string.customers_visit_staff, staff, stringResource(visit.paymentMethod.labelRes))
                        else stringResource(visit.paymentMethod.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.format(visit.totalMinor), style = MaterialTheme.typography.titleSmall)
                    if (visit.status == SaleStatus.VOIDED) {
                        StatusBadge(
                            stringResource(R.string.sale_voided),
                            container = SalonTheme.extended.negativeContainer,
                            content = SalonTheme.extended.negative,
                        )
                    }
                }
            },
        )
    }
}
