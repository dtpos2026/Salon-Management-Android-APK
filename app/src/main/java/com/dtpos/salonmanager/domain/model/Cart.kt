package com.dtpos.salonmanager.domain.model

/** One service line in the POS cart. All money in minor units. */
data class CartLine(
    val key: String,
    val serviceId: Long?,
    val serviceName: String,
    val staffId: Long?,
    val staffName: String?,
    val unitPriceMinor: Long,
    val quantity: Int,
    val discountMinor: Long = 0L,
    /** Staff commission rate snapshot, basis points (1000 = 10%). 0 when staff earns no commission. */
    val commissionBps: Int = 0,
) {
    val grossMinor: Long get() = unitPriceMinor * quantity
}

/** Discount applied to the whole sale: a fixed amount (minor units) or a percentage (basis points). */
data class SaleDiscount(val type: DiscountType, val value: Long) {
    companion object {
        val NONE = SaleDiscount(DiscountType.AMOUNT, 0L)
        fun amount(minor: Long) = SaleDiscount(DiscountType.AMOUNT, minor)
        fun percent(bps: Int) = SaleDiscount(DiscountType.PERCENT, bps.toLong())
    }
}

data class LineTotals(
    val key: String,
    val grossMinor: Long,
    val lineDiscountMinor: Long,
    /** Share of the sale-level discount allocated to this line (proportional). */
    val allocatedSaleDiscountMinor: Long,
    /** What the salon actually earned for this line; used for staff sales and commission. */
    val netMinor: Long,
    val commissionMinor: Long,
)

data class SaleTotals(
    val lines: List<LineTotals>,
    /** Sum of unit price x quantity before any discount. */
    val subtotalMinor: Long,
    val itemDiscountMinor: Long,
    val saleDiscountMinor: Long,
    val totalMinor: Long,
    val serviceCount: Int,
) {
    val totalDiscountMinor: Long get() = itemDiscountMinor + saleDiscountMinor

    companion object {
        val EMPTY = SaleTotals(emptyList(), 0, 0, 0, 0, 0)
    }
}

/** Everything needed to atomically record a sale. */
data class NewSaleRequest(
    val customerId: Long?,
    val customerName: String?,
    val customerPhone: String?,
    val lines: List<CartLine>,
    val discount: SaleDiscount,
    val paymentMethod: PaymentMethod,
    /** Cash handed over by the customer (optional; used to show change). */
    val amountTenderedMinor: Long?,
    val note: String?,
)
