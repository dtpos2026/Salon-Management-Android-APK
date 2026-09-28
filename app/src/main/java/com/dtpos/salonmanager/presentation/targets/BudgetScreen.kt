package com.dtpos.salonmanager.presentation.targets

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.domain.calc.BudgetLine
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.AmountField
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.ProgressBar
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Smart budget: monthly plan for business costs, household, children, food and savings. */
class BudgetViewModel(private val container: AppContainer) : BaseViewModel() {
    private val today = DateTimeUtils.today()
    private val month = DateRange(DateTimeUtils.monthStart(today), DateTimeUtils.monthEnd(today))
    private val _lines = MutableStateFlow<List<BudgetLine>>(emptyList())
    val lines: StateFlow<List<BudgetLine>> = _lines.asStateFlow()

    init {
        viewModelScope.launch {
            container.targetRepository.observeBudgets().collect { budgets ->
                _lines.value = try {
                    container.reportRepository.buildBudget(month, budgets)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
        }
    }

    fun setBudget(group: BudgetGroup, input: String, onDone: () -> Unit) {
        val amount = Validators.amount(input.ifBlank { "0" }, allowZero = true)
        if (amount !is FieldResult.Valid) return showMessage(amount.errorOrNull!!.messageRes)
        launchSafe {
            container.targetRepository.setBudget(group, amount.value)
            onDone()
        }
    }
}

@Composable
fun BudgetScreen(onBack: () -> Unit) {
    val vm = appViewModel { BudgetViewModel(it) }
    val lines by vm.lines.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_budget), onBack = onBack, subtitle = DateTimeUtils.formatMonth(DateTimeUtils.today())) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.budget_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(lines, key = { it.group.name }) { line -> BudgetCard(line) { editing = line } }
        }
    }

    editing?.let { line ->
        var input by remember(line.group) { mutableStateOf(if (line.hasBudget) Money.toInput(line.budgetMinor) else "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(line.group.labelRes)) },
            text = {
                Column {
                    Text(stringResource(budgetHint(line.group)), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    AmountField(input, { input = it }, stringResource(R.string.budget_monthly_amount), money.config.symbol)
                }
            },
            confirmButton = { Button(onClick = { vm.setBudget(line.group, input) { editing = null } }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun budgetHint(group: BudgetGroup): Int = when (group) {
    BudgetGroup.BUSINESS_EXPENSES -> R.string.budget_hint_business
    BudgetGroup.HOUSEHOLD -> R.string.budget_hint_household
    BudgetGroup.CHILDREN -> R.string.budget_hint_children
    BudgetGroup.FOOD -> R.string.budget_hint_food
    BudgetGroup.SAVINGS -> R.string.budget_hint_savings
}

@Composable
private fun BudgetCard(line: BudgetLine, onEdit: () -> Unit) {
    val money = LocalMoney.current
    val ext = SalonTheme.extended
    val bad = line.isOverBudget || line.isSavingsShort
    ContentCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(line.group.labelRes), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(budgetHint(line.group)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        }
        if (!line.hasBudget) {
            LabeledValueRow(stringResource(R.string.budget_actual), money.format(line.actualMinor))
            Text(stringResource(R.string.budget_not_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ContentCard
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(line.fraction, color = if (bad) ext.negative else if (line.group == BudgetGroup.SAVINGS) ext.positive else ext.gold)
        Spacer(Modifier.height(6.dp))
        LabeledValueRow(stringResource(R.string.budget_planned), money.format(line.budgetMinor))
        LabeledValueRow(
            stringResource(if (line.group == BudgetGroup.SAVINGS) R.string.budget_saved else R.string.budget_actual),
            "${money.format(line.actualMinor)} (${Percent.formatRatio(line.usedPercent)}%)",
            valueColor = if (bad) ext.negative else MaterialTheme.colorScheme.onSurface,
        )
        val remaining = line.remainingMinor
        LabeledValueRow(
            stringResource(
                when {
                    line.group == BudgetGroup.SAVINGS && remaining > 0 -> R.string.budget_to_save
                    line.group == BudgetGroup.SAVINGS -> R.string.budget_saved_extra
                    remaining >= 0 -> R.string.budget_left
                    else -> R.string.budget_over
                },
            ),
            money.format(kotlin.math.abs(remaining)),
            emphasize = true,
            valueColor = if (bad) ext.negative else ext.positive,
        )
    }
}
