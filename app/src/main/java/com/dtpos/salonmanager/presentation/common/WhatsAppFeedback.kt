package com.dtpos.salonmanager.presentation.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.services.export.ExternalApps
import com.dtpos.salonmanager.services.export.ShareMessages
import com.dtpos.salonmanager.services.export.WhatsAppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells the owner what actually happened after a WhatsApp button (never claims "sent"). When
 * nothing could be opened and the [message] is known, it is copied so it can be pasted.
 */
fun Context.showWhatsAppResult(result: WhatsAppResult, message: String? = null) {
    val res = when (result) {
        WhatsAppResult.WHATSAPP -> return
        WhatsAppResult.OTHER_APP -> R.string.whatsapp_fallback_opened
        WhatsAppResult.FAILED -> if (message != null && copyToClipboard(message)) R.string.whatsapp_copied else R.string.whatsapp_failed
    }
    Toast.makeText(this, getString(res), Toast.LENGTH_LONG).show()
}

/**
 * Every WhatsApp button goes through this. It opens WhatsApp with the message and then checks
 * that WhatsApp really came to the front: some phones silently ignore a direct hand-off, so the
 * button seemed to do nothing. When nothing opened within [OPEN_CHECK_MS], the Android share
 * sheet opens with the same message (WhatsApp is in that list) and the owner is told why.
 * Pictures go through Share only: sending a picture straight to WhatsApp did not work on
 * salons' phones.
 */
class WhatsAppLauncher(private val context: Context, private val scope: CoroutineScope) {

    /** The message in WhatsApp: the customer's chat when [phone] is known, else WhatsApp's chat picker. */
    fun text(phone: String?, message: String, title: String = "WhatsApp"): WhatsAppResult = run(
        message,
        open = { ExternalApps.whatsAppText(context, phone, message, title) },
        fallback = { ExternalApps.shareText(context, message, title) },
    )

    private fun run(message: String, open: () -> WhatsAppResult, fallback: () -> Boolean): WhatsAppResult {
        val screen = ExternalApps.activityOf(context) as? LifecycleOwner
        val result = open()
        context.showWhatsAppResult(result, message)
        if (result == WhatsAppResult.WHATSAPP && screen != null) {
            scope.launch {
                // WhatsApp on top pauses this screen; still in front after the wait = nothing opened.
                val left = withTimeoutOrNull(OPEN_CHECK_MS) {
                    screen.lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) }
                }
                if (left == null && screen.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    if (fallback()) {
                        Toast.makeText(context, context.getString(R.string.whatsapp_not_opened), Toast.LENGTH_LONG).show()
                    } else {
                        context.showWhatsAppResult(WhatsAppResult.FAILED, message)
                    }
                }
            }
        }
        return result
    }

    companion object {
        const val OPEN_CHECK_MS = 3_000L
    }
}

@Composable
fun rememberWhatsAppLauncher(): WhatsAppLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { WhatsAppLauncher(context, scope) }
}

private fun Context.copyToClipboard(text: String): Boolean = try {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    clipboard != null
} catch (e: Exception) {
    false
}

private fun Context.greeting(name: String?): String =
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { getString(R.string.share_greeting_name, it) } ?: getString(R.string.share_greeting)

/** WhatsApp / SMS text for a receipt: every service, total, paid and udhaar, in the app's language. */
fun Context.receiptShareText(data: ReceiptData, money: CurrencyFormatter): String = ShareMessages.receipt(
    data = data,
    labels = ShareMessages.Labels(
        greeting = greeting(data.customerName),
        receipt = getString(R.string.share_receipt_label),
        discount = getString(R.string.share_discount),
        total = getString(R.string.share_total),
        paid = getString(R.string.share_paid),
        balance = getString(R.string.share_balance),
        thanks = getString(R.string.share_thanks),
        cancelled = getString(R.string.share_cancelled),
    ),
    money = money,
    dateTime = DateTimeUtils.formatDateTime(data.createdAtMillis),
    paymentLabel = data.paymentAccountName ?: getString(data.paymentMethod.labelRes),
)

/** Udhaar reminder: the bill, what was paid and what is still due. */
fun Context.dueReminderText(due: DueEntity, salon: String, money: CurrencyFormatter): String = ShareMessages.dueReminder(
    labels = ShareMessages.DueLabels(
        greeting = greeting(due.customerName),
        intro = getString(R.string.share_due_intro, salon.ifBlank { getString(R.string.app_name) }),
        receipt = getString(R.string.share_receipt_label),
        note = getString(R.string.share_note),
        bill = getString(R.string.share_bill),
        paid = getString(R.string.share_paid),
        balance = getString(R.string.share_due_balance),
        closing = getString(R.string.share_due_closing),
    ),
    money = money,
    fromSale = due.saleId != null,
    reference = due.note,
    date = DateTimeUtils.formatDate(due.createdAt),
    amountMinor = due.amountMinor,
    paidMinor = due.paidMinor,
)

/** Thank-you message after an udhaar payment: received, how, paid so far and what is left (or fully paid). */
fun Context.duePaidText(receipt: com.dtpos.salonmanager.data.repository.DuePaymentReceipt, salon: String, money: CurrencyFormatter): String {
    val due = receipt.due
    val payment = receipt.payment
    return ShareMessages.duePaid(
        labels = ShareMessages.DuePaidLabels(
            greeting = greeting(due.customerName),
            intro = getString(R.string.due_paid_intro, salon.ifBlank { getString(R.string.app_name) }),
            receipt = getString(R.string.share_receipt_label),
            note = getString(R.string.share_note),
            received = getString(R.string.due_paid_received),
            bill = getString(R.string.share_bill),
            paidTotal = getString(R.string.due_paid_total),
            balance = getString(R.string.due_paid_balance),
            fullyPaid = getString(R.string.due_paid_full),
            thanks = getString(R.string.due_paid_thanks),
        ),
        money = money,
        fromSale = due.saleId != null,
        reference = due.note,
        date = DateTimeUtils.formatDate(payment.createdAt),
        receivedMinor = payment.amountMinor,
        method = payment.paymentAccountName ?: getString(payment.paymentMethod.labelRes),
        billMinor = due.amountMinor,
        paidMinor = receipt.paidSoFarMinor,
    )
}
