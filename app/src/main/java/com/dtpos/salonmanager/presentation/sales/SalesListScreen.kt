package com.dtpos.salonmanager.presentation.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.database.model.SalesSummaryRow
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.components.ChoiceChips
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SearchField
import com.dtpos.salonmanager.presentation.dashboard.RecentSaleRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class SalesListState(
    val preset: PeriodPreset = PeriodPreset.TODAY,
    val query: String = "",
    val sales: List<SaleEntity>? = null,
    val summary: SalesSummaryRow = SalesSummaryRow.EMPTY,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SalesListViewModel(container: AppContainer) : BaseViewModel() {
    private val preset = MutableStateFlow(PeriodPreset.TODAY)
    private val query = MutableStateFlow("")
    private val repo = container.saleRepository

    private fun range(p: PeriodPreset): DateRange =
        if (p == PeriodPreset.THIS_YEAR) DateRange(DateTimeUtils.today().minusYears(5), DateTimeUtils.today())
        else Periods.range(p, DateTimeUtils.today())

    val state: StateFlow<SalesListState> = combine(preset, query.debounce(200)) { p, q -> p to q }
        .flatMapLatest { (p, q) ->
            val r = range(p)
            combine(repo.observeSales(r, q), repo.observeSummary(r)) { sales, summary -> SalesListState(p, q, sales, summary) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SalesListState())

    val queryText: StateFlow<String> = query
    val selectedPreset: StateFlow<PeriodPreset> = preset

    fun onPreset(p: PeriodPreset) {
        preset.value = p
    }

    fun onQuery(q: String) {
        query.value = q
    }
}

val SALES_PRESETS = listOf(PeriodPreset.TODAY, PeriodPreset.YESTERDAY, PeriodPreset.THIS_WEEK, PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH)

@Composable
fun SalesListScreen(onNewSale: () -> Unit, onOpenSale: (Long) -> Unit) {
    val vm = appViewModel { SalesListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val preset by vm.selectedPreset.collectAsStateWithLifecycle()
    val money = LocalMoney.current

    Scaffold(
        topBar = { SalonTopBar(title = stringResource(R.string.nav_sales)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSale,
                icon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
                text = { Text(stringResource(R.string.dashboard_new_sale)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SearchField(query, vm::onQuery, stringResource(R.string.sales_search)) }
            item {
                ChoiceChips(
                    options = SALES_PRESETS + PeriodPreset.THIS_YEAR,
                    selected = preset,
                    label = { if (it == PeriodPreset.THIS_YEAR) stringResource(R.string.filter_all) else stringResource(it.labelRes) },
                    onSelect = vm::onPreset,
                )
            }
            item {
                ContentCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sales_total), style = MaterialTheme.typography.labelMedium)
                            Text(money.format(state.summary.totalMinor), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sales_count), style = MaterialTheme.typography.labelMedium)
                            Text(state.summary.saleCount.toString(), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.dashboard_cash_received), style = MaterialTheme.typography.labelMedium)
                            Text(money.format(state.summary.cashMinor), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            val sales = state.sales
            if (sales != null && sales.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = stringResource(R.string.sales_empty_title),
                        message = stringResource(R.string.sales_empty_message),
                    )
                }
            }
            items(sales.orEmpty(), key = { it.id }) { sale -> RecentSaleRow(sale) { onOpenSale(sale.id) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
