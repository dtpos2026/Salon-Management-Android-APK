package com.dtpos.salonmanager.presentation.sales

import com.dtpos.salonmanager.presentation.common.receiptShareText
import androidx.compose.foundation.Image
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.services.export.ReceiptImageFormat
import com.dtpos.salonmanager.services.export.SaveResult
import com.dtpos.salonmanager.services.printer.PrintMode
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.InfoBanner
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.export.ShareHelper
import com.dtpos.salonmanager.services.printer.PrintAlign
import com.dtpos.salonmanager.services.printer.PrintResult
import com.dtpos.salonmanager.services.printer.PrinterError
import com.dtpos.salonmanager.services.printer.ReceiptLayout
import com.dtpos.salonmanager.services.printer.ReceiptTextRenderer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SaleDetailViewModel(
    private val container: AppContainer,
    private val saleId: Long,
    isNewSale: Boolean,
) : BaseViewModel() {

    val receipt: StateFlow<ReceiptData?> = container.saleRepository.observeSale(saleId)
        .map { if (it == null) null else container.saleRepository.buildReceipt(saleId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val customerId: StateFlow<Long?> = container.saleRepository.observeSale(saleId)
        .map { it?.sale?.customerId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _printing = MutableStateFlow(false)
    val printing: StateFlow<Boolean> = _printing.asStateFlow()

    /** Last print failure, shown as a dialog with retry. */
    private val _printError = MutableStateFlow<PrinterError?>(null)
    val printError: StateFlow<PrinterError?> = _printError.asStateFlow()

    val shareFile = MutableSharedFlow<File>(extraBufferCapacity = 1)

    /** Image actions the screen performs with an Activity context. */
    sealed interface ImageAction {
        data class Share(val file: File) : ImageAction
        data class WhatsApp(val file: File, val phone: String?, val message: String) : ImageAction
        data class RequestPermission(val format: ReceiptImageFormat) : ImageAction
    }

    val imageActions = MutableSharedFlow<ImageAction>(extraBufferCapacity = 2)

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /** Styled receipt for the preview (null while rendering). */
    val previewImage: StateFlow<android.graphics.Bitmap?> = receipt
        .map { data -> data?.let { runCatching { container.receiptExporter.render(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val printMode: StateFlow<PrintMode> = container.printerSettingsStore.settingsFlow
        .map { it.mode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintMode.IMAGE)

    private fun export(block: suspend (ReceiptData) -> Unit) {
        if (_exporting.value) return
        _exporting.value = true
        launchSafe {
            try {
                block(receipt.first { it != null } ?: return@launchSafe)
            } finally {
                _exporting.value = false
            }
        }
    }

    fun saveImage(format: ReceiptImageFormat) = export { data ->
        when (val result = container.receiptExporter.saveToGallery(data, format)) {
            is SaveResult.Saved -> {
                container.soundEffects.tap()
                showMessage(R.string.receipt_image_saved, result.fileName)
            }
            SaveResult.NeedsPermission -> imageActions.tryEmit(ImageAction.RequestPermission(format))
            SaveResult.Failed -> showMessage(R.string.receipt_image_failed)
        }
    }

    fun shareImage() = export { data ->
        val file = container.receiptExporter.cacheFile(data)
        if (file == null) showMessage(R.string.receipt_image_failed) else imageActions.tryEmit(ImageAction.Share(file))
    }

    fun sendWhatsApp(message: String) = export { data ->
        val file = container.receiptExporter.cacheFile(data)
        if (file == null) showMessage(R.string.receipt_image_failed) else imageActions.tryEmit(ImageAction.WhatsApp(file, data.customerPhone, message))
    }

    init {
        if (isNewSale) {
            viewModelScope.launch {
                val settings = container.printerSettingsStore.current()
                if (settings.autoPrint && settings.isConfigured) print()
            }
        }
    }

    fun print() {
        if (_printing.value) return
        _printing.value = true
        _printError.value = null
        launchSafe {
            try {
                when (val result = container.receiptPrinter.printSale(saleId)) {
                    PrintResult.Success -> showMessage(R.string.print_success)
                    is PrintResult.Failure -> _printError.value = result.error
                }
            } finally {
                _printing.value = false
            }
        }
    }

    fun dismissPrintError() {
        _printError.value = null
    }

    fun share(labelsProvider: (ReceiptData) -> com.dtpos.salonmanager.services.printer.ReceiptLabels) = launchSafe {
        val data = receipt.first { it != null } ?: return@launchSafe
        val lines = ReceiptLayout.build(data, labelsProvider(data), includeHeader = true)
        val logo = if (data.showLogo) container.logoStore.loadBitmap(data.logoPath) else null
        val file = container.pdfExporter.receiptPdf(lines, "Receipt_${data.receiptNumber}.pdf", logo)
        if (file == null) showMessage(R.string.share_failed) else shareFile.tryEmit(file)
    }

    fun void(reason: String) = launchSafe {
        when (val result = container.saleRepository.voidSale(saleId, reason)) {
            is DataResult.Success -> showMessage(R.string.sale_void_done)
            is DataResult.Failure -> showMessage(result.error.messageRes)
        }
    }
}

@Composable
fun SaleDetailScreen(
    saleId: Long,
    isNewSale: Boolean,
    onBack: () -> Unit,
    onNewSale: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onEdit: (Long) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "sale_$saleId") { SaleDetailViewModel(it, saleId, isNewSale) }
    val receipt by vm.receipt.collectAsStateWithLifecycle()
    val customerId by vm.customerId.collectAsStateWithLifecycle()
    val printing by vm.printing.collectAsStateWithLifecycle()
    val printError by vm.printError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showVoid by remember { mutableStateOf(false) }
    /** WhatsApp choice for this receipt: the message text (and phone) when the dialog is open. */
    var whatsAppChoice by remember { mutableStateOf<Pair<String, String?>?>(null) }
    MessageEffect(vm.messages, snackbar)
    val shareTitle = stringResource(R.string.share_receipt)
    LaunchedEffect(vm) {
        vm.shareFile.collect { ShareHelper.shareFile(context, it, "application/pdf", shareTitle) }
    }
    var pendingFormat by remember { mutableStateOf<ReceiptImageFormat?>(null) }
    val permissionDenied = stringResource(R.string.receipt_permission_denied)
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val format = pendingFormat
        pendingFormat = null
        if (granted && format != null) vm.saveImage(format) else Toast.makeText(context, permissionDenied, Toast.LENGTH_LONG).show()
    }
    val shareImageTitle = stringResource(R.string.receipt_share_image)
    val whatsApp = com.dtpos.salonmanager.presentation.common.rememberWhatsAppLauncher()
    LaunchedEffect(vm) {
        vm.imageActions.collect { action ->
            when (action) {
                is SaleDetailViewModel.ImageAction.Share -> ShareHelper.shareFile(context, action.file, "image/png", shareImageTitle)
                is SaleDetailViewModel.ImageAction.WhatsApp ->
                    whatsApp.image(ShareHelper.uriFor(context, action.file), action.phone, action.message, shareImageTitle)
                is SaleDetailViewModel.ImageAction.RequestPermission -> {
                    pendingFormat = action.format
                    storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
    // A short chime when a new sale is completed (Settings > App preferences > Sound effects).
    var chimed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isNewSale) {
        if (isNewSale && !chimed) {
            chimed = true
            container.soundEffects.success()
        }
    }
    val previewImage by vm.previewImage.collectAsStateWithLifecycle()
    val printMode by vm.printMode.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SalonTopBar(
                title = receipt?.receiptNumber ?: stringResource(R.string.sale_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.share { container.receiptPrinter.labels(it.paymentMethod) } }, enabled = receipt != null) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val data = receipt
        if (data == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isNewSale) {
                InfoBanner(
                    text = stringResource(R.string.sale_saved),
                    icon = Icons.Filled.CheckCircle,
                    container = SalonTheme.extended.positiveContainer,
                    content = SalonTheme.extended.positive,
                )
            }
            if (data.isVoided) {
                InfoBanner(
                    text = stringResource(R.string.sale_is_voided),
                    icon = Icons.Filled.Block,
                    container = SalonTheme.extended.negativeContainer,
                    content = SalonTheme.extended.negative,
                )
            }
            if (printMode == PrintMode.TEXT) {
                ReceiptPreview(data, Modifier.widthIn(max = 420.dp))
            } else {
                StyledReceiptPreview(previewImage, Modifier.widthIn(max = 420.dp))
            }

            Button(
                onClick = vm::print,
                enabled = !printing,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(52.dp),
            ) {
                if (printing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.print_printing))
                } else {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isNewSale) R.string.print_receipt else R.string.print_reprint))
                }
            }
            val whatsappMessage = remember(data) { context.receiptShareText(data, CurrencyFormatter(data.currency)) }
            ReceiptImageActions(
                exporting = exporting,
                onPng = { vm.saveImage(ReceiptImageFormat.PNG) },
                onJpeg = { vm.saveImage(ReceiptImageFormat.JPEG) },
                onShare = vm::shareImage,
                onWhatsApp = { whatsAppChoice = whatsappMessage to data.customerPhone },
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onNewSale, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dashboard_new_sale))
                }
                customerId?.let { id ->
                    OutlinedButton(onClick = { onOpenCustomer(id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sale_open_customer))
                    }
                }
            }
            if (!data.isVoided) {
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onEdit(saleId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sale_edit))
                    }
                    TextButton(onClick = { showVoid = true }) {
                        Text(stringResource(R.string.sale_void), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    printError?.let { error ->
        AlertDialog(
            onDismissRequest = vm::dismissPrintError,
            icon = { Icon(Icons.Filled.Print, contentDescription = null) },
            title = { Text(stringResource(R.string.print_failed_title)) },
            text = { Text(stringResource(error.messageRes)) },
            confirmButton = { Button(onClick = vm::print) { Text(stringResource(R.string.action_retry)) } },
            dismissButton = { TextButton(onClick = vm::dismissPrintError) { Text(stringResource(R.string.action_close)) } },
        )
    }

    whatsAppChoice?.let { (message, phone) ->
        val number = com.dtpos.salonmanager.core.util.PhoneNumbers.toWhatsApp(phone)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { whatsAppChoice = null },
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = com.dtpos.salonmanager.presentation.messages.WhatsAppGreen) },
            title = { Text(stringResource(R.string.receipt_wa_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            whatsAppChoice = null
                            whatsApp.text(phone, message, shareImageTitle)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.dtpos.salonmanager.presentation.messages.WhatsAppGreen, contentColor = androidx.compose.ui.graphics.Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (number != null) R.string.receipt_wa_text_customer else R.string.receipt_wa_text_choose)) }
                    OutlinedButton(
                        onClick = {
                            whatsAppChoice = null
                            vm.sendWhatsApp(message)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.receipt_wa_picture)) }
                    Text(
                        stringResource(if (number != null) R.string.receipt_wa_hint else R.string.receipt_wa_hint_no_phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { whatsAppChoice = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showVoid) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text(stringResource(R.string.sale_void_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sale_void_message))
                    Spacer(Modifier.height(12.dp))
                    FormTextField(reason, { reason = it }, stringResource(R.string.sale_void_reason))
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.void(reason)
                    showVoid = false
                }) { Text(stringResource(R.string.sale_void_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showVoid = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** On-screen receipt that mirrors the 58 mm printout exactly (same layout engine). */
@Composable
fun ReceiptPreview(receipt: ReceiptData, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val labels = remember(receipt.paymentMethod) { container.receiptPrinter.labels(receipt.paymentMethod) }
    val lines = remember(receipt, labels) {
        ReceiptTextRenderer.render(ReceiptLayout.build(receipt, labels), ReceiptTextRenderer.CHARS_58MM)
    }
    val logo = remember(receipt.logoPath, receipt.showLogo) {
        if (receipt.showLogo) container.logoStore.loadBitmap(receipt.logoPath, 256)?.asImageBitmap() else null
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            logo?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(8.dp))
            }
            lines.forEach { line ->
                Text(
                    text = line.text.trimEnd().ifEmpty { " " },
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (line.large) 17.sp else 11.sp,
                    fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                    color = Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (line.align == PrintAlign.CENTER) androidx.compose.ui.text.style.TextAlign.Center else null,
                )
            }
        }
    }
}

/** The branded receipt image exactly as it is saved, shared and printed in image mode. */
@Composable
fun StyledReceiptPreview(image: android.graphics.Bitmap?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        if (image == null) {
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            val bitmap = remember(image) { image.asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReceiptImageActions(
    exporting: Boolean,
    onPng: () -> Unit,
    onJpeg: () -> Unit,
    onShare: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPng, enabled = !exporting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.receipt_save_png), maxLines = 1)
            }
            FilledTonalButton(onClick = onJpeg, enabled = !exporting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.receipt_save_jpeg), maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onShare, enabled = !exporting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_share), maxLines = 1)
            }
            Button(
                onClick = onWhatsApp,
                enabled = !exporting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA855), contentColor = Color.White),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.support_whatsapp), maxLines = 1)
            }
        }
    }
}
