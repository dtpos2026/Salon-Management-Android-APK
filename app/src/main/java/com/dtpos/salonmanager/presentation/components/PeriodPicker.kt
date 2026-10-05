package com.dtpos.salonmanager.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.domain.model.Periods
import com.dtpos.salonmanager.presentation.common.labelRes
import java.time.LocalDate

/** A period chosen with [PeriodPicker]: a preset, or one exact date ([PeriodPreset.CUSTOM]). */
data class PeriodChoice(val preset: PeriodPreset = PeriodPreset.TODAY, val date: LocalDate? = null) {
    fun range(today: LocalDate): DateRange =
        if (preset == PeriodPreset.CUSTOM && date != null) DateRange.single(date) else Periods.range(preset, today)
}

val DEFAULT_PERIOD_PRESETS = listOf(PeriodPreset.TODAY, PeriodPreset.YESTERDAY, PeriodPreset.THIS_WEEK, PeriodPreset.THIS_MONTH)

/** Today / Yesterday / This week / This month chips plus a calendar chip for any one date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPicker(
    choice: PeriodChoice,
    onChange: (PeriodChoice) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<PeriodPreset> = DEFAULT_PERIOD_PRESETS,
) {
    var picking by remember { mutableStateOf(false) }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { preset ->
            FilterChip(
                selected = choice.preset == preset,
                onClick = { onChange(PeriodChoice(preset)) },
                label = { Text(stringResource(preset.labelRes)) },
            )
        }
        val custom = choice.preset == PeriodPreset.CUSTOM && choice.date != null
        FilterChip(
            selected = custom,
            onClick = { picking = true },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text(if (custom) DateTimeUtils.formatDate(choice.date!!) else stringResource(R.string.period_pick_date)) },
        )
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DateTimeUtils.toPickerMillis(choice.date ?: DateTimeUtils.today()))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(DateTimeUtils::fromPickerMillis)?.let { onChange(PeriodChoice(PeriodPreset.CUSTOM, it)) }
                    picking = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}
