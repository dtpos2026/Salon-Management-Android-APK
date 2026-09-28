package com.dtpos.salonmanager.data.database

import com.dtpos.salonmanager.data.database.entities.ExpenseCategoryEntity
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.ExpenseType

/** Built-in reference data. Category names are stored in English; the UI translates by [systemKey]. */
object DefaultData {

    data class DefaultCategory(val key: String, val name: String, val type: ExpenseType, val group: BudgetGroup? = null)

    val expenseCategories: List<DefaultCategory> = listOf(
        DefaultCategory("electricity", "Electricity", ExpenseType.BUSINESS),
        DefaultCategory("gas", "Gas", ExpenseType.BUSINESS),
        DefaultCategory("water", "Water", ExpenseType.BUSINESS),
        DefaultCategory("rent", "Rent", ExpenseType.BUSINESS),
        DefaultCategory("internet", "Internet", ExpenseType.BUSINESS),
        DefaultCategory("supplies", "Supplies", ExpenseType.BUSINESS),
        DefaultCategory("cosmetics", "Cosmetics", ExpenseType.BUSINESS),
        DefaultCategory("equipment", "Equipment", ExpenseType.BUSINESS),
        DefaultCategory("maintenance", "Maintenance", ExpenseType.BUSINESS),
        DefaultCategory("marketing", "Marketing", ExpenseType.BUSINESS),
        DefaultCategory("staff_expenses", "Staff expenses", ExpenseType.BUSINESS),
        DefaultCategory("business_other", "Other", ExpenseType.BUSINESS),
        DefaultCategory("food", "Food", ExpenseType.PERSONAL, BudgetGroup.FOOD),
        DefaultCategory("children", "Children", ExpenseType.PERSONAL, BudgetGroup.CHILDREN),
        DefaultCategory("education", "Education", ExpenseType.PERSONAL, BudgetGroup.CHILDREN),
        DefaultCategory("medical", "Medical", ExpenseType.PERSONAL),
        DefaultCategory("home", "Home", ExpenseType.PERSONAL),
        DefaultCategory("transport", "Transport", ExpenseType.PERSONAL),
        DefaultCategory("shopping", "Shopping", ExpenseType.PERSONAL),
        DefaultCategory("personal_other", "Other", ExpenseType.PERSONAL),
    )

    fun expenseCategoryEntities(businessId: Long, now: Long): List<ExpenseCategoryEntity> =
        expenseCategories.mapIndexed { index, c ->
            ExpenseCategoryEntity(
                businessId = businessId,
                name = c.name,
                type = c.type,
                systemKey = c.key,
                budgetGroup = c.group,
                sortOrder = index,
                createdAt = now,
            )
        }

    /**
     * Common salon services with *suggested* prices (PKR). Only added when the owner ticks
     * "Add common services" during setup; every price can be edited afterwards.
     */
    data class DefaultService(val name: String, val category: String, val priceUnits: Long, val minutes: Int)

    val services: List<DefaultService> = listOf(
        DefaultService("Hair Cut", "Hair", 500, 30),
        DefaultService("Kids Hair Cut", "Hair", 350, 20),
        DefaultService("Hair Styling", "Hair", 400, 20),
        DefaultService("Hair Wash", "Hair", 200, 10),
        DefaultService("Hair Color", "Color", 1500, 60),
        DefaultService("Beard", "Beard & Shave", 300, 15),
        DefaultService("Shaving", "Beard & Shave", 250, 15),
        DefaultService("Facial", "Skin Care", 1500, 45),
        DefaultService("Massage", "Spa", 800, 30),
    )

    fun serviceEntities(businessId: Long, now: Long): List<ServiceEntity> =
        services.mapIndexed { index, s ->
            ServiceEntity(
                businessId = businessId,
                name = s.name,
                category = s.category,
                priceMinor = s.priceUnits * 100,
                durationMinutes = s.minutes,
                sortOrder = index,
                createdAt = now,
                updatedAt = now,
            )
        }
}
