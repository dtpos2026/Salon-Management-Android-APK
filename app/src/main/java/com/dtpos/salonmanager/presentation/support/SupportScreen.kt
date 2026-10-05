package com.dtpos.salonmanager.presentation.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.services.support.SupportMessage
import com.dtpos.salonmanager.services.support.SupportSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class SupportViewModel(private val container: AppContainer) : BaseViewModel() {
    private val chat = container.supportChat
    private val accounts = container.accountManager
    private val uid = accounts.currentUser()?.uid

    val available: Boolean = chat.isConfigured && uid != null
    val chatMessages: StateFlow<List<SupportMessage>?> = (if (uid != null) chat.messages(uid) else kotlinx.coroutines.flow.emptyFlow())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun clear() {
        val id = uid ?: return
        launchSafe {
            try {
                chat.clear(id)
                showMessage(R.string.support_cleared)
            } catch (e: Exception) {
                showMessage(R.string.support_clear_failed)
            }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val id = uid ?: return
        if (text.isBlank() || _sending.value) return
        _sending.value = true
        launchSafe {
            try {
                val account = accounts.cachedAccount()?.account
                chat.send(id, account?.salonName, account?.email ?: accounts.currentUser()?.email, text, container.uiPreferences.language.tag)
                onSent()
            } catch (e: Exception) {
                showMessage(R.string.support_send_failed)
            } finally {
                _sending.value = false
            }
        }
    }
}

/** Chat with DT support. The AI helper answers at once (when enabled); a person replies later. */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val vm = appViewModel { SupportViewModel(it) }
    val messages by vm.chatMessages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(messages?.size) { messages?.size?.takeIf { it > 0 }?.let { listState.animateScrollToItem(it - 1) } }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        com.dtpos.salonmanager.presentation.components.ConfirmDialog(
            title = stringResource(R.string.support_clear),
            message = stringResource(R.string.support_clear_message),
            confirmLabel = stringResource(R.string.support_clear),
            destructive = true,
            onConfirm = {
                confirmClear = false
                vm.clear()
            },
            onDismiss = { confirmClear = false },
        )
    }

    Scaffold(
        topBar = {
            SalonTopBar(
                stringResource(R.string.support_title),
                onBack = onBack,
                subtitle = stringResource(R.string.support_subtitle),
                actions = {
                    if (vm.available && !messages.isNullOrEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.support_clear))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (vm.available) {
                Row(
                    Modifier.fillMaxWidth().imePadding().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        placeholder = { Text(stringResource(R.string.support_hint)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                    )
                    FilledIconButton(onClick = { vm.send(draft) { draft = "" } }, enabled = draft.isNotBlank() && !sending, modifier = Modifier.size(52.dp)) {
                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.support_send))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !vm.available -> EmptyState(Icons.Filled.SupportAgent, stringResource(R.string.support_offline_title), stringResource(R.string.support_offline_message))
                messages == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                messages!!.isEmpty() -> EmptyState(Icons.Filled.SupportAgent, stringResource(R.string.support_empty_title), stringResource(R.string.support_empty_message))
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages!!, key = { it.id }) { Bubble(it) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: SupportMessage) {
    val mine = message.from == SupportSender.USER
    val container = when (message.from) {
        SupportSender.USER -> MaterialTheme.colorScheme.primary
        SupportSender.ADMIN -> MaterialTheme.colorScheme.surfaceVariant
        SupportSender.AI -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (message.from) {
        SupportSender.USER -> MaterialTheme.colorScheme.onPrimary
        SupportSender.ADMIN -> MaterialTheme.colorScheme.onSurfaceVariant
        SupportSender.AI -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(container, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!mine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.from == SupportSender.AI) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(
                        stringResource(if (message.from == SupportSender.AI) R.string.support_from_ai else R.string.support_from_admin),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                }
                Spacer(Modifier.size(2.dp))
            }
            Text(message.text, color = content, style = MaterialTheme.typography.bodyLarge)
            message.atMillis?.let {
                Text(DateTimeUtils.formatTime(it), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
