package com.dtpos.salonmanager.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.InitialsAvatar
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.StatusBadge
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.account.AccessPolicy
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountStatus
import com.dtpos.salonmanager.services.account.RefreshResult
import com.dtpos.salonmanager.services.export.ExternalApps

class AccountViewModel(private val container: AppContainer) : BaseViewModel() {
    private val accounts = container.accountManager
    val state = accounts.state
    val checking = accounts.checking
    val branding = accounts.branding
    val appConfig = accounts.appConfig

    fun user() = accounts.currentUser()
    fun cached() = accounts.cachedAccount()

    fun verify() = launchSafe {
        when (accounts.refresh()) {
            RefreshResult.Ok -> showMessage(R.string.account_verified)
            is RefreshResult.Failed -> showMessage(R.string.status_check_failed)
            else -> Unit
        }
    }

    fun signOut() = launchSafe { accounts.signOut() }
}

fun accountStatusLabel(status: AccountStatus): Int = when (status) {
    AccountStatus.PENDING -> R.string.account_status_pending
    AccountStatus.APPROVED -> R.string.account_status_approved
    AccountStatus.PAYMENT_PENDING -> R.string.account_status_payment_pending
    AccountStatus.SUSPENDED -> R.string.account_status_suspended
    AccountStatus.BLOCKED -> R.string.account_status_blocked
    AccountStatus.EXPIRED -> R.string.account_status_expired
    AccountStatus.REJECTED -> R.string.account_status_rejected
}

fun planLabel(plan: String?): Int? = when (plan?.uppercase()) {
    "MONTHLY" -> R.string.plan_month_1
    "QUARTERLY" -> R.string.plan_month_3
    "HALF_YEARLY" -> R.string.plan_month_6
    "YEARLY" -> R.string.plan_year_1
    "LIFETIME" -> R.string.plan_lifetime
    "TRIAL" -> R.string.plan_trial
    "CUSTOM" -> R.string.plan_custom
    else -> null
}

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val vm = appViewModel { AccountViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val branding by vm.branding.collectAsStateWithLifecycle()
    val config by vm.appConfig.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)

    // Re-read after every state change (verification updates the cache).
    val entry = remember(state, checking) { vm.cached() }
    val user = remember(state) { vm.user() }
    val account = (state as? AccessState.Allowed)?.account ?: entry?.account
    val notAssigned = stringResource(R.string.account_not_assigned)

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.account_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(user?.displayName ?: account?.ownerName ?: "?")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user?.displayName ?: account?.ownerName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    account?.let {
                        val status = AccessPolicy.effectiveStatus(it, System.currentTimeMillis())
                        val ok = status == AccountStatus.APPROVED
                        StatusBadge(
                            stringResource(accountStatusLabel(status)),
                            container = if (ok) SalonTheme.extended.positiveContainer else SalonTheme.extended.warningContainer,
                            content = if (ok) SalonTheme.extended.positive else SalonTheme.extended.warning,
                        )
                    }
                }
            }
            account?.let { acc ->
                ContentCard {
                    LabeledValueRow(stringResource(R.string.account_salon), acc.salonName.ifBlank { "-" })
                    LabeledValueRow(stringResource(R.string.account_owner), acc.ownerName.ifBlank { "-" })
                    LabeledValueRow(stringResource(R.string.account_phone), acc.phone.ifBlank { "-" })
                    LabeledValueRow(stringResource(R.string.account_customer_id), acc.customerId ?: notAssigned)
                    LabeledValueRow(stringResource(R.string.account_business_id), acc.businessId ?: notAssigned)
                    LabeledValueRow(stringResource(R.string.account_license_id), acc.licenseId ?: notAssigned)
                    LabeledValueRow(stringResource(R.string.account_plan), planLabel(acc.plan)?.let { stringResource(it) } ?: acc.plan ?: "-")
                    LabeledValueRow(
                        stringResource(R.string.account_expires),
                        acc.expiresAtMillis?.let { DateTimeUtils.formatDate(it) } ?: stringResource(R.string.account_no_expiry),
                        emphasize = true,
                    )
                }
            }
            ContentCard {
                entry?.let {
                    LabeledValueRow(stringResource(R.string.account_last_verified), DateTimeUtils.formatDateTime(it.verifiedAtMillis))
                    val left = config.offlineGraceDays - AccessPolicy.daysSince(it.verifiedAtMillis, System.currentTimeMillis())
                    Text(
                        stringResource(R.string.account_offline_days, left.coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = vm::verify, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_check_now))
                }
            }
            val whatsapp = branding.whatsapp.ifBlank { branding.contactNumber }
            if (whatsapp.isNotBlank() || branding.contactNumber.isNotBlank()) {
                val message = stringResource(R.string.support_message, account?.customerId ?: user?.email ?: "-")
                val notInstalled = stringResource(R.string.whatsapp_not_installed)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (whatsapp.isNotBlank()) {
                        OutlinedButton(onClick = {
                            if (!ExternalApps.openWhatsAppChat(context, whatsapp, message)) {
                                Toast.makeText(context, notInstalled, Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.support_whatsapp))
                        }
                    }
                    if (branding.contactNumber.isNotBlank()) {
                        OutlinedButton(onClick = { ExternalApps.dial(context, branding.contactNumber) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.support_call))
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.account_signout_title),
            message = stringResource(R.string.account_signout_message),
            confirmLabel = stringResource(R.string.action_sign_out),
            icon = Icons.AutoMirrored.Filled.Logout,
            onConfirm = {
                confirmSignOut = false
                vm.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
