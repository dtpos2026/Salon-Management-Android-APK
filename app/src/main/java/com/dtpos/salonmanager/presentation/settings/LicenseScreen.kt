package com.dtpos.salonmanager.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.license.LicenseStatus
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.StatusBadge
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.license.ActivationResult

class LicenseViewModel(private val container: AppContainer) : BaseViewModel() {
    private val manager = container.licenseManager
    val state = manager.state
    val installationId: String = manager.installationId
    val enforced: Boolean = manager.isEnforced
    val canActivate: Boolean = manager.canActivate

    fun activate(key: String, onDone: () -> Unit) = launchSafe {
        when (val result = manager.activate(key)) {
            is ActivationResult.Activated -> {
                showMessage(R.string.license_activated)
                onDone()
            }
            is ActivationResult.Rejected -> showMessage(result.error.messageRes)
        }
    }

    fun remove() = launchSafe { manager.removeLicense() }
}

@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val vm = appViewModel { LicenseViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var key by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)
    val ext = SalonTheme.extended

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.settings_license), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.license_status), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val good = state.status in setOf(LicenseStatus.ACTIVE, LicenseStatus.NOT_ENFORCED, LicenseStatus.TRIAL)
                    StatusBadge(
                        stringResource(state.status.labelRes),
                        container = if (good) ext.positiveContainer else ext.warningContainer,
                        content = if (good) ext.positive else ext.warning,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val payload = state.payload
                if (payload != null) {
                    LabeledValueRow(stringResource(R.string.license_id), payload.licenseId)
                    LabeledValueRow(stringResource(R.string.license_business), payload.businessName)
                    LabeledValueRow(stringResource(R.string.license_plan), stringResource(payload.plan.labelRes))
                    LabeledValueRow(stringResource(R.string.license_issued), DateTimeUtils.formatDate(payload.issuedOn))
                    LabeledValueRow(
                        stringResource(R.string.license_expires),
                        payload.expiresOn?.let { DateTimeUtils.formatDate(it) } ?: stringResource(R.string.license_never),
                    )
                } else if (state.trialEndsOn != null) {
                    LabeledValueRow(stringResource(R.string.license_trial_ends), DateTimeUtils.formatDate(state.trialEndsOn!!))
                }
                state.daysRemaining?.let { LabeledValueRow(stringResource(R.string.license_days_left), it.toString(), emphasize = true) }
                if (!vm.enforced) {
                    Text(
                        stringResource(R.string.license_owner_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ContentCard {
                Text(stringResource(R.string.license_installation_id), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.installationId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(vm.installationId)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                }
                Text(stringResource(R.string.license_installation_hint), style = MaterialTheme.typography.bodySmall)
            }

            ContentCard {
                Text(stringResource(R.string.license_activate_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (!vm.canActivate) {
                    Text(stringResource(R.string.license_error_not_configured), style = MaterialTheme.typography.bodySmall)
                } else {
                    FormTextField(key, { key = it }, stringResource(R.string.license_key), singleLine = false, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.activate(key) { key = "" } }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.license_activate))
                    }
                }
                if (state.payload != null) {
                    TextButton(onClick = { confirmRemove = true }) {
                        Text(stringResource(R.string.license_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                stringResource(R.string.license_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.license_remove),
            message = stringResource(R.string.license_remove_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmRemove = false
                vm.remove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
