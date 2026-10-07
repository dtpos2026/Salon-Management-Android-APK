package com.dtpos.salonmanager.services

import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.presentation.common.showWhatsAppResult
import com.dtpos.salonmanager.services.export.ExternalApps
import com.dtpos.salonmanager.services.export.WhatsAppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * What every WhatsApp / SMS button hands to Android: the right app, the customer's number and
 * the full message, with a working fallback when WhatsApp is missing. Only apps registered in
 * each test exist on this "phone"; starting anything else fails like on a real device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExternalAppsTest {

    private lateinit var app: Application
    private val message = "Assalam o Alaikum Ali,\nReceipt SAL-000123\nTotal: Rs. 1,000\nUdhaar (balance): Rs. 600"

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).checkActivities(true)
    }

    private fun installApp(pkg: String, filter: IntentFilter) {
        val pm = shadowOf(app.packageManager)
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = ApplicationInfo().apply { packageName = pkg } })
        val component = ComponentName(pkg, "$pkg.MainActivity")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, filter.apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    private fun viewFilter(scheme: String) = IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme(scheme) }

    @Test
    fun `WhatsApp opens the customer's chat with the whole message typed in`() {
        installApp("com.whatsapp", viewFilter("https"))
        val result = ExternalApps.whatsAppText(app, "0300-1234567", message)
        assertEquals(WhatsAppResult.WHATSAPP, result)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("api.whatsapp.com", intent.data?.host)
        assertEquals("923001234567", intent.data?.getQueryParameter("phone"))
        assertEquals(message, intent.data?.getQueryParameter("text"))
    }

    @Test
    fun `WhatsApp without a customer number opens its own Send screen with the message`() {
        installApp("com.whatsapp", IntentFilter(Intent.ACTION_SEND).apply { addDataType("text/plain") })
        assertEquals(WhatsAppResult.WHATSAPP, ExternalApps.whatsAppText(app, null, message))
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals(message, intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `WhatsApp Business is used when it is the only WhatsApp`() {
        installApp("com.whatsapp.w4b", viewFilter("https"))
        assertEquals(WhatsAppResult.WHATSAPP, ExternalApps.whatsAppText(app, "03001234567", message))
        assertEquals("com.whatsapp.w4b", shadowOf(app).nextStartedActivity.`package`)
    }

    @Test
    fun `without WhatsApp the WhatsApp web link opens in the browser`() {
        installApp("com.android.chrome", viewFilter("https"))
        val result = ExternalApps.whatsAppText(app, "+92 300 1234567", message)
        assertEquals(WhatsAppResult.OTHER_APP, result)
        val data = shadowOf(app).nextStartedActivity.data
        assertEquals("api.whatsapp.com", data?.host)
        assertEquals("923001234567", data?.getQueryParameter("phone"))
        assertEquals(message, data?.getQueryParameter("text"))
    }

    @Test
    fun `without WhatsApp and without a number the share sheet opens with the message`() {
        installApp("com.android.intentresolver", IntentFilter(Intent.ACTION_CHOOSER))
        val result = ExternalApps.whatsAppText(app, null, message)
        assertEquals(WhatsAppResult.OTHER_APP, result)
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(message, inner?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `when nothing can open the message is copied and the owner is told`() {
        val result = ExternalApps.whatsAppText(app, "03001234567", message)
        assertEquals(WhatsAppResult.FAILED, result)
        app.showWhatsAppResult(result, message)
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(message, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(app.getString(R.string.whatsapp_copied), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `receipt picture opens WhatsApp's Send screen with the message as caption`() {
        installApp("com.whatsapp", IntentFilter(Intent.ACTION_SEND).apply { addDataType("image/*") })
        val picture = Uri.parse("content://com.dtpos.salonmanager.files/receipts/SAL-000123.png")
        val result = ExternalApps.whatsAppImage(app, picture, "image/png", "03001234567", message)
        assertEquals(WhatsAppResult.WHATSAPP, result)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("image/png", intent.type)
        @Suppress("DEPRECATION")
        assertEquals(picture, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(message, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(null, intent.getStringExtra("jid"))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun `SMS opens the messaging app with the number and the message`() {
        installApp("com.google.android.apps.messaging", IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("smsto") })
        assertTrue(ExternalApps.sms(app, "0300 1234567", message))
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto", intent.data?.scheme)
        assertEquals(message, intent.getStringExtra("sms_body"))
    }
}
