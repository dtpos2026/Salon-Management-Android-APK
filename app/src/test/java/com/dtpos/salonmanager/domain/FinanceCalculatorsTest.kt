package com.dtpos.salonmanager.domain

import com.dtpos.salonmanager.domain.calc.BudgetCalculator
import com.dtpos.salonmanager.domain.calc.CashCalculator
import com.dtpos.salonmanager.domain.calc.CashDifferenceStatus
import com.dtpos.salonmanager.domain.calc.ProfitCalculator
import com.dtpos.salonmanager.domain.calc.StaffPayCalculator
import com.dtpos.salonmanager.domain.calc.TargetCalculator
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCalculatorsTest {

    private fun rs(units: Long) = units * 100

    @Test
    fun `business profit excludes personal spending - example from the specification`() {
        val summary = ProfitCalculator.summarize(
            grossSalesMinor = rs(100_000),
            businessExpensesMinor = rs(30_000),
            staffPaymentsMinor = 0,
            personalExpensesMinor = rs(20_000),
        )
        assertEquals(rs(70_000), summary.businessProfitMinor)
        assertEquals(rs(50_000), summary.remainingCashMinor)
        assertEquals(70.0, summary.profitMarginPercent, 0.001)
    }

    @Test
    fun `staff payments are business costs`() {
        val summary = ProfitCalculator.summarize(rs(18_500), rs(4_200), rs(1_000), rs(500))
        assertEquals(rs(5_200), summary.totalBusinessCostsMinor)
        assertEquals(rs(13_300), summary.businessProfitMinor)
        assertEquals(rs(12_800), summary.remainingCashMinor)
        assertFalse(summary.isLoss)
    }

    @Test
    fun `change percent needs a baseline`() {
        assertNull(ProfitCalculator.changePercent(100, 0))
        assertEquals(25.0, ProfitCalculator.changePercent(125, 100)!!, 0.001)
        assertEquals(-50.0, ProfitCalculator.changePercent(50, 100)!!, 0.001)
    }

    @Test
    fun `cash counter expected closing and difference`() {
        val breakdown = CashCalculator.breakdown(
            openingMinor = rs(2_000),
            signedTotalsByType = mapOf(
                CashTxType.SALE to rs(15_000),
                CashTxType.SALE_VOID to -rs(500),
                CashTxType.EXPENSE to -rs(1_200),
                CashTxType.STAFF_PAYMENT to -rs(3_000),
                CashTxType.CASH_IN to rs(1_000),
                CashTxType.CASH_OUT to -rs(2_000),
            ),
        )
        assertEquals(rs(11_300), breakdown.expectedClosingMinor)
        assertEquals(-rs(300), breakdown.differenceFor(rs(11_000)))
        assertEquals(CashDifferenceStatus.SHORT, CashCalculator.status(-rs(300)))
        assertEquals(CashDifferenceStatus.EXCESS, CashCalculator.status(rs(10)))
        assertEquals(CashDifferenceStatus.BALANCED, CashCalculator.status(0))
    }

    @Test
    fun `cash signs follow drawer direction`() {
        assertEquals(500, CashCalculator.signedAmount(CashTxType.SALE, 500))
        assertEquals(500, CashCalculator.signedAmount(CashTxType.CASH_IN, -500))
        assertEquals(-500, CashCalculator.signedAmount(CashTxType.EXPENSE, 500))
        assertEquals(-500, CashCalculator.signedAmount(CashTxType.STAFF_PAYMENT, 500))
        assertEquals(-500, CashCalculator.signedAmount(CashTxType.SALE_VOID, 500))
    }

    @Test
    fun `staff settlement fixed plus commission with advance`() {
        val s = StaffPayCalculator.settlement(
            salaryType = SalaryType.FIXED_PLUS_COMMISSION,
            monthlyFixedSalaryMinor = rs(20_000),
            commissionEarnedMinor = rs(6_000),
            paymentsByType = mapOf(
                StaffPaymentType.ADVANCE to rs(5_000),
                StaffPaymentType.SALARY to rs(10_000),
                StaffPaymentType.BONUS to rs(1_000),
            ),
        )
        assertEquals(rs(26_000), s.earnedMinor)
        assertEquals(rs(15_000), s.settledMinor)
        assertEquals(rs(11_000), s.outstandingMinor)
        assertEquals(rs(16_000), s.totalPaidMinor)
    }

    @Test
    fun `commission only staff ignores fixed salary and reports overpayment`() {
        val s = StaffPayCalculator.settlement(
            salaryType = SalaryType.COMMISSION,
            monthlyFixedSalaryMinor = rs(20_000),
            commissionEarnedMinor = rs(3_000),
            paymentsByType = mapOf(StaffPaymentType.ADVANCE to rs(4_000)),
        )
        assertEquals(rs(3_000), s.earnedMinor)
        assertEquals(0, s.outstandingMinor)
        assertEquals(rs(1_000), s.overpaidMinor)
    }

    @Test
    fun `target progress and required daily sales`() {
        val progress = TargetCalculator.progress(rs(500_000), rs(350_000))
        assertEquals(rs(150_000), progress.remainingMinor)
        assertEquals(70.0, progress.achievedPercent, 0.001)
        assertEquals(rs(7_500), TargetCalculator.requiredPerDay(progress, 20))
        assertFalse(progress.isAchieved)

        val daily = TargetCalculator.progress(rs(20_000), rs(18_500))
        assertEquals(92.5, daily.achievedPercent, 0.001)
        assertEquals(0.925f, daily.fraction, 0.0001f)

        val done = TargetCalculator.progress(rs(100), rs(150))
        assertTrue(done.isAchieved)
        assertEquals(0, done.remainingMinor)
        assertEquals(1f, done.fraction, 0f)
    }

    @Test
    fun `projection extrapolates the current pace`() {
        assertEquals(rs(300_000), TargetCalculator.projectedTotal(rs(100_000), 10, 30))
        assertEquals(rs(5), TargetCalculator.projectedTotal(rs(5), 0, 30))
    }

    @Test
    fun `budget lines compare plan and actual`() {
        val lines = BudgetCalculator.lines(
            budgets = mapOf(
                BudgetGroup.BUSINESS_EXPENSES to rs(30_000),
                BudgetGroup.HOUSEHOLD to rs(20_000),
                BudgetGroup.FOOD to rs(8_000),
                BudgetGroup.SAVINGS to rs(40_000),
            ),
            businessExpensesMinor = rs(32_000),
            personalByGroup = mapOf(BudgetGroup.FOOD to rs(6_000), BudgetGroup.CHILDREN to rs(4_000)),
            personalTotalMinor = rs(15_000),
            businessProfitMinor = rs(50_000),
        ).associateBy { it.group }
        assertTrue(lines.getValue(BudgetGroup.BUSINESS_EXPENSES).isOverBudget)
        assertFalse(lines.getValue(BudgetGroup.HOUSEHOLD).isOverBudget)
        assertEquals(rs(4_000), lines.getValue(BudgetGroup.CHILDREN).actualMinor)
        assertFalse(lines.getValue(BudgetGroup.CHILDREN).hasBudget)
        assertEquals(rs(35_000), lines.getValue(BudgetGroup.SAVINGS).actualMinor)
        assertTrue(lines.getValue(BudgetGroup.SAVINGS).isSavingsShort)
    }
}
