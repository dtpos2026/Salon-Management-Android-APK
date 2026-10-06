package com.dtpos.salonmanager.services.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.dtpos.salonmanager.core.util.PhoneNumbers

/** What a WhatsApp hand-off opened. The message is never sent automatically. */
enum class WhatsAppResult {
    /** WhatsApp opened with the chat / message ready. */
    WHATSAPP,
    /** WhatsApp missing or refused; a browser link or the share sheet opened instead. */
    OTHER_APP,
    /** Nothing could be opened. */
    FAILED,
}

/** WhatsApp, phone, email and browser hand-offs. Every call returns a result instead of crashing. */
object ExternalApps {

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun whatsAppPackage(context: Context): String? = WHATSAPP_PACKAGES.firstOrNull { isInstalled(context, it) }

    /**
     * Opens a WhatsApp chat with [message] typed in (the owner presses send). Tries, in order:
     * WhatsApp's own whatsapp://send link, WhatsApp's text share, the api.whatsapp.com page in a
     * browser, then the Android share sheet. Never reports "sent": only what was opened.
     */
    fun whatsAppText(context: Context, phone: String?, message: String, chooserTitle: String = "WhatsApp"): WhatsAppResult {
        val digits = PhoneNumbers.toWhatsApp(phone)
        val pkg = whatsAppPackage(context)
        if (pkg != null) {
            val query = (digits?.let { "phone=$it&" } ?: "") + "text=" + Uri.encode(message)
            if (start(context, Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?$query")).setPackage(pkg))) return WhatsAppResult.WHATSAPP
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                digits?.let { putExtra("jid", "$it@s.whatsapp.net") }
                setPackage(pkg)
            }
            if (start(context, send)) return WhatsAppResult.WHATSAPP
        }
        if (digits != null) {
            val web = Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=" + Uri.encode(message))
            if (start(context, Intent(Intent.ACTION_VIEW, web))) return WhatsAppResult.OTHER_APP
        }
        return if (shareText(context, message, chooserTitle)) WhatsAppResult.OTHER_APP else WhatsAppResult.FAILED
    }

    /**
     * Shares an image (receipt, token) to WhatsApp, straight into [phone]'s chat when WhatsApp
     * knows the number; without WhatsApp the Android share sheet opens with the same picture.
     */
    fun whatsAppImage(context: Context, image: Uri, mimeType: String, phone: String?, message: String, chooserTitle: String = "WhatsApp"): WhatsAppResult {
        val pkg = whatsAppPackage(context)
        if (pkg != null) {
            val intent = imageIntent(image, mimeType, message).apply {
                PhoneNumbers.toWhatsApp(phone)?.let { putExtra("jid", "$it@s.whatsapp.net") }
                setPackage(pkg)
            }
            if (start(context, intent)) return WhatsAppResult.WHATSAPP
        }
        val chooser = Intent.createChooser(imageIntent(image, mimeType, message), chooserTitle)
        return if (start(context, chooser)) WhatsAppResult.OTHER_APP else WhatsAppResult.FAILED
    }

    /** Android share sheet with plain text (WhatsApp, SMS, Messenger, ...). */
    fun shareText(context: Context, message: String, chooserTitle: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
        return start(context, Intent.createChooser(send, chooserTitle))
    }

    /** Android share sheet with an image and caption. */
    fun shareImage(context: Context, image: Uri, mimeType: String, message: String, chooserTitle: String): Boolean =
        start(context, Intent.createChooser(imageIntent(image, mimeType, message), chooserTitle))

    private fun imageIntent(image: Uri, mimeType: String, message: String) = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, image)
        if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
        clipData = android.content.ClipData.newRawUri("", image)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Opens the phone's SMS app with [message] typed in for [phone] (the owner presses send).
     * Normal SMS charges apply; nothing is sent automatically.
     */
    fun sms(context: Context, phone: String?, message: String): Boolean {
        val number = phone?.filter { it.isDigit() || it == '+' }.orEmpty()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", message)
        return start(context, intent) || shareText(context, message, "SMS")
    }

    /** Android's location on/off screen. */
    fun openLocationSettings(context: Context): Boolean =
        start(context, Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) || openAppSettings(context)

    /** This app's page in Android settings (permissions). */
    fun openAppSettings(context: Context): Boolean = start(
        context,
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

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
    } catch (e: IllegalArgumentException) {
        false
    }
}
