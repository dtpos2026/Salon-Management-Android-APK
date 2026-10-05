package com.dtpos.salonmanager.presentation.settings

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Lan
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.services.printer.LanPrinterService
import com.dtpos.salonmanager.services.printer.PrinterConnection
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.InfoBanner
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.components.SegmentedChoice
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.printer.PaperWidth
import com.dtpos.salonmanager.services.printer.PrintMode
import com.dtpos.salonmanager.services.printer.ReceiptStyle
import com.dtpos.salonmanager.services.printer.PrintResult
import com.dtpos.salonmanager.services.printer.PrinterDevice
import com.dtpos.salonmanager.services.printer.PrinterSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BluetoothStatus(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val hasConnectPermission: Boolean = false,
    val hasScanPermission: Boolean = false,
    val paired: List<PrinterDevice> = emptyList(),
    val discovered: List<PrinterDevice> = emptyList(),
    val scanning: Boolean = false,
    val testing: Boolean = false,
)

class PrinterSettingsViewModel(private val container: AppContainer) : BaseViewModel() {
    private val bt = container.bluetoothPrinterService
    private val store = container.printerSettingsStore

    val settings: StateFlow<PrinterSettings> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrinterSettings())

    private val _status = MutableStateFlow(BluetoothStatus())
    val status: StateFlow<BluetoothStatus> = _status.asStateFlow()
    private var scanJob: Job? = null

    val connectPermissions: Array<String> get() = bt.connectPermissions()
    val scanPermissions: Array<String> get() = bt.scanPermissions()

    fun refresh() {
        _status.update {
            it.copy(
                supported = bt.isSupported,
                enabled = bt.isEnabled,
                hasConnectPermission = bt.hasPermissions(bt.connectPermissions()),
                hasScanPermission = bt.hasPermissions(bt.scanPermissions()),
                paired = bt.bondedDevices(),
            )
        }
    }

    fun startScan() {
        if (!bt.hasPermissions(bt.scanPermissions())) return
        scanJob?.cancel()
        _status.update { it.copy(scanning = true, discovered = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                bt.discover().collect { found -> _status.update { it.copy(discovered = found.filterNot { d -> d.isBonded }) } }
            } finally {
                _status.update { it.copy(scanning = false) }
                refresh()
            }
        }
    }

    fun pair(device: PrinterDevice) {
        if (!bt.pair(device.address)) showMessage(R.string.printer_pair_failed) else showMessage(R.string.printer_pair_started)
    }

    fun select(device: PrinterDevice) = launchSafe {
        store.savePrinter(device.address, device.name)
        store.save(settings.value.copy(connection = PrinterConnection.BLUETOOTH))
        showMessage(R.string.printer_saved)
    }

    fun forget() = launchSafe { store.clearPrinter() }

    /** Network printer: saves IP / port and makes it the active printer. */
    fun saveLan(host: String, port: String) = launchSafe {
        val h = host.trim()
        val p = port.trim().toIntOrNull()
        if (!LanPrinterService.isValidHost(h) || p == null || p !in 1..65535) {
            showMessage(R.string.printer_lan_invalid)
            return@launchSafe
        }
        store.save(settings.value.copy(connection = PrinterConnection.LAN, lanHost = h, lanPort = p))
        showMessage(R.string.printer_saved)
    }

    fun useConnection(connection: PrinterConnection) = updateOptions { it.copy(connection = connection) }

    /** Opens a connection to the network printer; prints nothing. */
    fun checkLan(host: String, port: String) {
        val h = host.trim()
        val p = port.trim().toIntOrNull()
        if (!LanPrinterService.isValidHost(h) || p == null || p !in 1..65535) {
            showMessage(R.string.printer_lan_invalid)
            return
        }
        if (_status.value.testing) return
        _status.update { it.copy(testing = true) }
        launchSafe {
            try {
                when (container.receiptPrinter.checkLan(h, p)) {
                    PrintResult.Success -> showMessage(R.string.printer_lan_reachable)
                    is PrintResult.Failure -> showMessage(R.string.printer_lan_unreachable)
                }
            } finally {
                _status.update { it.copy(testing = false) }
            }
        }
    }

    fun updateOptions(transform: (PrinterSettings) -> PrinterSettings) = launchSafe { store.save(transform(settings.value)) }

    fun testPrint(device: PrinterDevice? = null) {
        if (_status.value.testing) return
        _status.update { it.copy(testing = true) }
        launchSafe {
            try {
                when (val result = container.receiptPrinter.printTest(device?.address)) {
                    PrintResult.Success -> showMessage(R.string.printer_test_sent)
                    is PrintResult.Failure -> showMessage(result.error.messageRes)
                }
            } finally {
                _status.update { it.copy(testing = false) }
            }
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { PrinterSettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)

    // Re-check Bluetooth state and permissions whenever the screen resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val scanPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.refresh()
        if (result.values.all { it }) vm.startScan()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.settings_printer), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ContentCard {
                    Text(stringResource(R.string.printer_selected), style = MaterialTheme.typography.labelMedium)
                    if (settings.isConfigured) {
                        val lan = settings.connection == PrinterConnection.LAN
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (lan) Icons.Filled.Lan else Icons.Filled.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (lan) stringResource(R.string.printer_lan_name) else settings.name ?: settings.address.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(if (lan) "${settings.lanHost}:${settings.lanPort}" else settings.address.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.testPrint() }, enabled = !status.testing) {
                                if (status.testing) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text(stringResource(R.string.printer_test))
                                }
                            }
                            TextButton(onClick = vm::forget) { Text(stringResource(R.string.printer_forget)) }
                        }
                    } else {
                        Text(stringResource(R.string.printer_none_selected), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.printer_setup_steps), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                ContentCard {
                    Text(stringResource(R.string.printer_connection), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoice(
                        options = PrinterConnection.entries.toList(),
                        selected = settings.connection,
                        label = { stringResource(if (it == PrinterConnection.LAN) R.string.printer_connection_lan else R.string.printer_connection_bluetooth) },
                        onSelect = vm::useConnection,
                    )
                }
            }

            if (settings.connection == PrinterConnection.LAN) {
                item { LanPrinterCard(settings, status.testing, vm) }
            }

            if (settings.connection == PrinterConnection.BLUETOOTH) item {
                when {
                    !status.supported -> InfoBanner(
                        stringResource(R.string.printer_error_unsupported),
                        Icons.Filled.BluetoothDisabled,
                        SalonTheme.extended.negativeContainer,
                        SalonTheme.extended.negative,
                    )
                    !status.hasConnectPermission -> InfoBanner(
                        stringResource(R.string.printer_permission_needed),
                        Icons.Filled.Bluetooth,
                        SalonTheme.extended.warningContainer,
                        SalonTheme.extended.warning,
                        actionLabel = stringResource(R.string.action_allow),
                        onAction = { permissionLauncher.launch(vm.connectPermissions) },
                    )
                    !status.enabled -> InfoBanner(
                        stringResource(R.string.printer_error_off),
                        Icons.Filled.BluetoothDisabled,
                        SalonTheme.extended.warningContainer,
                        SalonTheme.extended.warning,
                        actionLabel = stringResource(R.string.action_turn_on),
                        onAction = {
                            try {
                                enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } catch (e: Exception) {
                                // Some devices block the system dialog; the user can enable Bluetooth manually.
                            }
                        },
                    )
                    else -> Unit
                }
            }

            if (settings.connection == PrinterConnection.BLUETOOTH && status.supported && status.hasConnectPermission) {
                item { SectionHeader(stringResource(R.string.printer_paired)) }
                if (status.paired.isEmpty()) {
                    item { Text(stringResource(R.string.printer_no_paired), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(status.paired, key = { "p_${it.address}" }) { device ->
                    DeviceRow(device, selected = device.address == settings.address, onSelect = { vm.select(device) }, onTest = { vm.testPrint(device) })
                }
                item {
                    SectionHeader(stringResource(R.string.printer_nearby)) {
                        if (status.scanning) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = { if (status.hasScanPermission) vm.startScan() else scanPermissionLauncher.launch(vm.scanPermissions) },
                                enabled = status.enabled,
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.printer_scan))
                            }
                        }
                    }
                }
                items(status.discovered, key = { "d_${it.address}" }) { device ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
                            headlineContent = { Text(device.name) },
                            supportingContent = { Text(device.address) },
                            trailingContent = { OutlinedButton(onClick = { vm.pair(device) }) { Text(stringResource(R.string.printer_pair)) } },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.printer_options)) }
            item {
                ContentCard {
                    Text(stringResource(R.string.printer_paper), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoice(
                        options = PaperWidth.entries.toList(),
                        selected = settings.paper,
                        label = { if (it == PaperWidth.MM58) "58 mm" else "80 mm" },
                        onSelect = { p -> vm.updateOptions { it.copy(paper = p) } },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.receipt_style), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReceiptStyle.entries.forEach { style ->
                            FilterChip(
                                selected = settings.receiptStyle == style,
                                onClick = { vm.updateOptions { it.copy(receiptStyle = style) } },
                                label = { Text(stringResource(receiptStyleName(style))) },
                            )
                        }
                    }
                    Text(stringResource(R.string.receipt_style_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.printer_mode), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoice(
                        options = PrintMode.entries.toList(),
                        selected = settings.mode,
                        label = { stringResource(if (it == PrintMode.TEXT) R.string.printer_mode_text else R.string.printer_mode_image) },
                        onSelect = { m -> vm.updateOptions { it.copy(mode = m) } },
                    )
                    Text(stringResource(R.string.printer_mode_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(stringResource(R.string.printer_auto_print), settings.autoPrint, stringResource(R.string.printer_auto_print_hint)) { v ->
                        vm.updateOptions { it.copy(autoPrint = v) }
                    }
                    ToggleRow(stringResource(R.string.printer_print_logo), settings.printLogo) { v -> vm.updateOptions { it.copy(printLogo = v) } }
                    ToggleRow(stringResource(R.string.printer_cut), settings.cut, stringResource(R.string.printer_cut_hint)) { v ->
                        vm.updateOptions { it.copy(cut = v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.printer_copies), style = MaterialTheme.typography.titleSmall)
                    SegmentedChoice(listOf(1, 2, 3), settings.copies, { it.toString() }, { c -> vm.updateOptions { it.copy(copies = c) } })
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.printer_feed), style = MaterialTheme.typography.titleSmall)
                    SegmentedChoice(listOf(1, 3, 5, 8), settings.feedLines, { it.toString() }, { f -> vm.updateOptions { it.copy(feedLines = f) } })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanPrinterCard(settings: PrinterSettings, testing: Boolean, vm: PrinterSettingsViewModel) {
    var host by rememberSaveable(settings.lanHost) { mutableStateOf(settings.lanHost.orEmpty()) }
    var port by rememberSaveable(settings.lanPort) { mutableStateOf(settings.lanPort.toString()) }
    ContentCard {
        Text(stringResource(R.string.printer_lan_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.printer_lan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FormTextField(host, { host = it.trim() }, stringResource(R.string.printer_lan_ip), keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None)
        Spacer(Modifier.height(6.dp))
        FormTextField(port, { v -> port = v.filter(Char::isDigit).take(5) }, stringResource(R.string.printer_lan_port), keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveLan(host, port) }) { Text(stringResource(R.string.printer_lan_use)) }
            OutlinedButton(onClick = { vm.checkLan(host, port) }, enabled = !testing) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.printer_lan_check))
            }
        }
    }
}

@Composable
private fun DeviceRow(device: PrinterDevice, selected: Boolean, onSelect: () -> Unit, onTest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else if (device.isLikelyPrinter) Icons.Filled.Print else Icons.Filled.Bluetooth,
                    contentDescription = null,
                )
            },
            headlineContent = { Text(device.name) },
            supportingContent = { Text(device.address) },
            trailingContent = { TextButton(onClick = onTest) { Text(stringResource(R.string.printer_test_short)) } },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

fun receiptStyleName(style: ReceiptStyle): Int = when (style) {
    ReceiptStyle.CLASSIC -> R.string.receipt_style_classic
    ReceiptStyle.MODERN -> R.string.receipt_style_modern
    ReceiptStyle.MINIMAL -> R.string.receipt_style_minimal
    ReceiptStyle.ELEGANT -> R.string.receipt_style_elegant
}
