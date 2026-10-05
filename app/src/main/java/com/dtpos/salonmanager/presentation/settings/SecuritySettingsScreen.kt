package com.dtpos.salonmanager.presentation.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.services.security.BiometricAuthenticator
import com.dtpos.salonmanager.services.security.LockType
import com.dtpos.salonmanager.services.security.ProtectedArea
import com.dtpos.salonmanager.services.security.VerifyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecuritySettingsViewModel(container: AppContainer) : BaseViewModel() {
    private val security = container.securityManager
    val config = security.config

    private val _recoveryCode = MutableStateFlow<String?>(null)
    /** One-time recovery code to show right after a PIN/password is set. */
    val recoveryCode: StateFlow<String?> = _recoveryCode.asStateFlow()

    /** Returns an error message resource, or null when the new secret was saved. */
    fun setSecret(type: LockType, secret: String, confirm: String, onDone: () -> Unit) {
        val valid = if (type == LockType.PIN) Validators.pin(secret) else Validators.password(secret)
        if (valid !is FieldResult.Valid) return showMessage(valid.errorOrNull!!.messageRes)
        if (secret != confirm) return showMessage(ValidationError.CONFIRMATION_MISMATCH.messageRes)
        launchSafe {
            _recoveryCode.value = security.setCredential(type, valid.value)
            onDone()
        }
    }

    fun verifyThen(secret: String, onVerified: () -> Unit) = launchSafe {
        when (val result = security.verify(secret, ownerOnly = true)) {
            VerifyResult.Success -> onVerified()
            is VerifyResult.Wrong -> showMessage(R.string.lock_wrong_attempts, result.attemptsLeft)
            is VerifyResult.LockedOut -> showMessage(R.string.lock_locked_out)
        }
    }

    fun disable() = launchSafe {
        security.disable()
        showMessage(R.string.security_disabled)
    }

    fun setBiometric(enabled: Boolean) = launchSafe { security.setBiometricEnabled(enabled) }

    fun setProtection(area: ProtectedArea, enabled: Boolean) = launchSafe { security.setProtection(area, enabled) }

    fun lockNow() = security.lockNow()

    /** Sets or (blank) removes a manager / assistant PIN. */
    fun setStaffPin(role: com.dtpos.salonmanager.services.security.StaffAccess, pin: String, confirm: String, onDone: () -> Unit) {
        if (pin.isNotBlank()) {
            val valid = Validators.pin(pin)
            if (valid !is FieldResult.Valid) return showMessage(valid.errorOrNull!!.messageRes)
            if (pin != confirm) return showMessage(ValidationError.CONFIRMATION_MISMATCH.messageRes)
        }
        launchSafe {
            if (security.setStaffPin(role, pin)) {
                showMessage(if (pin.isBlank()) R.string.security_staff_pin_removed else R.string.security_staff_pin_saved)
                onDone()
            } else {
                showMessage(R.string.security_staff_pin_taken)
            }
        }
    }

    fun dismissRecovery() {
        _recoveryCode.value = null
    }
}

private sealed interface SecurityDialog {
    data class SetSecret(val type: LockType) : SecurityDialog
    data class StaffPin(val role: com.dtpos.salonmanager.services.security.StaffAccess) : SecurityDialog
    data class VerifyCurrent(val then: () -> Unit) : SecurityDialog
}

@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SecuritySettingsViewModel(it) }
    val config by vm.config.collectAsStateWithLifecycle()
    val recovery by vm.recoveryCode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricAuthenticator.isAvailable(context) }
    var dialog by remember { mutableStateOf<SecurityDialog?>(null) }
    MessageEffect(vm.messages, snackbar)
    val c = config

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.settings_security), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (c == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ContentCard {
                    Text(stringResource(R.string.security_app_lock), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (c.lockType) {
                                LockType.NONE -> R.string.security_status_off
                                LockType.PIN -> R.string.security_status_pin
                                LockType.PASSWORD -> R.string.security_status_password
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!c.isEnabled) {
                        Button(onClick = { dialog = SecurityDialog.SetSecret(LockType.PIN) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.security_set_pin))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { dialog = SecurityDialog.SetSecret(LockType.PASSWORD) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Key, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.security_set_password))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { dialog = SecurityDialog.VerifyCurrent { dialog = SecurityDialog.SetSecret(c.lockType) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.security_change)) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = vm::lockNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.security_lock_now)) }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { dialog = SecurityDialog.VerifyCurrent { vm.disable(); dialog = null } }) {
                            Text(stringResource(R.string.security_turn_off), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (c.isEnabled) {
                item { SectionHeader(stringResource(R.string.security_protect_title)) }
                item {
                    ContentCard {
                        ToggleRow(stringResource(R.string.security_protect_app), c.lockOnStart) { vm.setProtection(ProtectedArea.APP, it) }
                        ToggleRow(stringResource(R.string.security_protect_reports), c.protectReports) { vm.setProtection(ProtectedArea.REPORTS, it) }
                        ToggleRow(stringResource(R.string.security_protect_expenses), c.protectExpenses) { vm.setProtection(ProtectedArea.EXPENSES, it) }
                        ToggleRow(
                            stringResource(R.string.security_protect_settings),
                            c.protectSettings,
                            stringResource(R.string.security_protect_settings_hint),
                        ) { vm.setProtection(ProtectedArea.SETTINGS, it) }
                    }
                }
                item { SectionHeader(stringResource(R.string.security_staff_title)) }
                item {
                    ContentCard {
                        Text(stringResource(R.string.security_staff_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(
                            Triple(com.dtpos.salonmanager.services.security.StaffAccess.MANAGER, c.hasManagerPin, R.string.security_staff_manager),
                            Triple(com.dtpos.salonmanager.services.security.StaffAccess.ASSISTANT, c.hasAssistantPin, R.string.security_staff_assistant),
                        ).forEach { (role, set, label) ->
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (role == com.dtpos.salonmanager.services.security.StaffAccess.MANAGER) R.string.security_staff_manager_can else R.string.security_staff_assistant_can),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton(onClick = { dialog = SecurityDialog.StaffPin(role) }) {
                                    Text(stringResource(if (set) R.string.security_staff_change else R.string.security_staff_set))
                                }
                                if (set) {
                                    TextButton(onClick = { vm.setStaffPin(role, "", "") {} }) {
                                        Text(stringResource(R.string.security_staff_remove), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    ContentCard {
                        ToggleRow(
                            stringResource(R.string.security_biometric),
                            c.biometricEnabled && biometricAvailable,
                            stringResource(if (biometricAvailable) R.string.security_biometric_hint else R.string.security_biometric_unavailable),
                        ) { if (biometricAvailable) vm.setBiometric(it) }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.security_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (val d = dialog) {
        is SecurityDialog.SetSecret -> SetSecretDialog(
            type = d.type,
            onSave = { secret, confirm -> vm.setSecret(d.type, secret, confirm) { dialog = null } },
            onDismiss = { dialog = null },
        )
        is SecurityDialog.StaffPin -> SetSecretDialog(
            type = LockType.PIN,
            onSave = { secret, confirm -> vm.setStaffPin(d.role, secret, confirm) { dialog = null } },
            onDismiss = { dialog = null },
        )
        is SecurityDialog.VerifyCurrent -> VerifyDialog(
            numeric = c?.lockType == LockType.PIN,
            onVerify = { secret -> vm.verifyThen(secret, d.then) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    recovery?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Key, contentDescription = null) },
            title = { Text(stringResource(R.string.security_recovery_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.security_recovery_message))
                    Spacer(Modifier.height(12.dp))
                    Text(code, fontFamily = FontFamily.Monospace, fontSize = 24.sp, style = MaterialTheme.typography.headlineSmall)
                }
            },
            confirmButton = { Button(onClick = vm::dismissRecovery) { Text(stringResource(R.string.security_recovery_done)) } },
        )
    }
}

@Composable
private fun SetSecretDialog(type: LockType, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val numeric = type == LockType.PIN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (numeric) R.string.security_set_pin else R.string.security_set_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (numeric) R.string.security_pin_rules else R.string.security_password_rules),
                    style = MaterialTheme.typography.bodySmall,
                )
                SecretField(secret, { secret = it }, stringResource(R.string.security_new), numeric)
                SecretField(confirm, { confirm = it }, stringResource(R.string.security_confirm), numeric)
            }
        },
        confirmButton = { Button(onClick = { onSave(secret, confirm) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun VerifyDialog(numeric: Boolean, onVerify: (String) -> Unit, onDismiss: () -> Unit) {
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.security_enter_current)) },
        text = { SecretField(secret, { secret = it }, stringResource(R.string.security_current), numeric) },
        confirmButton = { Button(onClick = { onVerify(secret) }, enabled = secret.isNotEmpty()) { Text(stringResource(R.string.action_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, numeric: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(if (numeric) v.filter(Char::isDigit).take(Validators.MAX_PIN_LENGTH) else v.take(64)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
