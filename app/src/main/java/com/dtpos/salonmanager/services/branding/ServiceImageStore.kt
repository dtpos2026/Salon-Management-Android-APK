package com.dtpos.salonmanager.services.branding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Photos for the service menu (hair style, beard look, facial ...). Saved downsized as JPEG in
 * app-private storage under [DIR]; the database keeps the path relative to filesDir so backups
 * restore them on another phone.
 */
class ServiceImageStore(private val context: Context) {

    fun resolve(relativePath: String?): File? =
        relativePath?.takeIf { isValidPath(it) }?.let { File(context.filesDir, it) }?.takeIf { it.isFile }

    /** Decodes, downsizes (max [MAX_SIZE] px) and saves the picked photo. Returns the relative path or null. */
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
            val relative = "$DIR/${UUID.randomUUID().toString().replace("-", "")}.jpg"
            val target = File(context.filesDir, relative)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            relative
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun delete(relativePath: String?) {
        resolve(relativePath)?.delete()
    }

    /** All stored photos (for backups): relative path to file. */
    fun all(): Map<String, File> =
        File(context.filesDir, DIR).listFiles()?.filter { it.isFile && isValidPath("$DIR/${it.name}") }
            ?.associateBy { "$DIR/${it.name}" }.orEmpty()

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
        const val DIR = "menu"
        private const val MAX_SIZE = 720
        private val NAME = Regex("^$DIR/[A-Za-z0-9_-]{1,64}\\.jpg$")

        /** Only flat files inside [DIR] (no "..", no sub folders): safe for backup entry names. */
        fun isValidPath(path: String): Boolean = NAME.matches(path)
    }
}
