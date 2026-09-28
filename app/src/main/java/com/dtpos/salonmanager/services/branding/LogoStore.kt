package com.dtpos.salonmanager.services.branding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Stores the salon logo inside app-private storage. The database keeps a path *relative* to
 * filesDir so backups restore correctly on another phone.
 */
class LogoStore(private val context: Context) {

    fun resolve(relativePath: String?): File? =
        relativePath?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }?.takeIf { it.isFile }

    /** Decodes, downsizes (max 512 px) and saves the picked image. Returns the relative path or null. */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIZE && bounds.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val scale = minOf(MAX_SIZE.toFloat() / decoded.width, MAX_SIZE.toFloat() / decoded.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else {
                decoded
            }
            val target = File(context.filesDir, RELATIVE_PATH)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "logo.tmp")
            FileOutputStream(temp).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            RELATIVE_PATH
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun delete() {
        File(context.filesDir, RELATIVE_PATH).delete()
    }

    /** Loads the logo for display/printing; returns null when missing or unreadable. */
    fun loadBitmap(relativePath: String?, maxSize: Int = MAX_SIZE): Bitmap? {
        val file = resolve(relativePath) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    companion object {
        const val RELATIVE_PATH = "branding/logo.png"
        private const val MAX_SIZE = 512
    }
}
