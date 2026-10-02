package com.dtpos.salonmanager.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.UiText
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.asString
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.services.ai.AiAssistant
import com.dtpos.salonmanager.services.ai.AiError
import com.dtpos.salonmanager.services.ai.AiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class AiExchange(val question: String, val answer: String)

class AiAssistantViewModel(private val container: AppContainer) : BaseViewModel() {
    val enabled: StateFlow<Boolean> = container.uiPreferences.aiAssistant

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()
    private val _history = MutableStateFlow<List<AiExchange>>(emptyList())
    val history: StateFlow<List<AiExchange>> = _history.asStateFlow()

    fun setEnabled(value: Boolean) = container.uiPreferences.setAiAssistant(value)

    fun ask(question: String, onAsked: () -> Unit) {
        if (question.isBlank() || _busy.value) return
        _busy.value = true
        _error.value = null
        launchSafe {
            try {
                val snapshot = container.reportRepository.buildSnapshot(DateTimeUtils.today())
                val stats = AiAssistant.summary(
                    snapshot,
                    pendingDuesMinor = container.dueRepository.observeOpenTotal().first(),
                    customerCount = container.customerRepository.observeCount().first(),
                )
                when (val result = container.aiAssistant.ask(question, stats, container.uiPreferences.language.tag)) {
                    is AiResult.Answer -> {
                        _history.value = listOf(AiExchange(question.trim(), result.text)) + _history.value
                        onAsked()
                    }
                    is AiResult.Failed -> _error.value = UiText.res(errorText(result.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun errorText(error: AiError): Int = when (error) {
        AiError.OFF_BY_ADMIN -> R.string.ai_error_off
        AiError.LIMIT -> R.string.ai_error_limit
        AiError.NOT_ACTIVE -> R.string.ai_error_not_active
        AiError.SIGNED_OUT -> R.string.ai_error_signed_out
        AiError.NETWORK -> R.string.ai_error_network
        AiError.NOT_SET_UP -> R.string.ai_error_not_set_up
        AiError.FAILED -> R.string.ai_error_failed
    }
}

private val SUGGESTIONS = listOf(
    R.string.ai_q_grow, R.string.ai_q_promotion, R.string.ai_q_slow_days, R.string.ai_q_services,
    R.string.ai_q_expenses, R.string.ai_q_staff, R.string.ai_q_customers, R.string.ai_q_full,
)

/** AI business assistant: off until the owner turns it on; offline Insights always work. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(onBack: () -> Unit, onOpenInsights: () -> Unit) {
    val vm = appViewModel { AiAssistantViewModel(it) }
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { SalonTopBar(stringResource(R.string.ai_title), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ai_switch), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.ai_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = vm::setEnabled)
                    }
                }
            }
            if (!enabled) {
                item {
                    ContentCard {
                        Text(stringResource(R.string.ai_off_message), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(10.dp))
                        OutlinedButton(onClick = onOpenInsights) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_open_insights))
                        }
                    }
                }
                return@LazyColumn
            }
            item {
                ContentCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.ai_suggestions), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SUGGESTIONS.forEach { res ->
                                val text = stringResource(res)
                                AssistChip(onClick = { question = text }, label = { Text(text) })
                            }
                        }
                        FormTextField(question, { question = it.take(1500) }, stringResource(R.string.ai_question), singleLine = false, minLines = 2)
                        Button(onClick = { vm.ask(question) { question = "" } }, enabled = !busy && question.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_thinking))
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_ask))
                            }
                        }
                        error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(history) { exchange ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(exchange.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        SelectionContainer { Text(exchange.answer, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
