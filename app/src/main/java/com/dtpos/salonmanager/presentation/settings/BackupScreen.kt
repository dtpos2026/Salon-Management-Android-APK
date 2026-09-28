package com.dtpos.salonmanager.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.InfoBanner
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.backup.AppRestarter
import com.dtpos.salonmanager.services.backup.BackupInspection
import com.dtpos.salonmanager.services.backup.BackupManager
import com.dtpos.salonmanager.services.backup.LocalBackup
import com.dtpos.salonmanager.services.backup.PreparedRestore
import com.dtpos.salonmanager.services.export.ShareHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File

sealed interface RestoreStep {
    data object None : RestoreStep
    data class AskPassword(val data: ByteArray, val wrong: Boolean) : RestoreStep
    data class Confirm(val prepared: PreparedRestore) : RestoreStep
    data object Done : RestoreStep
}

data class BackupUiState(
    val busy: Boolean = false,
    val localBackups: List<LocalBackup> = emptyList(),
    val restore: RestoreStep = RestoreStep.None,
)

class BackupViewModel(private val container: AppContainer) : BaseViewModel() {
    private val manager = container.backupManager
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    val lastBackupAt: StateFlow<Long> = container.settingsRepository.observeLong(SettingKeys.BACKUP_LAST_MANUAL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val shareFile = MutableSharedFlow<File>(extraBufferCapacity = 1)

    /** Password chosen for the next export (empty = unencrypted). */
    var exportPassword: CharArray? = null

    init {
        refreshLocal()
    }

    private fun refreshLocal() {
        _state.update { it.copy(localBackups = manager.localBackups()) }
    }

    private fun busy(block: suspend () -> Unit) = launchSafe {
        _state.update { it.copy(busy = true) }
        try {
            block()
        } finally {
            _state.update { it.copy(busy = false) }
            refreshLocal()
        }
    }

    fun exportTo(uri: Uri) = busy {
        val error = manager.exportTo(uri, exportPassword)
        exportPassword = null
        showMessage(error?.messageRes ?: R.string.backup_saved)
    }

    fun shareLatest() = busy {
        val file = manager.createLocalBackup("share")
        if (file == null) {
            showMessage(R.string.backup_error_write)
        } else {
            container.settingsRepository.putLong(SettingKeys.BACKUP_LAST_MANUAL, System.currentTimeMillis())
            shareFile.tryEmit(file)
        }
    }

    fun exportCsv(uri: Uri) = busy {
        val range = DateRange(DateTimeUtils.today().minusYears(20), DateTimeUtils.today())
        showMessage(if (container.dataExporter.exportCsvZip(uri, range)) R.string.export_done else R.string.export_failed)
    }

    fun startRestore(uri: Uri) = busy {
        val data = manager.readUri(uri)
        if (data == null) showMessage(R.string.backup_error_read) else inspect(data, null)
    }

    fun restoreLocal(backup: LocalBackup) = busy {
        val data = try {
            backup.file.readBytes()
        } catch (e: Exception) {
            null
        }
        if (data == null) showMessage(R.string.backup_error_read) else inspect(data, null)
    }

    fun submitPassword(password: String) {
        val step = _state.value.restore as? RestoreStep.AskPassword ?: return
        busy { inspect(step.data, password.toCharArray()) }
    }

    private suspend fun inspect(data: ByteArray, password: CharArray?) {
        when (val result = manager.prepareRestore(data, password)) {
            BackupInspection.NeedsPassword -> _state.update { it.copy(restore = RestoreStep.AskPassword(data, wrong = false)) }
            is BackupInspection.Ready -> _state.update { it.copy(restore = RestoreStep.Confirm(result.prepared)) }
            is BackupInspection.Invalid -> {
                if (result.reason == com.dtpos.salonmanager.services.backup.BackupError.WRONG_PASSWORD) {
                    _state.update { it.copy(restore = RestoreStep.AskPassword(data, wrong = true)) }
                } else {
                    _state.update { it.copy(restore = RestoreStep.None) }
                    showMessage(result.reason.messageRes)
                }
            }
        }
    }

    fun confirmRestore() {
        val step = _state.value.restore as? RestoreStep.Confirm ?: return
        busy {
            val error = manager.applyRestore(step.prepared)
            if (error == null) {
                _state.update { it.copy(restore = RestoreStep.Done) }
            } else {
                _state.update { it.copy(restore = RestoreStep.None) }
                showMessage(error.messageRes)
            }
        }
    }

    fun cancelRestore() = _state.update { it.copy(restore = RestoreStep.None) }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val vm = appViewModel { BackupViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lastBackup by vm.lastBackupAt.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var askExportPassword by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)
    val shareTitle = stringResource(R.string.backup_share)
    LaunchedEffect(vm) { vm.shareFile.collect { ShareHelper.shareFile(context, it, BackupManager.MIME_TYPE, shareTitle) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)) { uri ->
        if (uri != null) vm.exportTo(uri) else vm.exportPassword = null
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.startRestore(uri)
    }

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.settings_backup), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                InfoBanner(
                    text = stringResource(R.string.backup_intro),
                    icon = Icons.Filled.Backup,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            item {
                ContentCard {
                    Text(stringResource(R.string.backup_create_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (lastBackup > 0) stringResource(R.string.backup_last, DateTimeUtils.formatDateTime(lastBackup))
                        else stringResource(R.string.backup_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { askExportPassword = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_save_file))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::shareLatest, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_share))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { csvLauncher.launch("SalonData_${DateTimeUtils.formatIso(DateTimeUtils.today())}.zip") },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.TableView, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_export_csv))
                    }
                }
            }
            item {
                ContentCard {
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.backup_restore_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_restore_file))
                    }
                }
            }
            if (state.localBackups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.backup_local_title)) }
                item {
                    Text(
                        stringResource(R.string.backup_local_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.localBackups, key = { it.file.name }) { backup ->
                    ContentCard {
                        ListItem(
                            headlineContent = { Text(DateTimeUtils.formatDateTime(backup.createdAt)) },
                            supportingContent = { Text("${backup.file.name} · ${backup.sizeBytes / 1024} KB") },
                            trailingContent = {
                                IconButton(onClick = { vm.restoreLocal(backup) }, enabled = !state.busy) {
                                    Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.backup_restore_title))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_title),
            message = stringResource(R.string.backup_password_message),
            confirmLabel = stringResource(R.string.action_continue),
            allowEmpty = true,
            wrong = false,
            onConfirm = { password ->
                vm.exportPassword = password.takeIf { it.isNotEmpty() }?.toCharArray()
                askExportPassword = false
                exportLauncher.launch(BackupManager.suggestedFileName())
            },
            onDismiss = { askExportPassword = false },
        )
    }

    when (val step = state.restore) {
        is RestoreStep.AskPassword -> PasswordDialog(
            title = stringResource(R.string.backup_encrypted_title),
            message = stringResource(R.string.backup_encrypted_message),
            confirmLabel = stringResource(R.string.action_continue),
            allowEmpty = false,
            wrong = step.wrong,
            onConfirm = vm::submitPassword,
            onDismiss = vm::cancelRestore,
        )
        is RestoreStep.Confirm -> AlertDialog(
            onDismissRequest = vm::cancelRestore,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.backup_confirm_message))
                    val m = step.prepared.manifest
                    Text(stringResource(R.string.backup_info_created, DateTimeUtils.formatDateTime(m.createdAt)), style = MaterialTheme.typography.bodySmall)
                    m.businessName?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.backup_info_business, it), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.backup_info_version, m.appVersion), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmRestore,
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_confirm_restore)) }
            },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
        RestoreStep.Done -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Restore, contentDescription = null, tint = SalonTheme.extended.positive) },
            title = { Text(stringResource(R.string.backup_restored_title)) },
            text = { Text(stringResource(R.string.backup_restored_message)) },
            confirmButton = { Button(onClick = { AppRestarter.restart(context) }) { Text(stringResource(R.string.backup_restart)) } },
        )
        RestoreStep.None -> Unit
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    allowEmpty: Boolean,
    wrong: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text(stringResource(R.string.backup_error_password)) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.lock_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = allowEmpty || password.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
