package com.dtpos.salonmanager.services.export

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
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

    /** Installed WhatsApp apps (normal first). Detection can miss them on some phones, so the
     * launchers below also try every WhatsApp package directly. */
    fun whatsAppPackage(context: Context): String? = WHATSAPP_PACKAGES.firstOrNull { isInstalled(context, it) }

    /** WhatsApp packages to try: the detected one first, then the others. */
    private fun whatsAppCandidates(context: Context): List<String> =
        (listOfNotNull(whatsAppPackage(context)) + WHATSAPP_PACKAGES).distinct()

    /** Starts [build] for the first WhatsApp app that accepts it. */
    private fun startInWhatsApp(context: Context, build: (String) -> Intent): Boolean =
        whatsAppCandidates(context).any { pkg -> start(context, build(pkg)) }

    private fun clickToChat(digits: String, message: String): Uri =
        Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=" + Uri.encode(message))

    /**
     * Opens WhatsApp with [message] typed in (the owner presses send). With a number: WhatsApp's
     * official click-to-chat link opened in the WhatsApp app, so the customer's chat opens.
     * Without a number: WhatsApp's own Send screen to pick the chat. Without WhatsApp: the link
     * in a browser, then the Android share sheet. Never reports "sent": only what was opened.
     */
    fun whatsAppText(context: Context, phone: String?, message: String, chooserTitle: String = "WhatsApp"): WhatsAppResult {
        val digits = PhoneNumbers.toWhatsApp(phone)
        if (digits != null && startInWhatsApp(context) { Intent(Intent.ACTION_VIEW, clickToChat(digits, message)).setPackage(it) }) return WhatsAppResult.WHATSAPP
        if (startInWhatsApp(context) { Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message).setPackage(it) }) {
            return WhatsAppResult.WHATSAPP
        }
        if (digits != null && start(context, Intent(Intent.ACTION_VIEW, clickToChat(digits, message)))) return WhatsAppResult.OTHER_APP
        return if (shareText(context, message, chooserTitle)) WhatsAppResult.OTHER_APP else WhatsAppResult.FAILED
    }

    /**
     * Sends an image (receipt, token) with [message] as caption through WhatsApp's own Send
     * screen, where the owner taps the customer's chat (WhatsApp lets apps attach a picture this
     * way only). Without WhatsApp the Android share sheet opens with the same picture.
     */
    @Suppress("UNUSED_PARAMETER")
    fun whatsAppImage(context: Context, image: Uri, mimeType: String, phone: String?, message: String, chooserTitle: String = "WhatsApp"): WhatsAppResult {
        if (startInWhatsApp(context) { imageIntent(image, mimeType, message).setPackage(it) }) return WhatsAppResult.WHATSAPP
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

    /** The screen behind [context] (Compose dialogs and language wrappers hide it), if any. */
    fun activityOf(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Started from the screen itself, like Android's own share sheet does. A separate task
     * (FLAG_ACTIVITY_NEW_TASK) is only used without a screen: some phones silently drop a
     * hand-off into WhatsApp's already running task, so the button seemed to do nothing.
     */
    private fun start(context: Context, intent: Intent): Boolean = try {
        val activity = activityOf(context)
        if (activity != null) activity.startActivity(intent) else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: RuntimeException) {
        // SecurityException, a refused URI, or another app's crash on launch: try the next way.
        false
    }
}
