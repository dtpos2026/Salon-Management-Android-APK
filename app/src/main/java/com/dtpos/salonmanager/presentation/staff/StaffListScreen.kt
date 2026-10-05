package com.dtpos.salonmanager.presentation.staff

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
import androidx.compose.material.icons.filled.Groups
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
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.model.StaffPerformanceRow
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.domain.model.TargetPeriod
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.PeriodChoice
import com.dtpos.salonmanager.presentation.components.PeriodPicker
import com.dtpos.salonmanager.presentation.components.InitialsAvatar
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SearchField
import com.dtpos.salonmanager.presentation.components.StatusBadge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class StaffListState(
    val staff: List<StaffEntity>? = null,
    val monthPerformance: Map<Long, StaffPerformanceRow> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StaffListViewModel(container: AppContainer) : BaseViewModel() {
    private val repo = container.staffRepository
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query

    private val period = MutableStateFlow(PeriodChoice())
    val periodChoice: StateFlow<PeriodChoice> = period

    fun choose(value: PeriodChoice) {
        period.value = value
    }

    val state: StateFlow<StaffListState> = combine(
        query.flatMapLatest { repo.observeAll(it) },
        period.flatMapLatest { repo.observeTeamPerformance(it.range(DateTimeUtils.today())) },
    ) { staff, perf ->
        StaffListState(staff, perf.mapNotNull { row -> row.staffId?.let { it to row } }.toMap())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffListState())

    fun onQuery(value: String) {
        query.value = value
    }
}

@Composable
fun StaffListScreen(onBack: () -> Unit, onOpenStaff: (Long) -> Unit, onAddStaff: () -> Unit) {
    val vm = appViewModel { StaffListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val choice by vm.periodChoice.collectAsStateWithLifecycle()
    val money = LocalMoney.current

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_staff), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddStaff,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.staff_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SearchField(query, vm::onQuery, stringResource(R.string.staff_search)) }
            item { PeriodPicker(choice, vm::choose) }
            val list = state.staff
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Groups,
                        title = stringResource(R.string.staff_empty_title),
                        message = stringResource(R.string.staff_empty_message),
                        actionLabel = stringResource(R.string.staff_add),
                        onAction = onAddStaff,
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { member ->
                val perf = state.monthPerformance[member.id]
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStaff(member.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    ListItem(
                        leadingContent = { InitialsAvatar(member.name) },
                        headlineContent = { Text(member.name) },
                        supportingContent = {
                            Text("${stringResource(member.role.labelRes)} · ${stringResource(member.salaryType.labelRes)}")
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money.format(perf?.salesMinor ?: 0), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.staff_customers_count, perf?.customerCount ?: 0),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if ((perf?.commissionMinor ?: 0L) > 0L) {
                                    Text(
                                        stringResource(R.string.staff_commission_short, money.format(perf?.commissionMinor ?: 0L)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = com.dtpos.salonmanager.presentation.theme.SalonTheme.extended.warning,
                                    )
                                }
                                if (!member.isActive) {
                                    StatusBadge(
                                        stringResource(R.string.status_inactive),
                                        container = MaterialTheme.colorScheme.surfaceVariant,
                                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
