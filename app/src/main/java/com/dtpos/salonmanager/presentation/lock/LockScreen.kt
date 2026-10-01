package com.dtpos.salonmanager.presentation.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.BrandBackground
import com.dtpos.salonmanager.presentation.components.BrandMonogram
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.security.BiometricAuthenticator
import com.dtpos.salonmanager.services.security.LockType
import com.dtpos.salonmanager.services.security.ProtectedArea
import com.dtpos.salonmanager.services.security.SecurityManager
import com.dtpos.salonmanager.services.security.VerifyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LockUiState(
    val input: String = "",
    val busy: Boolean = false,
    val error: Int? = null,
    val attemptsLeft: Int? = null,
    val lockedUntil: Long? = null,
    val recoveryError: Boolean = false,
)

class LockViewModel(private val security: SecurityManager) : BaseViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun onInput(value: String) = _state.update { it.copy(input = value.take(32), error = null) }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.busy || current.input.length >= Validators.MAX_PIN_LENGTH) return
        _state.update { it.copy(input = it.input + digit, error = null) }
    }

    fun onBackspace() = _state.update { it.copy(input = it.input.dropLast(1), error = null) }

    fun submit() {
        val secret = _state.value.input
        if (secret.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        launchSafe {
            when (val result = security.verify(secret)) {
                VerifyResult.Success -> _state.value = LockUiState()
                is VerifyResult.Wrong -> _state.update {
                    it.copy(input = "", busy = false, error = R.string.lock_wrong, attemptsLeft = result.attemptsLeft, lockedUntil = null)
                }
                is VerifyResult.LockedOut -> _state.update {
                    it.copy(input = "", busy = false, error = R.string.lock_locked_out, lockedUntil = result.untilMillis)
                }
            }
        }
    }

    fun recover(code: String, onDone: () -> Unit) = launchSafe {
        if (security.resetWithRecoveryCode(code)) {
            showMessage(R.string.lock_recovery_success)
            onDone()
        } else {
            _state.update { it.copy(recoveryError = true) }
        }
    }

    fun onBiometricSuccess() = security.unlockWithBiometric()
}

/** Wraps protected content; shows the lock screen until the owner unlocks this session. */
@Composable
fun SecuredArea(area: ProtectedArea, content: @Composable () -> Unit) {
    val security = LocalAppContainer.current.securityManager
    val config by security.config.collectAsStateWithLifecycle()
    val unlocked by security.unlocked.collectAsStateWithLifecycle()
    if (config?.protects(area) == true && !unlocked) {
        LockScreen(area = area, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        content()
    }
}

@Composable
fun LockScreen(area: ProtectedArea, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "lock_${area.name}") { LockViewModel(it.securityManager) }
    val state by vm.state.collectAsStateWithLifecycle()
    val config by container.securityManager.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lockType = config?.lockType ?: LockType.PIN
    val biometricAllowed = config?.biometricEnabled == true && remember { BiometricAuthenticator.isAvailable(context) }
    var showRecovery by remember { mutableStateOf(false) }

    val biometricTitle = stringResource(R.string.lock_biometric_title)
    val biometricSubtitle = stringResource(R.string.lock_biometric_subtitle)
    val usePin = stringResource(if (lockType == LockType.PIN) R.string.lock_use_pin else R.string.lock_use_password)
    val promptBiometric = {
        BiometricAuthenticator.authenticate(
            context = context,
            title = biometricTitle,
            subtitle = biometricSubtitle,
            negativeText = usePin,
            onSuccess = vm::onBiometricSuccess,
            onFailure = {},
        )
    }
    LaunchedEffect(biometricAllowed) { if (biometricAllowed) promptBiometric() }

    val accent = SalonTheme.glass.accent
    com.dtpos.salonmanager.presentation.account.SystemBarIcons(lightBackground = false)
    BrandBackground(modifier = modifier, animated = false) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            BrandMonogram(width = 112.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (lockType == LockType.PIN) R.string.lock_enter_pin else R.string.lock_enter_password),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                stringResource(
                    when (area) {
                        ProtectedArea.APP -> R.string.lock_area_app
                        ProtectedArea.REPORTS -> R.string.lock_area_reports
                        ProtectedArea.EXPENSES -> R.string.lock_area_expenses
                        ProtectedArea.SETTINGS -> R.string.lock_area_settings
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            if (lockType == LockType.PIN) {
                PinDots(length = state.input.length)
                Spacer(Modifier.height(12.dp))
                ErrorLine(state)
                Spacer(Modifier.height(12.dp))
                PinPad(
                    onDigit = vm::onDigit,
                    onBackspace = vm::onBackspace,
                    onSubmit = vm::submit,
                    enabled = !state.busy,
                )
            } else {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInput,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                    label = { Text(stringResource(R.string.lock_password_label)) },
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        cursorColor = accent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                ErrorLine(state)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::submit, enabled = !state.busy, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.lock_unlock))
                }
            }

            Spacer(Modifier.height(16.dp))
            if (biometricAllowed) {
                TextButton(onClick = promptBiometric) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = accent)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.lock_use_biometric), color = accent)
                }
            }
            TextButton(onClick = { showRecovery = true }) {
                Text(stringResource(R.string.lock_forgot), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }

    if (showRecovery) {
        RecoveryDialog(
            error = state.recoveryError,
            onSubmit = { code -> vm.recover(code) { showRecovery = false } },
            onDismiss = { showRecovery = false },
        )
    }
}

@Composable
private fun ErrorLine(state: LockUiState) {
    val text = when {
        state.lockedUntil != null -> stringResource(R.string.lock_locked_out_until, DateTimeUtils.formatTime(state.lockedUntil))
        state.error != null && state.attemptsLeft != null -> stringResource(R.string.lock_wrong_attempts, state.attemptsLeft)
        state.error != null -> stringResource(state.error)
        else -> ""
    }
    Text(text, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

@Composable
private fun PinDots(length: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxOf(4, length)) { i ->
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(if (i < length) SalonTheme.glass.accent else Color.White.copy(alpha = 0.25f)),
            )
        }
    }
}

@Composable
private fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, enabled: Boolean) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d -> PinKey(d.toString(), enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.lock_backspace), tint = Color.White)
            }
            PinKey("0", enabled) { onDigit('0') }
            FilledTonalIconButton(onClick = onSubmit, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.lock_unlock))
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 28.sp, color = Color.White)
        }
    }
}

@Composable
private fun RecoveryDialog(error: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_recovery_title)) },
        text = {
            Column {
                Text(stringResource(R.string.lock_recovery_message))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(20) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.lock_recovery_invalid)) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.lock_recovery_code)) },
                )
            }
        },
        confirmButton = { Button(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
