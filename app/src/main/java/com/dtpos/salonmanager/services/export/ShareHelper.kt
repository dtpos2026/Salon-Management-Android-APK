package com.dtpos.salonmanager.services.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares files through the Android share sheet (WhatsApp, email, Drive, ...). */
object ShareHelper {

    /** content:// URI that other apps (WhatsApp, Gmail, ...) may read. */
    fun uriFor(context: Context, file: File): android.net.Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
