package com.dtpos.salonmanager.services.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Network (LAN / Wi-Fi) ESC/POS printers: raw TCP, port 9100 on almost every model.
 * Same bytes as Bluetooth; every failure becomes a [PrintResult.Failure].
 */
class LanPrinterService {

    private val mutex = Mutex()

    suspend fun send(host: String?, port: Int, data: ByteArray, copies: Int = 1): PrintResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val address = host?.trim().orEmpty()
            if (!isValidHost(address)) return@withLock PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
            if (port !in 1..65535) return@withLock PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
            if (data.isEmpty()) return@withLock PrintResult.Failure(PrinterError.NOTHING_TO_PRINT)
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = WRITE_TIMEOUT_MS
                    val out = socket.getOutputStream()
                    repeat(copies.coerceIn(1, 3)) {
                        data.asList().chunked(CHUNK).forEach { chunk -> out.write(chunk.toByteArray()) }
                    }
                    out.flush()
                }
                PrintResult.Success
            } catch (e: UnknownHostException) {
                PrintResult.Failure(PrinterError.DEVICE_NOT_FOUND)
            } catch (e: SocketTimeoutException) {
                PrintResult.Failure(PrinterError.CONNECTION_FAILED)
            } catch (e: IOException) {
                PrintResult.Failure(PrinterError.CONNECTION_FAILED)
            } catch (e: SecurityException) {
                PrintResult.Failure(PrinterError.PERMISSION_DENIED)
            }
        }
    }

    /** Opens and closes a connection: tells the owner whether the printer answers on the network. */
    suspend fun check(host: String?, port: Int): PrintResult = withContext(Dispatchers.IO) {
        val address = host?.trim().orEmpty()
        if (!isValidHost(address) || port !in 1..65535) return@withContext PrintResult.Failure(PrinterError.NO_PRINTER_SELECTED)
        try {
            Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS) }
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Failure(PrinterError.CONNECTION_FAILED)
        }
    }

    companion object {
        const val DEFAULT_PORT = 9100
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val WRITE_TIMEOUT_MS = 10_000
        private const val CHUNK = 4096

        private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
        private val HOSTNAME = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,62})(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,62}))*$")

        fun isValidHost(host: String): Boolean = host.isNotEmpty() && (IPV4.matches(host) || (HOSTNAME.matches(host) && host.any { it.isLetter() }))
    }
}
