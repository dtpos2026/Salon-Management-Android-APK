package com.dtpos.salonmanager.presentation.account

import com.dtpos.salonmanager.presentation.common.showWhatsAppResult
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.BuildConfig
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.UiText
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.asString
import com.dtpos.salonmanager.presentation.components.BrandBackground
import com.dtpos.salonmanager.presentation.components.BrandHeader
import com.dtpos.salonmanager.presentation.components.Glass
import com.dtpos.salonmanager.presentation.components.GlassCard
import com.dtpos.salonmanager.presentation.components.GlassOutlinedButton
import com.dtpos.salonmanager.presentation.components.GlassPrimaryButton
import com.dtpos.salonmanager.presentation.components.GlassTextField
import com.dtpos.salonmanager.presentation.components.PoweredByFooter
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.account.AccessPolicy
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountRegistration
import com.dtpos.salonmanager.services.account.AccountStatus
import com.dtpos.salonmanager.services.account.Branding
import com.dtpos.salonmanager.services.account.CloudAccount
import com.dtpos.salonmanager.services.account.LocationAccess
import com.dtpos.salonmanager.services.account.LocationStatus
import com.dtpos.salonmanager.services.account.RemoteAppConfig
import com.dtpos.salonmanager.services.account.SignedInUser
import com.dtpos.salonmanager.services.backup.AppRestarter
import com.dtpos.salonmanager.services.export.ExternalApps
import com.dtpos.salonmanager.services.prefs.AppLanguage

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Sets dark or light status/navigation bar icons for the current screen. */
@Composable
fun SystemBarIcons(lightBackground: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}

/**
 * Shows the brand intro, then sign-in / registration / approval screens until the account may use
 * the app; then [content] (the salon app). Approved accounts open offline from the local cache.
 */
@Composable
fun AccessGate(content: @Composable () -> Unit) {
    val vm = appViewModel { AccessViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val fast by com.dtpos.salonmanager.presentation.common.LocalAppContainer.current.uiPreferences.fastMode.collectAsStateWithLifecycle()
    // Fast mode skips the brand intro animation.
    var introDone by rememberSaveable { mutableStateOf(IntroState.shown || fast) }
    if (!introDone) {
        SystemBarIcons(lightBackground = false)
        IntroSplash(onFinished = {
            IntroState.shown = true
            introDone = true
        })
        return
    }
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            if (fast) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else fadeIn(tween(380)) togetherWith fadeOut(tween(220))
        },
        label = "access",
    ) { s ->
        if (s !is AccessState.Allowed) SystemBarIcons(lightBackground = false)
        when (s) {
            AccessState.Loading -> LoadingScreen()
            AccessState.NotConfigured -> NotConfiguredScreen(vm)
            AccessState.SignedOut -> LoginScreen(vm)
            is AccessState.NeedsProfile -> RegistrationScreen(vm, s.user)
            is AccessState.Restricted -> StatusScreen(vm, s.account, s.status)
            is AccessState.NeedsVerification -> VerificationScreen(vm, s)
            is AccessState.DeviceNotApproved -> DeviceApprovalScreen(vm, s)
            is AccessState.DeviceBlocked -> DeviceBlockedScreen(vm, s)
            is AccessState.WrongDevice -> WrongDeviceScreen(vm, s)
            is AccessState.UpdateRequired -> UpdateRequiredScreen(vm, s.config)
            is AccessState.Allowed -> LocationGate(content)
        }
    }
}

@Composable
private fun GlassPage(content: @Composable ColumnScope.() -> Unit) {
    BrandBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}

@Composable
private fun NoticeText(notice: UiText?) {
    if (notice == null) return
    Spacer(Modifier.height(14.dp))
    Text(
        notice.asString(),
        color = Color(0xFFFFD7D7),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33FF5C5C))
            .padding(12.dp),
    )
}

@Composable
private fun LoadingScreen() {
    GlassPage {
        Spacer(Modifier.height(120.dp))
        BrandHeader(stringResource(R.string.brand_tagline))
        Spacer(Modifier.height(36.dp))
        CircularProgressIndicator(color = SalonTheme.glass.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun LanguageMenu() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = container.uiPreferences.language
    Box {
        GlassOutlinedButton(text = languageName(current), onClick = { open = true }, icon = Icons.Filled.Language)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(languageName(language)) },
                    onClick = {
                        open = false
                        if (language != current) {
                            container.uiPreferences.setLanguage(language)
                            AppRestarter.restart(context)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun languageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.URDU -> R.string.language_urdu
        AppLanguage.ROMAN_URDU -> R.string.language_roman_urdu
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportRow(branding: Branding, reference: String) {
    val context = LocalContext.current
    val whatsapp = branding.whatsapp.ifBlank { branding.contactNumber }
    if (whatsapp.isBlank() && branding.contactNumber.isBlank() && branding.email.isBlank()) return
    val message = stringResource(R.string.support_message, reference)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (whatsapp.isNotBlank()) {
            GlassOutlinedButton(stringResource(R.string.support_whatsapp), icon = Icons.AutoMirrored.Filled.Chat, onClick = {
                context.showWhatsAppResult(ExternalApps.whatsAppText(context, whatsapp, message))
            })
        }
        if (branding.contactNumber.isNotBlank()) {
            GlassOutlinedButton(stringResource(R.string.support_call), icon = Icons.Filled.Call, onClick = {
                ExternalApps.dial(context, branding.contactNumber)
            })
        }
        if (branding.email.isNotBlank()) {
            GlassOutlinedButton(stringResource(R.string.support_email), icon = Icons.Filled.Email, onClick = {
                ExternalApps.email(context, branding.email, branding.appName)
            })
        }
    }
    if (branding.supportText.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(branding.supportText, color = Glass.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoText(info: UiText?) {
    if (info == null) return
    Spacer(Modifier.height(14.dp))
    Text(
        info.asString(),
        color = Color(0xFFD8FFE4),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x3341D17A))
            .padding(12.dp),
    )
}

/** Email + password: sign in, or create a new login (the salon details form follows). */
@Composable
private fun LoginScreen(vm: AccessViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val branding by vm.branding.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    GlassPage {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageMenu() }
        Spacer(Modifier.height(20.dp))
        BrandHeader(stringResource(R.string.brand_tagline), compact = creating)
        Spacer(Modifier.height(28.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (creating) R.string.login_create_title else R.string.login_welcome),
                color = Glass.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (creating) R.string.login_create_subtitle else R.string.login_subtitle),
                color = Glass.TextSecondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(18.dp))
            GlassTextField(email, { email = it.trim() }, stringResource(R.string.login_email), keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            GlassTextField(password, { password = it }, stringResource(R.string.login_password), password = true)
            if (creating) {
                Spacer(Modifier.height(10.dp))
                GlassTextField(confirm, { confirm = it }, stringResource(R.string.login_confirm_password), password = true)
            }
            Spacer(Modifier.height(18.dp))
            if (creating) {
                GlassPrimaryButton(
                    stringResource(R.string.login_create_account),
                    loading = busy,
                    icon = Icons.Filled.PersonAdd,
                    onClick = { vm.createLogin(email, password, confirm) },
                )
            } else {
                GlassPrimaryButton(
                    stringResource(R.string.login_sign_in),
                    loading = busy,
                    icon = Icons.AutoMirrored.Filled.Login,
                    onClick = { vm.signIn(email, password) },
                )
                TextButton(onClick = { vm.forgotPassword(email) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.login_forgot), color = Glass.TextSecondary)
                }
            }
            NoticeText(notice)
            InfoText(info)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = {
            creating = !creating
            confirm = ""
            vm.clearNotice()
        }) {
            Text(
                stringResource(if (creating) R.string.login_switch_to_sign_in else R.string.login_switch_to_create),
                color = SalonTheme.glass.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.login_new_salon_hint, branding.appName),
            color = Glass.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        SupportRow(branding, reference = email.ifBlank { "-" })
        Spacer(Modifier.height(24.dp))
        PoweredByFooter(stringResource(R.string.brand_by, branding.companyName))
    }
}

@Composable
private fun RegistrationScreen(vm: AccessViewModel, user: SignedInUser) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val branding by vm.branding.collectAsStateWithLifecycle()
    var salon by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf(user.displayName.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    GlassPage {
        Spacer(Modifier.height(8.dp))
        BrandHeader(stringResource(R.string.brand_tagline), compact = true)
        Spacer(Modifier.height(24.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.register_title), color = Glass.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.register_subtitle, branding.appName), color = Glass.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.register_signed_in_as, user.email.orEmpty()), color = SalonTheme.glass.accent, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            GlassTextField(salon, { salon = it }, stringResource(R.string.register_salon_name))
            Spacer(Modifier.height(10.dp))
            GlassTextField(owner, { owner = it }, stringResource(R.string.register_owner_name))
            Spacer(Modifier.height(10.dp))
            GlassTextField(phone, { phone = it }, stringResource(R.string.register_phone), keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(10.dp))
            GlassTextField(city, { city = it }, stringResource(R.string.register_city))
            Spacer(Modifier.height(10.dp))
            GlassTextField(address, { address = it }, stringResource(R.string.register_address), singleLine = false)
            Spacer(Modifier.height(18.dp))
            GlassPrimaryButton(
                stringResource(R.string.register_submit),
                loading = busy,
                icon = Icons.Filled.VerifiedUser,
                onClick = { vm.register(AccountRegistration(salon, owner, phone, city, address)) },
            )
            NoticeText(notice)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = vm::signOut) {
            Text(stringResource(R.string.register_use_other), color = Glass.TextSecondary)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector) {
    val accent = SalonTheme.glass.accent
    Box(
        Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)))
            .border(BorderStroke(1.dp, Glass.Border), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Glass.TextMuted, fontSize = 13.sp)
        Spacer(Modifier.size(12.dp))
        Text(value, color = Glass.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

/** Shared layout of the status pages: icon, title, message, details and actions. */
@Composable
private fun StatusPage(
    vm: AccessViewModel,
    icon: ImageVector,
    title: String,
    message: String,
    account: CloudAccount?,
    extra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    val notice by vm.notice.collectAsStateWithLifecycle()
    val branding by vm.branding.collectAsStateWithLifecycle()
    GlassPage {
        Spacer(Modifier.height(24.dp))
        StatusIcon(icon)
        Spacer(Modifier.height(20.dp))
        Text(title, color = Glass.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Glass.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            extra()
            account?.messageToUser?.let { msg ->
                Text(stringResource(R.string.status_admin_message, branding.appName), color = SalonTheme.glass.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(msg, color = Glass.TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
            }
            if (account != null) {
                DetailRow(stringResource(R.string.account_salon), account.salonName)
                DetailRow(stringResource(R.string.account_email), account.email)
                DetailRow(stringResource(R.string.account_customer_id), account.customerId)
                DetailRow(stringResource(R.string.account_license_id), account.licenseId)
                Spacer(Modifier.height(14.dp))
            }
            actions()
            NoticeText(notice)
        }
        Spacer(Modifier.height(22.dp))
        SupportRow(branding, reference = account?.customerId ?: account?.email ?: "-")
        Spacer(Modifier.height(18.dp))
        PoweredByFooter(stringResource(R.string.brand_by, branding.companyName))
    }
}

@Composable
private fun CheckAgainButton(vm: AccessViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    GlassPrimaryButton(stringResource(R.string.status_check_again), onClick = vm::checkAgain, loading = busy || checking, icon = Icons.Filled.Refresh)
}

@Composable
private fun SignOutButton(vm: AccessViewModel) {
    TextButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Glass.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.action_sign_out), color = Glass.TextSecondary)
    }
}

@Composable
private fun StatusScreen(vm: AccessViewModel, account: CloudAccount, status: AccountStatus) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    val name = branding.appName
    val (icon, title, message) = when (status) {
        AccountStatus.PENDING -> Triple(Icons.Filled.HourglassTop, stringResource(R.string.status_pending_title), stringResource(R.string.status_pending_message, name))
        AccountStatus.PAYMENT_PENDING -> Triple(Icons.Filled.Payments, stringResource(R.string.status_payment_title), stringResource(R.string.status_payment_message))
        AccountStatus.SUSPENDED -> Triple(Icons.Filled.PauseCircle, stringResource(R.string.status_suspended_title), stringResource(R.string.status_suspended_message, name))
        AccountStatus.BLOCKED -> Triple(Icons.Filled.Block, stringResource(R.string.status_blocked_title), stringResource(R.string.status_blocked_message, name))
        AccountStatus.EXPIRED -> Triple(
            Icons.Filled.EventBusy,
            stringResource(R.string.status_expired_title),
            account.expiresAtMillis?.let { stringResource(R.string.status_expired_message, DateTimeUtils.formatDate(it)) }
                ?: stringResource(R.string.status_expired_message_nodate),
        )
        AccountStatus.REJECTED -> Triple(Icons.Filled.Cancel, stringResource(R.string.status_rejected_title), stringResource(R.string.status_rejected_message, name))
        // Not shown for approved accounts; kept for completeness.
        AccountStatus.APPROVED -> Triple(Icons.Filled.VerifiedUser, stringResource(R.string.account_status_approved), "")
    }
    StatusPage(
        vm = vm,
        icon = icon,
        title = title,
        message = message,
        account = account,
        extra = {
            val due = account.pendingAmount?.takeIf { it > 0 }
            if (due != null && (status == AccountStatus.PAYMENT_PENDING || status == AccountStatus.EXPIRED)) {
                Text(
                    stringResource(R.string.status_amount_due, Money.groupDigits(due)),
                    color = SalonTheme.glass.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
            }
        },
    ) {
        CheckAgainButton(vm)
        Spacer(Modifier.height(4.dp))
        SignOutButton(vm)
    }
}

@Composable
private fun VerificationScreen(vm: AccessViewModel, state: AccessState.NeedsVerification) {
    StatusPage(
        vm = vm,
        icon = Icons.Filled.WifiOff,
        title = stringResource(R.string.status_verify_title),
        message = state.daysSinceVerified?.let { stringResource(R.string.status_verify_message_days, it) }
            ?: stringResource(R.string.status_verify_message),
        account = state.account,
    ) {
        CheckAgainButton(vm)
        Spacer(Modifier.height(4.dp))
        SignOutButton(vm)
    }
}

/** The account is approved for another phone; this one waits for the admin. */
@Composable
private fun DeviceApprovalScreen(vm: AccessViewModel, state: AccessState.DeviceNotApproved) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    val model = state.account.deviceModel ?: stringResource(R.string.device_unknown_model)
    StatusPage(
        vm = vm,
        icon = Icons.Filled.PhoneAndroid,
        title = stringResource(R.string.device_pending_title),
        message = stringResource(
            if (state.requested) R.string.device_pending_message else R.string.device_pending_offline,
            model,
            branding.appName,
        ),
        account = state.account,
    ) {
        CheckAgainButton(vm)
        Spacer(Modifier.height(4.dp))
        SignOutButton(vm)
    }
}

/**
 * The salon app opens only with location allowed and switched on. The shop's location is part
 * of the account check (DT sees it on the Super Admin map). Re-checked whenever the app resumes,
 * so allowing it in Android settings or switching location on opens the app at once.
 */
@Composable
private fun LocationGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    var status by remember { mutableStateOf(LocationAccess.status(context)) }
    var deniedForever by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val next = LocationAccess.status(context)
                if (next == LocationStatus.READY && status != LocationStatus.READY) container.deviceMonitor.reportNow()
                status = next
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        status = LocationAccess.status(context)
        if (result.values.any { it }) {
            container.deviceMonitor.reportNow()
        } else {
            // Denied with "don't ask again" (or twice on Android 11+): only Settings can allow it now.
            val activity = context.findActivity()
            deniedForever = activity != null && LocationAccess.PERMISSIONS.none { activity.shouldShowRequestPermissionRationale(it) }
        }
    }
    if (status == LocationStatus.READY) {
        content()
        return
    }
    SystemBarIcons(lightBackground = false)
    GlassPage {
        Spacer(Modifier.height(32.dp))
        StatusIcon(Icons.Filled.LocationOn)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(if (status == LocationStatus.SERVICES_OFF) R.string.location_off_title else R.string.location_required_title),
            color = Glass.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                when {
                    status == LocationStatus.SERVICES_OFF -> R.string.location_off_message
                    deniedForever -> R.string.location_denied_message
                    else -> R.string.location_required_message
                },
            ),
            color = Glass.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        when {
            status == LocationStatus.SERVICES_OFF -> GlassPrimaryButton(
                stringResource(R.string.location_turn_on),
                onClick = { ExternalApps.openLocationSettings(context) },
                icon = Icons.Filled.LocationOn,
            )
            deniedForever -> GlassPrimaryButton(
                stringResource(R.string.location_open_settings),
                onClick = { ExternalApps.openAppSettings(context) },
                icon = Icons.Filled.LocationOn,
            )
            else -> GlassPrimaryButton(
                stringResource(R.string.location_allow),
                onClick = { launcher.launch(LocationAccess.PERMISSIONS) },
                icon = Icons.Filled.LocationOn,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.location_privacy_note),
            color = Glass.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceBlockedScreen(vm: AccessViewModel, state: AccessState.DeviceBlocked) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    StatusPage(
        vm = vm,
        icon = Icons.Filled.PhonelinkLock,
        title = stringResource(R.string.device_blocked_title),
        message = stringResource(R.string.device_blocked_message, branding.appName),
        account = state.account,
    ) {
        CheckAgainButton(vm)
        Spacer(Modifier.height(4.dp))
        SignOutButton(vm)
    }
}

@Composable
private fun WrongDeviceScreen(vm: AccessViewModel, state: AccessState.WrongDevice) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    StatusPage(
        vm = vm,
        icon = Icons.Filled.PhonelinkLock,
        title = stringResource(R.string.device_title),
        message = stringResource(R.string.device_message, state.ownerEmail),
        account = null,
    ) {
        GlassPrimaryButton(stringResource(R.string.action_sign_out), onClick = vm::signOut, icon = Icons.AutoMirrored.Filled.Logout)
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.device_erase), color = Color(0xFFFFB4AB))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
            title = { Text(stringResource(R.string.device_erase_title)) },
            text = { Text(stringResource(R.string.device_erase_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        vm.eraseAndTakeOver()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_erase_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun UpdateRequiredScreen(vm: AccessViewModel, config: RemoteAppConfig) {
    val context = LocalContext.current
    StatusPage(
        vm = vm,
        icon = Icons.Filled.SystemUpdate,
        title = stringResource(R.string.update_required_title),
        message = config.updateMessage.ifBlank { stringResource(R.string.update_required_message) },
        account = null,
    ) {
        if (config.updateUrl.isNotBlank()) {
            GlassPrimaryButton(stringResource(R.string.update_now), onClick = { ExternalApps.openUrl(context, config.updateUrl) }, icon = Icons.Filled.SystemUpdate)
        }
        Spacer(Modifier.height(4.dp))
        CheckAgainButton(vm)
    }
}

@Composable
private fun NotConfiguredScreen(vm: AccessViewModel) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    StatusPage(
        vm = vm,
        icon = Icons.Filled.CloudOff,
        title = stringResource(R.string.setup_missing_title),
        message = stringResource(R.string.setup_missing_message, branding.companyName),
        account = null,
    ) {}
}

/** Soft update prompt and one-time admin notice, shown inside the salon app. */
@Composable
fun AdminMessages() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val config by container.accountManager.appConfig.collectAsStateWithLifecycle()
    val branding by container.accountManager.branding.collectAsStateWithLifecycle()
    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }

    if (!updateDismissed && AccessPolicy.updateAvailable(config, BuildConfig.VERSION_CODE)) {
        AlertDialog(
            onDismissRequest = { updateDismissed = true },
            icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    listOf(stringResource(R.string.update_available_message, config.latestVersionName), config.updateMessage)
                        .filter { it.isNotBlank() }.joinToString("\n\n"),
                )
            },
            confirmButton = {
                Button(onClick = {
                    updateDismissed = true
                    ExternalApps.openUrl(context, config.updateUrl)
                }) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = { TextButton(onClick = { updateDismissed = true }) { Text(stringResource(R.string.update_later)) } },
        )
        return
    }
    val notice = config.notice
    if (!noticeDismissed && notice.isNotBlank() && !container.uiPreferences.isNoticeSeen(notice)) {
        val close = {
            noticeDismissed = true
            container.uiPreferences.markNoticeSeen(notice)
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.status_admin_message, branding.appName)) },
            text = { Text(notice) },
            confirmButton = { Button(onClick = close) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
