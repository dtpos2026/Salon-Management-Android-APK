package com.dtpos.salonmanager.presentation.targets

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.dtpos.salonmanager.domain.calc.TargetCalculator
import com.dtpos.salonmanager.domain.calc.TargetProgress
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.domain.model.TargetPeriod
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TargetRow(val period: TargetPeriod, val progress: TargetProgress, val daysLeft: Int)

class TargetsViewModel(private val container: AppContainer) : BaseViewModel() {
    private val today = DateTimeUtils.today()
    private val daily = Periods.targetRange(TargetPeriod.DAILY, today)
    private val weekly = Periods.targetRange(TargetPeriod.WEEKLY, today)
    private val monthly = Periods.targetRange(TargetPeriod.MONTHLY, today)

    val rows: StateFlow<List<TargetRow>> = combine(
        container.targetRepository.observeTargets(),
        container.saleRepository.observeSummary(daily),
        container.saleRepository.observeSummary(weekly),
        container.saleRepository.observeSummary(monthly),
    ) { targets, d, w, m ->
        listOf(
            TargetRow(TargetPeriod.DAILY, TargetCalculator.progress(targets[TargetPeriod.DAILY] ?: 0, d.totalMinor), 1),
            TargetRow(
                TargetPeriod.WEEKLY,
                TargetCalculator.progress(targets[TargetPeriod.WEEKLY] ?: 0, w.totalMinor),
                (weekly.endEpochDay - today.toEpochDay() + 1).toInt(),
            ),
            TargetRow(
                TargetPeriod.MONTHLY,
                TargetCalculator.progress(targets[TargetPeriod.MONTHLY] ?: 0, m.totalMinor),
                (monthly.endEpochDay - today.toEpochDay() + 1).toInt(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTarget(period: TargetPeriod, input: String, onDone: () -> Unit) {
        val amount = Validators.amount(input, allowZero = true)
        if (amount !is FieldResult.Valid) return showMessage(amount.errorOrNull!!.messageRes)
        launchSafe {
            container.targetRepository.setTarget(period, amount.value)
            showMessage(R.string.saved)
            onDone()
        }
    }
}

@Composable
fun TargetsScreen(onBack: () -> Unit, onOpenBudget: () -> Unit) {
    val vm = appViewModel { TargetsViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<TargetRow?>(null) }
    val money = LocalMoney.current
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_targets), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.period.name }) { row -> TargetCard(row, onEdit = { editing = row }) }
            item {
                OutlinedButton(onClick = onOpenBudget, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PieChart, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.nav_budget))
                }
            }
        }
    }

    editing?.let { row ->
        var input by remember(row.period) { mutableStateOf(if (row.progress.hasTarget) Money.toInput(row.progress.targetMinor) else "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.targets_set_title, stringResource(row.period.labelRes))) },
            text = {
                Column {
                    Text(stringResource(R.string.targets_set_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    AmountField(input, { input = it }, stringResource(R.string.field_amount), money.config.symbol)
                }
            },
            confirmButton = { Button(onClick = { vm.setTarget(row.period, input.ifBlank { "0" }) { editing = null } }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TargetCard(row: TargetRow, onEdit: () -> Unit) {
    val money = LocalMoney.current
    val p = row.progress
    ContentCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(row.period.labelRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        }
        if (!p.hasTarget) {
            Text(stringResource(R.string.targets_not_set), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ContentCard
        }
        Text(
            "${Percent.formatRatio(p.achievedPercent)}%",
            style = MaterialTheme.typography.headlineMedium,
            color = if (p.isAchieved) SalonTheme.extended.positive else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        ProgressBar(p.fraction, color = if (p.isAchieved) SalonTheme.extended.positive else SalonTheme.extended.gold)
        Spacer(Modifier.height(8.dp))
        LabeledValueRow(stringResource(R.string.targets_target), money.format(p.targetMinor))
        LabeledValueRow(stringResource(R.string.targets_achieved), money.format(p.achievedMinor))
        LabeledValueRow(stringResource(R.string.targets_remaining), money.format(p.remainingMinor), emphasize = true)
        if (!p.isAchieved && row.daysLeft > 1) {
            Text(
                stringResource(
                    R.string.targets_required_per_day,
                    money.format(TargetCalculator.requiredPerDay(p, row.daysLeft)),
                    row.daysLeft,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
