package com.dtpos.salonmanager.domain.calc

import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.DiscountType
import com.dtpos.salonmanager.domain.model.LineTotals
import com.dtpos.salonmanager.domain.model.SaleDiscount
import com.dtpos.salonmanager.domain.model.SaleTotals

enum class CartProblem {
    EMPTY_CART,
    INVALID_QUANTITY,
    INVALID_PRICE,
    LINE_DISCOUNT_EXCEEDS_LINE,
    SALE_DISCOUNT_EXCEEDS_TOTAL,
    INVALID_DISCOUNT_PERCENT,
    NEGATIVE_DISCOUNT,
    TENDERED_LESS_THAN_TOTAL,
}

/**
 * Pure sale arithmetic. Line discounts are applied first, then the sale-level discount is
 * split across lines in proportion to their value (largest-remainder rounding) so staff sales
 * and commission reflect what was really charged. Totals can never go negative.
 */
object SaleCalculator {

    fun calculate(lines: List<CartLine>, discount: SaleDiscount = SaleDiscount.NONE): SaleTotals {
        if (lines.isEmpty()) return SaleTotals.EMPTY

        val afterLine = lines.map { line ->
            val gross = line.grossMinor
            gross - line.discountMinor.coerceIn(0L, gross)
        }
        val subtotalAfterLineDiscount = afterLine.sum()
        val saleDiscount = saleDiscountAmount(subtotalAfterLineDiscount, discount)
        val allocations = allocate(saleDiscount, afterLine)

        val lineTotals = lines.mapIndexed { index, line ->
            val gross = line.grossMinor
            val lineDiscount = line.discountMinor.coerceIn(0L, gross)
            val net = afterLine[index] - allocations[index]
            LineTotals(
                key = line.key,
                grossMinor = gross,
                lineDiscountMinor = lineDiscount,
                allocatedSaleDiscountMinor = allocations[index],
                netMinor = net,
                commissionMinor = Money.applyBps(net, line.commissionBps.coerceIn(0, 10_000)),
            )
        }

        return SaleTotals(
            lines = lineTotals,
            subtotalMinor = lineTotals.sumOf { it.grossMinor },
            itemDiscountMinor = lineTotals.sumOf { it.lineDiscountMinor },
            saleDiscountMinor = saleDiscount,
            totalMinor = subtotalAfterLineDiscount - saleDiscount,
            serviceCount = lines.sumOf { it.quantity },
        )
    }

    /** Sale-level discount in minor units, clamped to what is left after line discounts. */
    fun saleDiscountAmount(amountAfterLineDiscounts: Long, discount: SaleDiscount): Long {
        if (amountAfterLineDiscounts <= 0L || discount.value <= 0L) return 0L
        val raw = when (discount.type) {
            DiscountType.AMOUNT -> discount.value
            DiscountType.PERCENT -> Money.applyBps(amountAfterLineDiscounts, discount.value.toInt().coerceIn(0, 10_000))
        }
        return raw.coerceIn(0L, amountAfterLineDiscounts)
    }

    /** Splits [amount] across [weights] proportionally; the parts always sum exactly to [amount]. */
    fun allocate(amount: Long, weights: List<Long>): List<Long> {
        val totalWeight = weights.sum()
        if (amount <= 0L || totalWeight <= 0L) return weights.map { 0L }
        val base = LongArray(weights.size)
        val remainders = ArrayList<Pair<Int, Long>>(weights.size)
        var allocated = 0L
        weights.forEachIndexed { index, weight ->
            val product = amount.toBigInteger() * weight.toBigInteger()
            val share = product / totalWeight.toBigInteger()
            val remainder = product % totalWeight.toBigInteger()
            base[index] = share.toLong()
            allocated += base[index]
            remainders += index to remainder.toLong()
        }
        var leftover = amount - allocated
        // Hand out the rounding leftovers to the largest remainders (ties -> earlier lines).
        for ((index, _) in remainders.sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })) {
            if (leftover <= 0L) break
            base[index] += 1
            leftover -= 1
        }
        return base.toList()
    }

    fun change(totalMinor: Long, tenderedMinor: Long?): Long =
        if (tenderedMinor == null || tenderedMinor < totalMinor) 0L else tenderedMinor - totalMinor

    /** Returns every problem that prevents the sale from being saved. Empty list = valid. */
    fun validate(lines: List<CartLine>, discount: SaleDiscount, tenderedMinor: Long? = null): List<CartProblem> {
        val problems = mutableListOf<CartProblem>()
        if (lines.isEmpty()) {
            problems += CartProblem.EMPTY_CART
            return problems
        }
        if (lines.any { it.quantity !in 1..Validators.MAX_QUANTITY }) problems += CartProblem.INVALID_QUANTITY
        if (lines.any { it.unitPriceMinor <= 0L || it.unitPriceMinor > Money.MAX_AMOUNT_MINOR }) problems += CartProblem.INVALID_PRICE
        if (lines.any { it.discountMinor < 0L } || discount.value < 0L) problems += CartProblem.NEGATIVE_DISCOUNT
        if (lines.any { it.discountMinor > it.grossMinor }) problems += CartProblem.LINE_DISCOUNT_EXCEEDS_LINE
        val afterLine = lines.sumOf { it.grossMinor - it.discountMinor.coerceIn(0L, it.grossMinor) }
        when (discount.type) {
            DiscountType.AMOUNT -> if (discount.value > afterLine) problems += CartProblem.SALE_DISCOUNT_EXCEEDS_TOTAL
            DiscountType.PERCENT -> if (discount.value > 10_000L) problems += CartProblem.INVALID_DISCOUNT_PERCENT
        }
        if (tenderedMinor != null && problems.isEmpty()) {
            val total = calculate(lines, discount).totalMinor
            if (tenderedMinor < total) problems += CartProblem.TENDERED_LESS_THAN_TOTAL
        }
        return problems
    }
}
