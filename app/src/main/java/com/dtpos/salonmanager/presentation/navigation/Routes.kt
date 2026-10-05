package com.dtpos.salonmanager.presentation.navigation

import com.dtpos.salonmanager.domain.model.ExpenseType

/** String routes for Navigation Compose. Optional ids use -1 as "none". */
object Routes {
    const val DASHBOARD = "dashboard"
    const val SALES = "sales"
    const val CUSTOMERS = "customers"
    const val EXPENSES = "expenses"
    const val REPORTS = "reports"

    const val POS = "pos?customerId={customerId}&editSaleId={editSaleId}"
    fun pos(customerId: Long? = null) = "pos?customerId=${customerId ?: -1}&editSaleId=-1"
    /** Opens a completed receipt in the POS to correct it. */
    fun editSale(saleId: Long) = "pos?customerId=-1&editSaleId=$saleId"

    const val SALE_DETAIL = "sale/{saleId}?fresh={fresh}"
    fun saleDetail(saleId: Long, fresh: Boolean = false) = "sale/$saleId?fresh=$fresh"

    const val CUSTOMER_DETAIL = "customer/{customerId}"
    fun customerDetail(id: Long) = "customer/$id"

    const val CUSTOMER_EDIT = "customer-edit?customerId={customerId}"
    fun customerEdit(id: Long? = null) = "customer-edit?customerId=${id ?: -1}"

    const val SERVICES = "services"

    const val STAFF = "staff"
    const val STAFF_DETAIL = "staff/{staffId}"
    fun staffDetail(id: Long) = "staff/$id"
    const val STAFF_EDIT = "staff-edit?staffId={staffId}"
    fun staffEdit(id: Long? = null) = "staff-edit?staffId=${id ?: -1}"

    const val EXPENSE_EDIT = "expense-edit?expenseId={expenseId}&type={type}"
    fun expenseEdit(id: Long? = null, type: ExpenseType = ExpenseType.BUSINESS) = "expense-edit?expenseId=${id ?: -1}&type=${type.name}"
    const val EXPENSE_CATEGORIES = "expense-categories?type={type}"
    fun expenseCategories(type: ExpenseType) = "expense-categories?type=${type.name}"

    const val CASH = "cash"
    const val TARGETS = "targets"
    const val BUDGET = "budget"
    const val INSIGHTS = "insights"
    const val DUES = "dues"
    const val PROMOTIONS = "promotions"
    const val SUPPORT = "support"
    const val AI = "ai"
    const val TOKENS = "tokens"
    const val CLOSE_DAY = "close-day"
    const val MENU = "menu"
    const val PAYMENT_ACCOUNTS = "settings/accounts"

    const val SETTINGS = "settings"
    const val BUSINESS_PROFILE = "settings/business"
    const val RECEIPT_SETTINGS = "settings/receipt"
    const val PRINTER = "settings/printer"
    const val BACKUP = "settings/backup"
    const val SECURITY = "settings/security"
    const val LICENSE = "settings/license"
    const val ACCOUNT = "settings/account"
    const val PREFERENCES = "settings/preferences"
    const val ABOUT = "settings/about"

    val topLevel = listOf(DASHBOARD, SALES, CUSTOMERS, EXPENSES, REPORTS)
}
