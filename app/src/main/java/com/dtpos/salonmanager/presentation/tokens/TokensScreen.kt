package com.dtpos.salonmanager.presentation.tokens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.dtpos.salonmanager.presentation.common.showWhatsAppResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.BookingEntity
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.domain.model.BookingStatus
import com.dtpos.salonmanager.domain.model.BusinessProfile
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.DateField
import com.dtpos.salonmanager.presentation.components.DropdownField
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.messages.WhatsAppGreen
import com.dtpos.salonmanager.services.export.ExternalApps
import com.dtpos.salonmanager.services.export.ShareHelper
import com.dtpos.salonmanager.services.export.TokenImage
import com.dtpos.salonmanager.services.printer.PrintResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** "4:30 PM" style text for minutes after midnight. */
fun formatSlot(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

@OptIn(ExperimentalCoroutinesApi::class)
class TokensViewModel(private val container: AppContainer) : BaseViewModel() {
    private val repo = container.bookingRepository
    private val settings = container.settingsRepository

    val enabled: StateFlow<Boolean> = settings.observeBoolean(SettingKeys.TOKENS_ENABLED)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val date = MutableStateFlow(DateTimeUtils.today())
    val queue: StateFlow<List<BookingEntity>> = date.flatMapLatest { repo.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile: StateFlow<BusinessProfile?> = container.businessRepository.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Print preview: the exact black-and-white slip the printer will print. */
    val preview = MutableStateFlow<TokenPreview?>(null)
    val printing = MutableStateFlow(false)

    fun setEnabled(value: Boolean) = launchSafe { settings.putBoolean(SettingKeys.TOKENS_ENABLED, value) }

    fun shiftDay(days: Long) { date.value = date.value.plusDays(days) }

    fun issue(day: LocalDate, name: String, phone: String, service: String, timeMinutes: Int?, onDone: () -> Unit) = launchSafe {
        when (val r = container.bookingRepository.issue(day, name, phone, service, timeMinutes)) {
            is DataResult.Success -> {
                date.value = day
                container.soundEffects.success()
                onDone()
                openPreview(r.data, turn = false)
            }
            is DataResult.Failure -> showMessage(r.error.messageRes)
        }
    }

    fun callNext() = launchSafe {
        val next = repo.callNext(queue.value)
        if (next == null) showMessage(R.string.tokens_none_waiting) else {
            container.soundEffects.tap()
            showMessage(R.string.tokens_now_serving_msg, next.tokenNumber)
        }
    }

    fun setStatus(booking: BookingEntity, status: BookingStatus) = launchSafe { repo.setStatus(booking.id, status) }

    /** Renders the slip at the printer's paper width and shows it before printing or sending. */
    fun openPreview(booking: BookingEntity, turn: Boolean) = launchSafe {
        val slip = container.receiptPrinter.tokenSlip(booking, profile.value?.name.orEmpty())
        preview.value = TokenPreview(booking, slip, turn)
    }

    fun closePreview() {
        preview.value = null
    }

    fun printPreview() {
        val current = preview.value ?: return
        if (printing.value) return
        printing.value = true
        launchSafe {
            try {
                when (val result = container.receiptPrinter.printTokenSlip(current.slip)) {
                    is PrintResult.Success -> showMessage(R.string.tokens_printed)
                    is PrintResult.Failure -> showMessage(result.error.messageRes)
                }
            } finally {
                printing.value = false
            }
        }
    }
}

/** A token slip ready to print or send; [turn] picks the "your turn" message instead of the confirmation. */
class TokenPreview(val booking: BookingEntity, val slip: android.graphics.Bitmap, val turn: Boolean)

@Composable
fun TokensScreen(onBack: () -> Unit) {
    val vm = appViewModel { TokensViewModel(it) }
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val printing by vm.printing.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf<Boolean?>(null) } // false = walk-in, true = booking
    val context = LocalContext.current
    MessageEffect(vm.messages, snackbar)

    val accent = MaterialTheme.colorScheme.primary.toArgb()

    Scaffold(topBar = { SalonTopBar(stringResource(R.string.tokens_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tokens_switch), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.tokens_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = vm::setEnabled)
                    }
                }
            }
            if (!enabled) return@LazyColumn
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) }
                    Text(
                        if (date == DateTimeUtils.today()) stringResource(R.string.tokens_today) else DateTimeUtils.formatDate(date),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                }
            }
            item {
                val serving = queue.firstOrNull { it.status == BookingStatus.SERVING }
                val waiting = queue.count { it.status == BookingStatus.WAITING }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.tokens_now_serving), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(serving?.let { "#${it.tokenNumber}" } ?: "—", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        serving?.let { Text(it.customerName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Text(stringResource(R.string.tokens_waiting_count, waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.size(10.dp))
                        Button(onClick = vm::callNext, enabled = waiting > 0 || serving != null) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.tokens_call_next))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { creating = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tokens_new))
                    }
                    OutlinedButton(onClick = { creating = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tokens_book))
                    }
                }
            }
            if (queue.isEmpty()) {
                item { EmptyState(Icons.Filled.ConfirmationNumber, stringResource(R.string.tokens_empty_title), stringResource(R.string.tokens_empty_message)) }
            }
            items(queue, key = { it.id }) { b ->
                TokenRow(
                    b,
                    onWhatsApp = { vm.openPreview(b, turn = b.status == BookingStatus.SERVING || (b.timeMinutes == null && b.status == BookingStatus.WAITING)) },
                    onPrint = { vm.openPreview(b, turn = false) },
                    onDone = { vm.setStatus(b, BookingStatus.DONE) },
                    onCancel = { vm.setStatus(b, BookingStatus.CANCELLED) },
                    onServe = { vm.setStatus(b, BookingStatus.SERVING) },
                )
            }
        }
    }

    creating?.let { booking ->
        NewTokenDialog(
            booking = booking,
            initialDate = if (booking) date.plusDays(if (date == DateTimeUtils.today()) 1 else 0) else DateTimeUtils.today(),
            onDismiss = { creating = null },
            onSave = { day, name, phone, service, time -> vm.issue(day, name, phone, service, time) { creating = null } },
        )
    }
    preview?.let { p ->
        TokenPreviewDialog(
            preview = p,
            salon = profile?.name.orEmpty(),
            printing = printing,
            accent = accent,
            onPrint = vm::printPreview,
            onDismiss = vm::closePreview,
        )
    }
}

/** Message for the customer, built from the real booking. */
fun tokenMessage(context: android.content.Context, b: BookingEntity, salon: String, turn: Boolean): String =
    if (turn) {
        context.getString(R.string.tokens_wa_turn, b.customerName, b.tokenNumber, salon)
    } else {
        context.getString(
            R.string.tokens_wa_confirm, b.customerName, b.tokenNumber,
            DateTimeUtils.formatDate(LocalDate.ofEpochDay(b.dateEpochDay)) + (b.timeMinutes?.let { ", " + formatSlot(it) } ?: "") +
                (b.service?.takeIf { it.isNotBlank() }?.let { " (" + it + ")" } ?: ""),
            salon,
        )
    }

@Composable
private fun TokenPreviewDialog(
    preview: TokenPreview,
    salon: String,
    printing: Boolean,
    accent: Int,
    onPrint: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val b = preview.booking
    val message = remember(b.id, preview.turn) { tokenMessage(context, b, salon, preview.turn) }
    val chooser = stringResource(R.string.tokens_send_title)

    // Colour picture for WhatsApp / share (the printer gets the black-and-white slip shown above).
    fun pictureUri(): android.net.Uri? {
        val whenText = DateTimeUtils.formatDate(LocalDate.ofEpochDay(b.dateEpochDay)) + (b.timeMinutes?.let { "  ·  " + formatSlot(it) } ?: "")
        val image = TokenImage.render(
            salon = salon,
            title = context.getString(R.string.tokens_slip_title),
            token = b.tokenNumber,
            name = b.customerName,
            whenText = whenText,
            service = b.service,
            footer = context.getString(R.string.tokens_slip_wait),
            accent = accent,
        )
        return TokenImage.cacheFile(context, image, b.tokenNumber)?.let { ShareHelper.uriFor(context, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tokens_preview_title, b.tokenNumber)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.tokens_preview_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview.slip.asImageBitmap(),
                        contentDescription = stringResource(R.string.tokens_preview_title, b.tokenNumber),
                        modifier = Modifier.fillMaxWidth(0.8f),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                Button(onClick = onPrint, enabled = !printing, modifier = Modifier.fillMaxWidth()) {
                    if (printing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tokens_print))
                    }
                }
                Text(stringResource(R.string.tokens_send_title), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { context.showWhatsAppResult(ExternalApps.whatsAppText(context, b.customerPhone, message, chooser)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.tokens_send_wa_text), color = WhatsAppGreen) }
                    OutlinedButton(
                        onClick = {
                            val uri = pictureUri()
                            val result = if (uri != null) ExternalApps.whatsAppImage(context, uri, "image/png", b.customerPhone, message, chooser)
                            else ExternalApps.whatsAppText(context, b.customerPhone, message, chooser)
                            context.showWhatsAppResult(result)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.tokens_send_wa_image), color = WhatsAppGreen) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (!ExternalApps.sms(context, b.customerPhone, message)) context.showWhatsAppResult(com.dtpos.salonmanager.services.export.WhatsAppResult.FAILED) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.tokens_send_sms)) }
                    OutlinedButton(
                        onClick = {
                            val uri = pictureUri()
                            val ok = if (uri != null) ExternalApps.shareImage(context, uri, "image/png", message, chooser) else ExternalApps.shareText(context, message, chooser)
                            if (!ok) context.showWhatsAppResult(com.dtpos.salonmanager.services.export.WhatsAppResult.FAILED)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.tokens_send_share)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun TokenRow(b: BookingEntity, onWhatsApp: () -> Unit, onPrint: () -> Unit, onDone: () -> Unit, onCancel: () -> Unit, onServe: () -> Unit) {
    val active = b.status == BookingStatus.WAITING || b.status == BookingStatus.SERVING
    Card(
        colors = CardDefaults.cardColors(containerColor = if (b.status == BookingStatus.SERVING) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("#${b.tokenNumber}", color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        b.timeMinutes?.let { formatSlot(it) } ?: stringResource(R.string.tokens_walk_in),
                        b.service,
                        stringResource(statusLabel(b.status)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!b.customerPhone.isNullOrBlank()) {
                IconButton(onClick = onWhatsApp) { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", tint = WhatsAppGreen) }
            }
            IconButton(onClick = onPrint) { Icon(Icons.Filled.Print, contentDescription = stringResource(R.string.tokens_print)) }
            when (b.status) {
                BookingStatus.WAITING -> {
                    IconButton(onClick = onServe) { Icon(Icons.Filled.Campaign, contentDescription = stringResource(R.string.tokens_serve)) }
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tokens_cancel)) }
                }
                BookingStatus.SERVING -> IconButton(onClick = onDone) { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.tokens_done)) }
                else -> Unit
            }
        }
    }
}

private fun statusLabel(status: BookingStatus): Int = when (status) {
    BookingStatus.WAITING -> R.string.tokens_status_waiting
    BookingStatus.SERVING -> R.string.tokens_status_serving
    BookingStatus.DONE -> R.string.tokens_status_done
    BookingStatus.CANCELLED -> R.string.tokens_status_cancelled
}

private val SLOTS: List<Int> = (8 * 60 until 24 * 60 step 15).toList()

@Composable
private fun NewTokenDialog(
    booking: Boolean,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, String, Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(initialDate) }
    var slot by remember { mutableStateOf(17 * 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (booking) R.string.tokens_book else R.string.tokens_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(name, { name = it }, stringResource(R.string.tokens_name), capitalization = KeyboardCapitalization.Words)
                FormTextField(phone, { phone = it }, stringResource(R.string.tokens_phone), keyboardType = KeyboardType.Phone)
                FormTextField(service, { service = it }, stringResource(R.string.tokens_service))
                if (booking) {
                    DateField(stringResource(R.string.tokens_date), day, { it?.let { d -> day = d } })
                    DropdownField(stringResource(R.string.tokens_time), SLOTS, slot, { formatSlot(it) }, { slot = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(if (booking) day else DateTimeUtils.today(), name, phone, service, if (booking) slot else null) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
