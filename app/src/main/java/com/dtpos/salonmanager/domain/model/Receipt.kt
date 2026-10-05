package com.dtpos.salonmanager.domain.model

import com.dtpos.salonmanager.core.util.CurrencyConfig

/** Salon profile used on receipts, reports and the dashboard header. */
data class BusinessProfile(
    val id: Long,
    val name: String,
    val phone: String?,
    val address: String?,
    val logoPath: String?,
    val currency: CurrencyConfig,
    val receiptPrefix: String,
    val receiptHeaderNote: String?,
    val receiptFooter: String?,
    val showLogoOnReceipt: Boolean,
    val showStaffOnReceipt: Boolean,
    val isSetupComplete: Boolean,
)

data class ReceiptItem(
    val serviceName: String,
    val staffName: String?,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineDiscountMinor: Long,
    /** unit price x quantity - line discount (sale-level discount is shown separately). */
    val lineTotalMinor: Long,
    /** Owner's choice per service: print the name / the price in bold. */
    val boldName: Boolean = false,
    val boldPrice: Boolean = false,
)

/** Everything printed on / shown in a receipt. Built from a stored sale, never from the cart. */
data class ReceiptData(
    val saleId: Long,
    val businessName: String,
    val businessPhone: String?,
    val businessAddress: String?,
    val logoPath: String?,
    val headerNote: String?,
    val footer: String?,
    val currency: CurrencyConfig,
    val showStaff: Boolean,
    val showLogo: Boolean,
    val receiptNumber: String,
    val createdAtMillis: Long,
    val customerName: String?,
    val customerPhone: String?,
    val items: List<ReceiptItem>,
    val subtotalMinor: Long,
    val itemDiscountMinor: Long,
    val saleDiscountMinor: Long,
    val totalMinor: Long,
    val paymentMethod: PaymentMethod,
    val amountTenderedMinor: Long?,
    val changeMinor: Long,
    val isVoided: Boolean,
    val voidReason: String?,
    /** JazzCash / EasyPaisa / bank account name when paid into an account. */
    val paymentAccountName: String? = null,
    /** Udhaar part of the total (0 when fully paid). */
    val creditMinor: Long = 0,
) {
    val paidMinor: Long get() = (totalMinor - creditMinor).coerceAtLeast(0)

    val totalDiscountMinor: Long get() = itemDiscountMinor + saleDiscountMinor
}
