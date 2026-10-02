package com.dtpos.salonmanager.services.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class PrinterDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    /** Device reports itself as an imaging/printer device (used only for sorting). */
    val isLikelyPrinter: Boolean,
)

enum class PrinterError {
    BLUETOOTH_UNSUPPORTED,
    BLUETOOTH_OFF,
    PERMISSION_DENIED,
    NO_PRINTER_SELECTED,
    DEVICE_NOT_FOUND,
    CONNECTION_FAILED,
    WRITE_FAILED,
    NOTHING_TO_PRINT,
}

sealed interface PrintResult {
    data object Success : PrintResult
    data class Failure(val error: PrinterError) : PrintResult
}

/**
 * Classic Bluetooth (SPP / RFCOMM) transport for ESC/POS thermal printers.
 *
 * Designed to never crash the app: every Bluetooth call is guarded against missing
 * permissions, disabled adapters, unpaired or switched-off printers and I/O failures, and
 * each problem is reported as a [PrintResult.Failure] the UI can explain.
 */
class BluetoothPrinterService(private val context: Context) {

    private val mutex = Mutex()

    private val adapter: BluetoothAdapter?
        get() = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (e: Exception) {
            null
        }

    val isSupported: Boolean get() = adapter != null

    val isEnabled: Boolean
        get() = try {
            adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }

    /** Runtime permissions needed to talk to already paired printers. */
    fun connectPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    /** Runtime permissions needed to discover new (unpaired) printers. */
    fun scanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(permissions: Array<String>): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<PrinterDevice> {
        val bt = adapter ?: return emptyList()
        if (!hasPermissions(connectPermissions())) return emptyList()
        return try {
            bt.bondedDevices.orEmpty()
                .map { it.toPrinterDevice(bonded = true) }
                .sortedWith(compareByDescending<PrinterDevice> { it.isLikelyPrinter }.thenBy { it.name.lowercase() })
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Scans for nearby devices; emits the growing list and completes when discovery finishes. */
    @SuppressLint("MissingPermission")
    fun discover(): Flow<List<PrinterDevice>> = callbackFlow {
        val bt = adapter
        if (bt == null || !hasPermissions(scanPermissions())) {
            close()
            return@callbackFlow
        }
        val found = LinkedHashMap<String, PrinterDevice>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.bluetoothDevice() ?: return
                        try {
                            found[device.address] = device.toPrinterDevice(device.bondState == BluetoothDevice.BOND_BONDED)
                            trySend(found.values.toList())
                        } catch (e: SecurityException) {
                            // Permission revoked mid-scan: ignore this device.
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> channel.close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        try {
            if (bt.isDiscovering) bt.cancelDiscovery()
            if (!bt.startDiscovery()) channel.close()
        } catch (e: SecurityException) {
            channel.close()
        }
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered.
            }
            try {
                bt.cancelDiscovery()
            } catch (e: SecurityException) {
                // Nothing to clean up without permission.
            }
        }
    }

    /** Starts system pairing. The printer appears in [bondedDevices] once the user confirms. */
    @SuppressLint("MissingPermission")
    fun pair(address: String): Boolean = try {
        if (!hasPermissions(connectPermissions())) false
        else adapter?.getRemoteDevice(address)?.createBond() == true
    } catch (e: Exception) {
        false
    }

    /** Sends raw ESC/POS bytes to the printer, retrying the connection with fallbacks. */
    @SuppressLint("MissingPermission")
    suspend fun send(address: String?, data: ByteArray, copies: Int = 1): PrintResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bt = adapter ?: return@withLock PrintResult.Failure(PrinterError.BLUETOOTH_UNSUPPORTED)
            if (!hasPermissions(connectPermissions())) return@withLock PrintResult.Failure(PrinterError.PERMISSION_DENIED)
            if (!isEnabled) return@withLock PrintResult.Failure(PrinterError.BLUETOOTH_OFF)
            if (address.isNullOrBlank()) return@withLock PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
            if (data.isEmpty()) return@withLock PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)

            val device = try {
                if (!BluetoothAdapter.checkBluetoothAddress(address)) null else bt.getRemoteDevice(address)
            } catch (e: Exception) {
                null
            } ?: return@withLock PrintResult.Failure(PrinterError.DEVICE_NOT_FOUND)

            try {
                bt.cancelDiscovery() // Discovery slows down / breaks RFCOMM connections.
            } catch (e: SecurityException) {
                return@withLock PrintResult.Failure(PrinterError.PERMISSION_DENIED)
            }

            var lastError = PrinterError.CONNECTION_FAILED
            repeat(MAX_ATTEMPTS) { attempt ->
                val socket = try {
                    connect(device)
                } catch (e: SecurityException) {
                    return@withLock PrintResult.Failure(PrinterError.PERMISSION_DENIED)
                } catch (e: IOException) {
                    lastError = PrinterError.CONNECTION_FAILED
                    null
                }
                if (socket != null) {
                    try {
                        repeat(copies.coerceIn(1, 3)) { write(socket, data) }
                        return@withLock PrintResult.Success
                    } catch (e: IOException) {
                        lastError = PrinterError.WRITE_FAILED
                    } finally {
                        closeQuietly(socket)
                    }
                }
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
            PrintResult.Failure(lastError)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val factories: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            // Some low-cost printers only accept a direct channel-1 connection.
            {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        var last: Exception? = null
        for (factory in factories) {
            var socket: BluetoothSocket? = null
            try {
                socket = factory()
                connectWithTimeout(socket)
                return socket
            } catch (e: SecurityException) {
                closeQuietly(socket)
                throw e
            } catch (e: Exception) {
                last = e
                closeQuietly(socket)
            }
        }
        throw IOException("Unable to connect to printer", last)
    }

    @SuppressLint("MissingPermission") // Only reached from send(), which checks the connect permission.
    private fun connectWithTimeout(socket: BluetoothSocket) {
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val task = watchdog.schedule({ if (!socket.isConnected) closeQuietly(socket) }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        try {
            socket.connect()
        } finally {
            task.cancel(false)
            watchdog.shutdownNow()
        }
    }

    private fun write(socket: BluetoothSocket, data: ByteArray) {
        val out = socket.outputStream
        var offset = 0
        while (offset < data.size) {
            val length = minOf(CHUNK_SIZE, data.size - offset)
            out.write(data, offset, length)
            out.flush()
            offset += length
            // Small pause so printers with tiny input buffers do not drop data.
            Thread.sleep(CHUNK_PAUSE_MS)
        }
        // Give the printer time to finish before the socket closes.
        Thread.sleep((data.size / 20L).coerceIn(300L, 2_500L))
    }

    private fun closeQuietly(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Ignore.
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toPrinterDevice(bonded: Boolean): PrinterDevice {
        val cls = try {
            bluetoothClass
        } catch (e: SecurityException) {
            null
        }
        return PrinterDevice(
            name = (try { name } catch (e: SecurityException) { null })?.takeIf { it.isNotBlank() } ?: address,
            address = address,
            isBonded = bonded,
            isLikelyPrinter = cls?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING,
        )
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        /** Standard Serial Port Profile UUID used by virtually all Bluetooth receipt printers. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 800L
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val CHUNK_SIZE = 512
        private const val CHUNK_PAUSE_MS = 20L
    }
}
