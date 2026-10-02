package com.dtpos.salonmanager.services.export

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.services.branding.LogoStore
import com.dtpos.salonmanager.services.prefs.ColorTheme
import com.dtpos.salonmanager.services.prefs.UiPreferences
import com.dtpos.salonmanager.services.printer.ReceiptImageRenderer
import com.dtpos.salonmanager.services.printer.ReceiptPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class ReceiptImageFormat(val mimeType: String, val extension: String, val compress: Bitmap.CompressFormat, val quality: Int) {
    PNG("image/png", "png", Bitmap.CompressFormat.PNG, 100),
    JPEG("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG, 92),
}

sealed interface SaveResult {
    data class Saved(val fileName: String) : SaveResult
    /** Android 8/9 need the storage permission before writing to the gallery. */
    data object NeedsPermission : SaveResult
    data object Failed : SaveResult
}

/** Turns a receipt into a branded image for the gallery, sharing and WhatsApp. */
class ReceiptExporter(
    private val context: Context,
    private val printer: ReceiptPrinter,
    private val logoStore: LogoStore,
    private val preferences: UiPreferences,
) {

    fun fileName(receipt: ReceiptData, format: ReceiptImageFormat): String {
        val number = receipt.receiptNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "Receipt_${number}_${DateTimeUtils.formatIso(DateTimeUtils.toLocalDate(receipt.createdAtMillis))}.${format.extension}"
    }

    private fun accentColor(): Int = when (preferences.colorTheme.value) {
        ColorTheme.ROYAL_PURPLE -> ReceiptImageRenderer.DEFAULT_ACCENT
        ColorTheme.BLACK_GOLD -> 0xFF1C1B19.toInt()
        ColorTheme.ROSE_GOLD -> 0xFF7B2947.toInt()
    }

    suspend fun render(receipt: ReceiptData, widthPx: Int = ReceiptImageRenderer.SHARE_WIDTH_PX): Bitmap = withContext(Dispatchers.Default) {
        val logo = if (receipt.showLogo) logoStore.loadBitmap(receipt.logoPath, 512) else null
        ReceiptImageRenderer(widthPx, accentColor(), forPrinter = false, rtl = printer.isRtl(), style = printer.receiptStyle())
            .render(receipt, printer.labels(receipt.paymentMethod), logo)
    }

    /** Writes the image into the app cache (for share sheets / WhatsApp). */
    suspend fun cacheFile(receipt: ReceiptData, format: ReceiptImageFormat = ReceiptImageFormat.PNG): File? = try {
        val bitmap = render(receipt)
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared/receipts").apply { mkdirs() }
            val file = File(dir, fileName(receipt, format))
            FileOutputStream(file).use { write(bitmap, format, it) }
            file
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Saves to Pictures/DT Salon so the receipt shows up in the phone gallery. */
    suspend fun saveToGallery(receipt: ReceiptData, format: ReceiptImageFormat): SaveResult {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            return SaveResult.NeedsPermission
        }
        return try {
            val bitmap = render(receipt)
            val name = fileName(receipt, format)
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= 29) saveWithMediaStore(bitmap, name, format) else saveLegacy(bitmap, name, format)
            }
        } catch (e: Exception) {
            SaveResult.Failed
        } catch (e: OutOfMemoryError) {
            SaveResult.Failed
        }
    }

    @RequiresApi(29)
    private fun saveWithMediaStore(bitmap: Bitmap, name: String, format: ReceiptImageFormat): SaveResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return SaveResult.Failed
        return try {
            resolver.openOutputStream(uri)?.use { write(bitmap, format, it) } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SaveResult.Saved(name)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            SaveResult.Failed
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, name: String, format: ReceiptImageFormat): SaveResult {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER).apply { mkdirs() }
        var file = File(dir, name)
        var n = 1
        while (file.exists()) file = File(dir, name.substringBeforeLast('.') + "($n)." + format.extension).also { n++ }
        FileOutputStream(file).use { write(bitmap, format, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mimeType), null)
        return SaveResult.Saved(file.name)
    }

    private fun write(bitmap: Bitmap, format: ReceiptImageFormat, out: OutputStream) {
        if (!bitmap.compress(format.compress, format.quality, out)) error("compress failed")
    }

    private companion object {
        const val FOLDER = "DT Salon"
    }
}
