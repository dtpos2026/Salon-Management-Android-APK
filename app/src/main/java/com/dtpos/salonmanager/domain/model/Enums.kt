package com.dtpos.salonmanager.domain.model

// Enum names are persisted in the database and backups. Never rename or remove a constant;
// only add new ones (and handle them in the UI string mappers).

enum class PaymentMethod { CASH, CARD, BANK, OTHER }

enum class Gender { UNSPECIFIED, MALE, FEMALE, OTHER }

enum class StaffRole { BARBER, HAIRDRESSER, BEAUTICIAN, RECEPTIONIST, OTHER }

enum class SalaryType {
    FIXED,
    COMMISSION,
    FIXED_PLUS_COMMISSION;

    val hasFixed: Boolean get() = this == FIXED || this == FIXED_PLUS_COMMISSION
    val hasCommission: Boolean get() = this == COMMISSION || this == FIXED_PLUS_COMMISSION
}

/** Business and personal/household money is kept strictly separate for honest profit figures. */
enum class ExpenseType { BUSINESS, PERSONAL }

enum class StaffPaymentType { SALARY, ADVANCE, COMMISSION, BONUS, OTHER }

enum class SaleStatus { COMPLETED, VOIDED }

/** Token / booking queue states. */
enum class BookingStatus { WAITING, SERVING, DONE, CANCELLED }

enum class DiscountType { AMOUNT, PERCENT }

/** Movements in the physical cash drawer. Amounts are signed: + into the drawer, - out of it. */
enum class CashTxType { SALE, SALE_VOID, EXPENSE, STAFF_PAYMENT, CASH_IN, CASH_OUT }

enum class CashSessionStatus { OPEN, CLOSED }

enum class TargetPeriod { DAILY, WEEKLY, MONTHLY }

/** Smart budget lines (monthly). Expense categories can be linked to a group for tracking. */
enum class BudgetGroup { BUSINESS_EXPENSES, HOUSEHOLD, CHILDREN, FOOD, SAVINGS }
