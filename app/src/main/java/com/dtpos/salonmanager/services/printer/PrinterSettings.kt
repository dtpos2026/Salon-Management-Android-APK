package com.dtpos.salonmanager.services.printer

import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class PaperWidth(val chars: Int, val dots: Int) {
    MM58(ReceiptTextRenderer.CHARS_58MM, MonochromeImage.DOTS_58MM),
    MM80(ReceiptTextRenderer.CHARS_80MM, MonochromeImage.DOTS_80MM),
}

/**
 * TEXT: fast, sharp printing with the printer's own font (Latin characters).
 * IMAGE: the receipt is rendered with Android fonts and sent as a bitmap, so any language
 * (e.g. Urdu names) prints correctly on any ESC/POS printer that supports raster images.
 */
enum class PrintMode { TEXT, IMAGE }

/** Which printer prints: a Bluetooth printer (paired) or a network printer (IP and port). */
enum class PrinterConnection { BLUETOOTH, LAN }

data class PrinterSettings(
    val address: String? = null,
    val name: String? = null,
    val paper: PaperWidth = PaperWidth.MM58,
    val mode: PrintMode = PrintMode.IMAGE,
    val printLogo: Boolean = true,
    val autoPrint: Boolean = false,
    val copies: Int = 1,
    val feedLines: Int = 3,
    val cut: Boolean = true,
    val receiptStyle: ReceiptStyle = ReceiptStyle.CLASSIC,
    val connection: PrinterConnection = PrinterConnection.BLUETOOTH,
    val lanHost: String? = null,
    val lanPort: Int = LanPrinterService.DEFAULT_PORT,
) {
    val isConfigured: Boolean
        get() = when (connection) {
            PrinterConnection.BLUETOOTH -> !address.isNullOrBlank()
            PrinterConnection.LAN -> !lanHost.isNullOrBlank()
        }
}

class PrinterSettingsStore(private val settings: SettingsRepository) {

    val settingsFlow: Flow<PrinterSettings> = settings.observeAll().map(::parse).distinctUntilChanged()

    suspend fun current(): PrinterSettings = settingsFlow.first()

    suspend fun savePrinter(address: String, name: String?) {
        settings.putString(SettingKeys.PRINTER_ADDRESS, address)
        settings.putString(SettingKeys.PRINTER_NAME, name)
    }

    suspend fun clearPrinter() {
        settings.putString(SettingKeys.PRINTER_ADDRESS, null)
        settings.putString(SettingKeys.PRINTER_NAME, null)
    }

    suspend fun save(value: PrinterSettings) {
        settings.putString(SettingKeys.PRINTER_PAPER, value.paper.name)
        settings.putString(SettingKeys.PRINTER_MODE, value.mode.name)
        settings.putBoolean(SettingKeys.PRINTER_LOGO, value.printLogo)
        settings.putBoolean(SettingKeys.PRINTER_AUTO, value.autoPrint)
        settings.putInt(SettingKeys.PRINTER_COPIES, value.copies.coerceIn(1, 3))
        settings.putInt(SettingKeys.PRINTER_FEED, value.feedLines.coerceIn(0, 8))
        settings.putBoolean(SettingKeys.PRINTER_CUT, value.cut)
        settings.putString(SettingKeys.PRINTER_STYLE, value.receiptStyle.name)
        settings.putString(SettingKeys.PRINTER_CONNECTION, value.connection.name)
        settings.putString(SettingKeys.PRINTER_LAN_HOST, value.lanHost?.trim()?.ifEmpty { null })
        settings.putInt(SettingKeys.PRINTER_LAN_PORT, value.lanPort.coerceIn(1, 65535))
    }

    private fun parse(map: Map<String, String>) = PrinterSettings(
        address = map[SettingKeys.PRINTER_ADDRESS]?.takeIf { it.isNotBlank() },
        name = map[SettingKeys.PRINTER_NAME],
        paper = map[SettingKeys.PRINTER_PAPER]?.let { v -> PaperWidth.entries.firstOrNull { it.name == v } } ?: PaperWidth.MM58,
        mode = map[SettingKeys.PRINTER_MODE]?.let { v -> PrintMode.entries.firstOrNull { it.name == v } } ?: PrintMode.IMAGE,
        printLogo = map[SettingKeys.PRINTER_LOGO]?.toBooleanStrictOrNull() ?: true,
        autoPrint = map[SettingKeys.PRINTER_AUTO]?.toBooleanStrictOrNull() ?: false,
        copies = map[SettingKeys.PRINTER_COPIES]?.toIntOrNull()?.coerceIn(1, 3) ?: 1,
        feedLines = map[SettingKeys.PRINTER_FEED]?.toIntOrNull()?.coerceIn(0, 8) ?: 3,
        cut = map[SettingKeys.PRINTER_CUT]?.toBooleanStrictOrNull() ?: true,
        receiptStyle = map[SettingKeys.PRINTER_STYLE]?.let { v -> ReceiptStyle.entries.firstOrNull { it.name == v } } ?: ReceiptStyle.CLASSIC,
        connection = map[SettingKeys.PRINTER_CONNECTION]?.let { v -> PrinterConnection.entries.firstOrNull { it.name == v } } ?: PrinterConnection.BLUETOOTH,
        lanHost = map[SettingKeys.PRINTER_LAN_HOST]?.takeIf { it.isNotBlank() },
        lanPort = map[SettingKeys.PRINTER_LAN_PORT]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: LanPrinterService.DEFAULT_PORT,
    )
}
