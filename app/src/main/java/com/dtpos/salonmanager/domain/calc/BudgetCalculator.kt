package com.dtpos.salonmanager.domain.calc

import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.domain.model.BudgetGroup

/** Budget line for the month: planned vs actual. For SAVINGS "actual" means money saved. */
data class BudgetLine(
    val group: BudgetGroup,
    val budgetMinor: Long,
    val actualMinor: Long,
) {
    val hasBudget: Boolean get() = budgetMinor > 0L
    val usedPercent: Double get() = Percent.ratio(actualMinor, budgetMinor)
    val remainingMinor: Long get() = budgetMinor - actualMinor

    /** For spending lines going over budget is bad; for savings falling short is bad. */
    val isOverBudget: Boolean get() = hasBudget && group != BudgetGroup.SAVINGS && actualMinor > budgetMinor
    val isSavingsShort: Boolean get() = hasBudget && group == BudgetGroup.SAVINGS && actualMinor < budgetMinor
    val fraction: Float get() = if (!hasBudget) 0f else (actualMinor.toFloat() / budgetMinor).coerceIn(0f, 1f)
}

object BudgetCalculator {
    /**
     * @param businessExpensesMinor business expenses + staff payments for the month
     * @param personalByGroup personal expenses of the month grouped by the category budget group
     * @param personalTotalMinor all personal/household expenses of the month
     * @param businessProfitMinor business net profit of the month
     */
    fun lines(
        budgets: Map<BudgetGroup, Long>,
        businessExpensesMinor: Long,
        personalByGroup: Map<BudgetGroup, Long>,
        personalTotalMinor: Long,
        businessProfitMinor: Long,
    ): List<BudgetLine> = BudgetGroup.entries.map { group ->
        val actual = when (group) {
            BudgetGroup.BUSINESS_EXPENSES -> businessExpensesMinor
            BudgetGroup.HOUSEHOLD -> personalTotalMinor
            BudgetGroup.CHILDREN -> personalByGroup[BudgetGroup.CHILDREN] ?: 0L
            BudgetGroup.FOOD -> personalByGroup[BudgetGroup.FOOD] ?: 0L
            BudgetGroup.SAVINGS -> (businessProfitMinor - personalTotalMinor).coerceAtLeast(0L)
        }
        BudgetLine(group, budgets[group] ?: 0L, actual)
    }
}
