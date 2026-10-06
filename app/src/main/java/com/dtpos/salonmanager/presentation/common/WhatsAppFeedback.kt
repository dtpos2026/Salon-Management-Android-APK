package com.dtpos.salonmanager.presentation.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.entities.DueEntity
import com.dtpos.salonmanager.domain.model.ReceiptData
import com.dtpos.salonmanager.services.export.ShareMessages
import com.dtpos.salonmanager.services.export.WhatsAppResult

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
