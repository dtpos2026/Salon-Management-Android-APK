package com.dtpos.salonmanager.presentation.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.services.printer.PrinterConnection

/**
 * Wraps a print action so Android's Bluetooth question ("Nearby devices", Android 12+) comes
 * first when the app does not have it yet, right where the owner tapped Print, instead of an
 * error that sends them to Settings. After the answer the print runs; if the owner said no, the
 * print reports that itself. Network (LAN) printers and older Android need no question.
 */
@Composable
fun rememberPrintWithPermission(): (() -> Unit) -> Unit {
    val container = LocalAppContainer.current
    val bt = container.bluetoothPrinterService
    val settings by container.printerSettingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending.value
        pending.value = null
        action?.invoke()
    }
    return { action ->
        val bluetooth = settings?.connection != PrinterConnection.LAN
        if (!bluetooth || bt.hasPermissions(bt.connectPermissions())) {
            action()
        } else {
            pending.value = action
            launcher.launch(bt.printPermissions())
        }
    }
}
