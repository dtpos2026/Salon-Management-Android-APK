package com.dtpos.salonmanager.services.export

import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.domain.model.ReceiptData

/**
 * The texts that go with WhatsApp / SMS hand-offs, built from the stored sale or due so the
 * customer reads exactly what was billed: name, salon, receipt number, date, every service,
 * discount, total, what was paid and the udhaar left. Plain functions; the words come from
 * string resources through [Labels].
 */
object ShareMessages {

    data class Labels(
        /** "Assalam o Alaikum Ali," (or without a name). */
        val greeting: String,
        val receipt: String,
        val discount: String,
        val total: String,
        val paid: String,
        /** Udhaar left on this bill. */
        val balance: String,
        val thanks: String,
        val cancelled: String,
    )

    data class DueLabels(
        val greeting: String,
        /** "Friendly reminder from %1$s" already formatted with the salon's name. */
        val intro: String,
        val receipt: String,
        val note: String,
        val bill: String,
        val paid: String,
        val balance: String,
        val closing: String,
    )

    data class DuePaidLabels(
        val greeting: String,
        /** "Payment received at %1$s" already formatted with the salon's name. */
        val intro: String,
        val receipt: String,
        val note: String,
        val received: String,
        val bill: String,
        val paidTotal: String,
        val balance: String,
        val fullyPaid: String,
        val thanks: String,
    )

    /** Receipt text for WhatsApp / SMS. [paymentLabel] is the payment method or account name. */
    fun receipt(data: ReceiptData, labels: Labels, money: CurrencyFormatter, dateTime: String, paymentLabel: String): String {
        val lines = mutableListOf(labels.greeting)
        data.businessName.takeIf { it.isNotBlank() }?.let { lines += it }
        lines += "${labels.receipt} ${data.receiptNumber} · $dateTime"
        if (data.isVoided) lines += labels.cancelled
        lines += ""
        data.items.forEach { item ->
            val qty = if (item.quantity > 1) " x${item.quantity}" else ""
            lines += "• ${item.serviceName}$qty: ${money.format(item.lineTotalMinor)}"
        }
        if (data.saleDiscountMinor > 0) lines += "${labels.discount}: -${money.format(data.saleDiscountMinor)}"
        lines += "${labels.total}: ${money.format(data.totalMinor)}"
        if (data.creditMinor > 0) {
            lines += "${labels.paid}: ${money.format(data.paidMinor)}" + if (data.paidMinor > 0) " ($paymentLabel)" else ""
            lines += "${labels.balance}: ${money.format(data.creditMinor)}"
        } else {
            lines += "${labels.paid} ($paymentLabel): ${money.format(data.totalMinor)}"
        }
        lines += ""
        lines += labels.thanks
        return lines.joinToString("\n")
    }

    /**
     * Udhaar reminder: what the bill was, what was paid and what is left. [reference] is the
     * receipt number for a credit sale or the owner's note for a due added by hand.
     */
    fun dueReminder(
        labels: DueLabels,
        money: CurrencyFormatter,
        fromSale: Boolean,
        reference: String?,
        date: String,
        amountMinor: Long,
        paidMinor: Long,
    ): String {
        val lines = mutableListOf(labels.greeting, labels.intro, "")
        val ref = reference?.trim().orEmpty()
        lines += when {
            fromSale && ref.isNotEmpty() -> "${labels.receipt} $ref · $date"
            ref.isNotEmpty() -> "${labels.note}: $ref · $date"
            else -> date
        }
        lines += "${labels.bill}: ${money.format(amountMinor)}"
        if (paidMinor > 0) lines += "${labels.paid}: ${money.format(paidMinor)}"
        lines += "${labels.balance}: ${money.format((amountMinor - paidMinor).coerceAtLeast(0))}"
        lines += ""
        lines += labels.closing
        return lines.joinToString("\n")
    }

    /**
     * Thank-you message after an udhaar payment: how much was received and how ([method]: cash
     * or the account), the bill, paid so far, and what is still due or that it is fully paid.
     */
    fun duePaid(
        labels: DuePaidLabels,
        money: CurrencyFormatter,
        fromSale: Boolean,
        reference: String?,
        date: String,
        receivedMinor: Long,
        method: String,
        billMinor: Long,
        paidMinor: Long,
    ): String {
        val lines = mutableListOf(labels.greeting, labels.intro, "")
        val ref = reference?.trim().orEmpty()
        when {
            fromSale && ref.isNotEmpty() -> lines += "${labels.receipt} $ref"
            ref.isNotEmpty() -> lines += "${labels.note}: $ref"
        }
        lines += "${labels.received}: ${money.format(receivedMinor)} ($method) · $date"
        lines += "${labels.bill}: ${money.format(billMinor)}"
        lines += "${labels.paidTotal}: ${money.format(paidMinor)}"
        val balance = (billMinor - paidMinor).coerceAtLeast(0)
        lines += if (balance == 0L) labels.fullyPaid else "${labels.balance}: ${money.format(balance)}"
        lines += ""
        lines += labels.thanks
        return lines.joinToString("\n")
    }
}
