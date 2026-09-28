package com.dtpos.salonmanager.presentation.expenses

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.MoneyOff
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.ExpenseEntity
import com.dtpos.salonmanager.domain.model.ExpenseType
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
import com.dtpos.salonmanager.presentation.components.SegmentedChoice
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ExpensesState(
    val type: ExpenseType = ExpenseType.BUSINESS,
    val preset: PeriodPreset = PeriodPreset.THIS_MONTH,
    val query: String = "",
    val expenses: List<ExpenseEntity>? = null,
    val total: Long = 0,
    val otherTypeTotal: Long = 0,
)

data class ExpenseFilter(val type: ExpenseType, val preset: PeriodPreset, val query: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ExpensesViewModel(container: AppContainer) : BaseViewModel() {
    private val repo = container.expenseRepository
    private val filter = MutableStateFlow(ExpenseFilter(ExpenseType.BUSINESS, PeriodPreset.THIS_MONTH, ""))

    val state: StateFlow<ExpensesState> = filter.debounce(100).flatMapLatest { f ->
        val range = Periods.range(f.preset, DateTimeUtils.today())
        val other = if (f.type == ExpenseType.BUSINESS) ExpenseType.PERSONAL else ExpenseType.BUSINESS
        combine(
            repo.observeExpenses(f.type, range, f.query),
            repo.observeTotal(f.type, range),
            repo.observeTotal(other, range),
        ) { list, total, otherTotal -> ExpensesState(f.type, f.preset, f.query, list, total, otherTotal) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesState())

    /** Current filter without debounce, so chips and tabs react instantly. */
    val currentFilter: StateFlow<ExpenseFilter> = filter

    fun onType(type: ExpenseType) {
        filter.value = filter.value.copy(type = type)
    }

    fun onPreset(preset: PeriodPreset) {
        filter.value = filter.value.copy(preset = preset)
    }

    fun onQuery(query: String) {
        filter.value = filter.value.copy(query = query)
    }
}

private val EXPENSE_PRESETS = listOf(PeriodPreset.TODAY, PeriodPreset.THIS_WEEK, PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.THIS_YEAR)

@Composable
fun ExpensesScreen(
    onAddExpense: (ExpenseType) -> Unit,
    onEditExpense: (Long, ExpenseType) -> Unit,
    onManageCategories: (ExpenseType) -> Unit,
) {
    val vm = appViewModel { ExpensesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.currentFilter.collectAsStateWithLifecycle()
    val type = filter.type
    val preset = filter.preset
    val query = filter.query
    val money = LocalMoney.current
    val ext = SalonTheme.extended

    Scaffold(
        topBar = {
            SalonTopBar(
                stringResource(R.string.nav_expenses),
                actions = {
                    IconButton(onClick = { onManageCategories(type) }) {
                        Icon(Icons.Filled.Category, contentDescription = stringResource(R.string.expenses_categories))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddExpense(type) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.expenses_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SegmentedChoice(
                    options = ExpenseType.entries.toList(),
                    selected = type,
                    label = { stringResource(it.labelRes) },
                    onSelect = vm::onType,
                )
            }
            item {
                ChoiceChips(EXPENSE_PRESETS, preset, { stringResource(it.labelRes) }, vm::onPreset)
            }
            item {
                ContentCard {
                    Text(
                        stringResource(if (type == ExpenseType.BUSINESS) R.string.expenses_total_business else R.string.expenses_total_personal),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(money.format(state.total), style = MaterialTheme.typography.headlineSmall, color = ext.negative)
                    Text(
                        stringResource(
                            if (type == ExpenseType.BUSINESS) R.string.expenses_separation_business else R.string.expenses_separation_personal,
                            money.format(state.otherTypeTotal),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SearchField(query, vm::onQuery, stringResource(R.string.expenses_search)) }
            val list = state.expenses
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.MoneyOff,
                        title = stringResource(R.string.expenses_empty_title),
                        message = stringResource(R.string.expenses_empty_message),
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { expense ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onEditExpense(expense.id, expense.type) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    ListItem(
                        headlineContent = { Text(expense.categoryName) },
                        supportingContent = {
                            Column {
                                Text(
                                    "${DateTimeUtils.formatDate(LocalDate.ofEpochDay(expense.expenseDate))} · ${stringResource(expense.paymentMethod.labelRes)}" +
                                        if (expense.paidFromCounter) " · ${stringResource(R.string.expenses_from_counter)}" else "",
                                )
                                expense.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        trailingContent = { Text(money.format(expense.amountMinor), style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
        }
    }
}
