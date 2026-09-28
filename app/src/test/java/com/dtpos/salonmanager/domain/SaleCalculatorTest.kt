package com.dtpos.salonmanager.domain

import com.dtpos.salonmanager.domain.calc.CartProblem
import com.dtpos.salonmanager.domain.calc.SaleCalculator
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.SaleDiscount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleCalculatorTest {

    private fun line(key: String, price: Long, qty: Int = 1, discount: Long = 0, commissionBps: Int = 0) =
        CartLine(key, 1L, "Service $key", 1L, "Staff", price, qty, discount, commissionBps)

    @Test
    fun `subtotal and total without discounts`() {
        val totals = SaleCalculator.calculate(listOf(line("a", 50_000), line("b", 30_000, qty = 2)))
        assertEquals(110_000, totals.subtotalMinor)
        assertEquals(0, totals.totalDiscountMinor)
        assertEquals(110_000, totals.totalMinor)
        assertEquals(3, totals.serviceCount)
    }

    @Test
    fun `line discount reduces only its line`() {
        val totals = SaleCalculator.calculate(listOf(line("a", 50_000, discount = 5_000), line("b", 30_000)))
        assertEquals(80_000, totals.subtotalMinor)
        assertEquals(5_000, totals.itemDiscountMinor)
        assertEquals(75_000, totals.totalMinor)
        assertEquals(45_000, totals.lines[0].netMinor)
        assertEquals(30_000, totals.lines[1].netMinor)
    }

    @Test
    fun `percent sale discount is applied after line discounts`() {
        val totals = SaleCalculator.calculate(
            listOf(line("a", 100_000, discount = 20_000)),
            SaleDiscount.percent(1_000), // 10%
        )
        assertEquals(8_000, totals.saleDiscountMinor)
        assertEquals(72_000, totals.totalMinor)
    }

    @Test
    fun `fixed sale discount is split proportionally and sums exactly`() {
        val lines = listOf(line("a", 10_000), line("b", 10_000), line("c", 10_000))
        val totals = SaleCalculator.calculate(lines, SaleDiscount.amount(100)) // 1 rupee over 3 lines
        assertEquals(100, totals.lines.sumOf { it.allocatedSaleDiscountMinor })
        assertEquals(29_900, totals.totalMinor)
        assertEquals(totals.totalMinor, totals.lines.sumOf { it.netMinor })
    }

    @Test
    fun `discount can never make the total negative`() {
        val totals = SaleCalculator.calculate(listOf(line("a", 10_000)), SaleDiscount.amount(50_000))
        assertEquals(10_000, totals.saleDiscountMinor)
        assertEquals(0, totals.totalMinor)
    }

    @Test
    fun `commission is calculated on net line value`() {
        val totals = SaleCalculator.calculate(
            listOf(line("a", 100_000, commissionBps = 3_000), line("b", 50_000, commissionBps = 0)),
            SaleDiscount.amount(15_000),
        )
        // Line a carries 2/3 of the 150 rupee discount -> net 900 -> 30% = 270
        assertEquals(90_000, totals.lines[0].netMinor)
        assertEquals(27_000, totals.lines[0].commissionMinor)
        assertEquals(0, totals.lines[1].commissionMinor)
    }

    @Test
    fun `allocation handles zero weights`() {
        assertEquals(listOf(0L, 0L), SaleCalculator.allocate(100, listOf(0, 0)))
        assertEquals(listOf(34L, 33L, 33L), SaleCalculator.allocate(100, listOf(1, 1, 1)))
    }

    @Test
    fun `validation catches bad carts`() {
        assertEquals(listOf(CartProblem.EMPTY_CART), SaleCalculator.validate(emptyList(), SaleDiscount.NONE))
        assertTrue(CartProblem.INVALID_PRICE in SaleCalculator.validate(listOf(line("a", 0)), SaleDiscount.NONE))
        assertTrue(CartProblem.INVALID_QUANTITY in SaleCalculator.validate(listOf(line("a", 100, qty = 0)), SaleDiscount.NONE))
        assertTrue(
            CartProblem.LINE_DISCOUNT_EXCEEDS_LINE in
                SaleCalculator.validate(listOf(line("a", 100, discount = 200)), SaleDiscount.NONE),
        )
        assertTrue(
            CartProblem.SALE_DISCOUNT_EXCEEDS_TOTAL in
                SaleCalculator.validate(listOf(line("a", 100)), SaleDiscount.amount(101)),
        )
        assertTrue(
            CartProblem.INVALID_DISCOUNT_PERCENT in
                SaleCalculator.validate(listOf(line("a", 100)), SaleDiscount.percent(10_001)),
        )
        assertTrue(
            CartProblem.TENDERED_LESS_THAN_TOTAL in
                SaleCalculator.validate(listOf(line("a", 1_000)), SaleDiscount.NONE, tenderedMinor = 500),
        )
        assertTrue(SaleCalculator.validate(listOf(line("a", 1_000)), SaleDiscount.NONE, tenderedMinor = 1_000).isEmpty())
    }

    @Test
    fun `change is computed from tendered cash`() {
        assertEquals(25_000, SaleCalculator.change(75_000, 100_000))
        assertEquals(0, SaleCalculator.change(75_000, null))
        assertEquals(0, SaleCalculator.change(75_000, 50_000))
    }
}
