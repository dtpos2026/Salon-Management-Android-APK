package com.dtpos.salonmanager.services.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.dtpos.salonmanager.core.util.PhoneNumbers

/** WhatsApp, phone, email and browser hand-offs. Every call returns false instead of crashing. */
object ExternalApps {

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun whatsAppPackage(context: Context): String? = WHATSAPP_PACKAGES.firstOrNull { isInstalled(context, it) }

    fun isWhatsAppInstalled(context: Context): Boolean = whatsAppPackage(context) != null

    /** Opens a chat with [phone] (any local or international format) with [message] typed in. */
    fun openWhatsAppChat(context: Context, phone: String?, message: String): Boolean {
        val pkg = whatsAppPackage(context) ?: return false
        val digits = PhoneNumbers.toWhatsApp(phone).orEmpty()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message)))
            .setPackage(pkg)
        return start(context, intent)
    }

    /**
     * Shares an image (e.g. a receipt) to WhatsApp. With a phone number WhatsApp opens that chat
     * directly; the user always presses send themselves.
     */
    fun shareImageToWhatsApp(context: Context, image: Uri, mimeType: String, phone: String?, message: String): Boolean {
        val pkg = whatsAppPackage(context) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, image)
            putExtra(Intent.EXTRA_TEXT, message)
            PhoneNumbers.toWhatsApp(phone)?.let { putExtra("jid", "$it@s.whatsapp.net") }
            setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, intent)
    }

    fun dial(context: Context, phone: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))

    fun email(context: Context, address: String, subject: String): Boolean =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).putExtra(Intent.EXTRA_SUBJECT, subject))

    fun openUrl(context: Context, url: String): Boolean = try {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())))
    } catch (e: Exception) {
        false
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
