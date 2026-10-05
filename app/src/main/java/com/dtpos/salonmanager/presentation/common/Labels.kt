package com.dtpos.salonmanager.presentation.common

import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.validation.ValidationError
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.domain.calc.CartProblem
import com.dtpos.salonmanager.domain.license.LicensePlan
import com.dtpos.salonmanager.domain.license.LicenseStatus
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.PeriodPreset
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import com.dtpos.salonmanager.domain.model.StaffRole
import com.dtpos.salonmanager.domain.model.TargetPeriod
import com.dtpos.salonmanager.services.backup.BackupError
import com.dtpos.salonmanager.services.license.ActivationError
import com.dtpos.salonmanager.services.printer.PrinterError

/* Maps domain values to translatable string resources. */

val PaymentMethod.labelRes: Int
    get() = when (this) {
        PaymentMethod.CASH -> R.string.payment_cash
        PaymentMethod.CARD -> R.string.payment_card
        PaymentMethod.BANK -> R.string.payment_bank
        PaymentMethod.OTHER -> R.string.payment_other
    }

val Gender.labelRes: Int
    get() = when (this) {
        Gender.UNSPECIFIED -> R.string.gender_unspecified
        Gender.MALE -> R.string.gender_male
        Gender.FEMALE -> R.string.gender_female
        Gender.OTHER -> R.string.gender_other
    }

val StaffRole.labelRes: Int
    get() = when (this) {
        StaffRole.BARBER -> R.string.role_barber
        StaffRole.HAIRDRESSER -> R.string.role_hairdresser
        StaffRole.BEAUTICIAN -> R.string.role_beautician
        StaffRole.RECEPTIONIST -> R.string.role_receptionist
        StaffRole.OTHER -> R.string.role_other
        StaffRole.OWNER -> R.string.role_owner
    }

val com.dtpos.salonmanager.domain.model.AccountKind.labelRes: Int
    get() = when (this) {
        com.dtpos.salonmanager.domain.model.AccountKind.WALLET -> R.string.account_kind_wallet
        com.dtpos.salonmanager.domain.model.AccountKind.BANK -> R.string.account_kind_bank
        com.dtpos.salonmanager.domain.model.AccountKind.CARD -> R.string.account_kind_card
        com.dtpos.salonmanager.domain.model.AccountKind.OTHER -> R.string.account_kind_other
    }

val SalaryType.labelRes: Int
    get() = when (this) {
        SalaryType.FIXED -> R.string.salary_fixed
        SalaryType.COMMISSION -> R.string.salary_commission
        SalaryType.FIXED_PLUS_COMMISSION -> R.string.salary_fixed_plus_commission
    }

val StaffPaymentType.labelRes: Int
    get() = when (this) {
        StaffPaymentType.SALARY -> R.string.staff_pay_salary
        StaffPaymentType.ADVANCE -> R.string.staff_pay_advance
        StaffPaymentType.COMMISSION -> R.string.staff_pay_commission
        StaffPaymentType.BONUS -> R.string.staff_pay_bonus
        StaffPaymentType.OTHER -> R.string.staff_pay_other
    }

val ExpenseType.labelRes: Int
    get() = when (this) {
        ExpenseType.BUSINESS -> R.string.expense_type_business
        ExpenseType.PERSONAL -> R.string.expense_type_personal
    }

val CashTxType.labelRes: Int
    get() = when (this) {
        CashTxType.SALE -> R.string.cash_tx_sale
        CashTxType.SALE_VOID -> R.string.cash_tx_void
        CashTxType.EXPENSE -> R.string.cash_tx_expense
        CashTxType.STAFF_PAYMENT -> R.string.cash_tx_staff
        CashTxType.CASH_IN -> R.string.cash_tx_in
        CashTxType.CASH_OUT -> R.string.cash_tx_out
    }

val TargetPeriod.labelRes: Int
    get() = when (this) {
        TargetPeriod.DAILY -> R.string.target_daily
        TargetPeriod.WEEKLY -> R.string.target_weekly
        TargetPeriod.MONTHLY -> R.string.target_monthly
    }

val BudgetGroup.labelRes: Int
    get() = when (this) {
        BudgetGroup.BUSINESS_EXPENSES -> R.string.budget_business_expenses
        BudgetGroup.HOUSEHOLD -> R.string.budget_household
        BudgetGroup.CHILDREN -> R.string.budget_children
        BudgetGroup.FOOD -> R.string.budget_food
        BudgetGroup.SAVINGS -> R.string.budget_savings
    }

val PeriodPreset.labelRes: Int
    get() = when (this) {
        PeriodPreset.TODAY -> R.string.period_today
        PeriodPreset.YESTERDAY -> R.string.period_yesterday
        PeriodPreset.THIS_WEEK -> R.string.period_this_week
        PeriodPreset.LAST_WEEK -> R.string.period_last_week
        PeriodPreset.THIS_MONTH -> R.string.period_this_month
        PeriodPreset.LAST_MONTH -> R.string.period_last_month
        PeriodPreset.THIS_YEAR -> R.string.period_this_year
        PeriodPreset.CUSTOM -> R.string.period_custom
    }

val LicensePlan.labelRes: Int
    get() = when (this) {
        LicensePlan.TRIAL -> R.string.plan_trial
        LicensePlan.MONTH_1 -> R.string.plan_month_1
        LicensePlan.MONTH_3 -> R.string.plan_month_3
        LicensePlan.MONTH_6 -> R.string.plan_month_6
        LicensePlan.YEAR_1 -> R.string.plan_year_1
        LicensePlan.CUSTOM -> R.string.plan_custom
        LicensePlan.LIFETIME -> R.string.plan_lifetime
    }

val LicenseStatus.labelRes: Int
    get() = when (this) {
        LicenseStatus.NOT_ENFORCED -> R.string.license_status_owner
        LicenseStatus.TRIAL -> R.string.license_status_trial
        LicenseStatus.TRIAL_EXPIRED -> R.string.license_status_trial_expired
        LicenseStatus.ACTIVE -> R.string.license_status_active
        LicenseStatus.EXPIRING_SOON -> R.string.license_status_expiring
        LicenseStatus.EXPIRED -> R.string.license_status_expired
        LicenseStatus.INVALID -> R.string.license_status_invalid
        LicenseStatus.CLOCK_TAMPERED -> R.string.license_status_clock
    }

val ValidationError.messageRes: Int
    get() = when (this) {
        ValidationError.REQUIRED -> R.string.error_required
        ValidationError.TOO_LONG -> R.string.error_too_long
        ValidationError.INVALID_PHONE -> R.string.error_invalid_phone
        ValidationError.INVALID_AMOUNT -> R.string.error_invalid_amount
        ValidationError.AMOUNT_MUST_BE_POSITIVE -> R.string.error_amount_positive
        ValidationError.INVALID_PERCENT -> R.string.error_invalid_percent
        ValidationError.INVALID_QUANTITY -> R.string.error_invalid_quantity
        ValidationError.DISCOUNT_EXCEEDS_AMOUNT -> R.string.error_discount_exceeds
        ValidationError.DUPLICATE_NAME -> R.string.error_duplicate_name
        ValidationError.DUPLICATE_PHONE -> R.string.error_duplicate_phone
        ValidationError.INVALID_PREFIX -> R.string.error_invalid_prefix
        ValidationError.PIN_FORMAT -> R.string.error_pin_format
        ValidationError.PASSWORD_TOO_SHORT -> R.string.error_password_short
        ValidationError.CONFIRMATION_MISMATCH -> R.string.error_confirmation_mismatch
        ValidationError.INVALID_DURATION -> R.string.error_invalid_duration
    }

val DataError.messageRes: Int
    get() = when (this) {
        DataError.DUPLICATE_NAME -> R.string.error_duplicate_name
        DataError.DUPLICATE_PHONE -> R.string.error_duplicate_phone
        DataError.NOT_FOUND -> R.string.error_not_found
        DataError.IN_USE -> R.string.error_in_use
        DataError.INVALID -> R.string.error_invalid_data
        DataError.READ_ONLY -> R.string.error_read_only
        DataError.ALREADY_CLOSED -> R.string.error_already_closed
        DataError.STORAGE -> R.string.error_storage
    }

val CartProblem.messageRes: Int
    get() = when (this) {
        CartProblem.EMPTY_CART -> R.string.pos_error_empty
        CartProblem.INVALID_QUANTITY -> R.string.error_invalid_quantity
        CartProblem.INVALID_PRICE -> R.string.pos_error_price
        CartProblem.LINE_DISCOUNT_EXCEEDS_LINE -> R.string.error_discount_exceeds
        CartProblem.SALE_DISCOUNT_EXCEEDS_TOTAL -> R.string.error_discount_exceeds
        CartProblem.INVALID_DISCOUNT_PERCENT -> R.string.error_invalid_percent
        CartProblem.NEGATIVE_DISCOUNT -> R.string.error_invalid_amount
        CartProblem.TENDERED_LESS_THAN_TOTAL -> R.string.pos_error_tendered
    }

val PrinterError.messageRes: Int
    get() = when (this) {
        PrinterError.BLUETOOTH_UNSUPPORTED -> R.string.printer_error_unsupported
        PrinterError.BLUETOOTH_OFF -> R.string.printer_error_off
        PrinterError.PERMISSION_DENIED -> R.string.printer_error_permission
        PrinterError.NO_PRINTER_SELECTED -> R.string.printer_error_none
        PrinterError.DEVICE_NOT_FOUND -> R.string.printer_error_not_found
        PrinterError.CONNECTION_FAILED -> R.string.printer_error_connect
        PrinterError.WRITE_FAILED -> R.string.printer_error_write
        PrinterError.NOTHING_TO_PRINT -> R.string.printer_error_nothing
    }

val BackupError.messageRes: Int
    get() = when (this) {
        BackupError.NOT_A_BACKUP -> R.string.backup_error_not_backup
        BackupError.WRONG_PASSWORD -> R.string.backup_error_password
        BackupError.CORRUPT -> R.string.backup_error_corrupt
        BackupError.NEWER_APP_VERSION -> R.string.backup_error_newer
        BackupError.READ_FAILED -> R.string.backup_error_read
        BackupError.WRITE_FAILED -> R.string.backup_error_write
        BackupError.RESTORE_FAILED -> R.string.backup_error_restore
    }

val ActivationError.messageRes: Int
    get() = when (this) {
        ActivationError.MALFORMED -> R.string.license_error_malformed
        ActivationError.BAD_SIGNATURE -> R.string.license_error_signature
        ActivationError.WRONG_DEVICE -> R.string.license_error_device
        ActivationError.EXPIRED -> R.string.license_error_expired
        ActivationError.NOT_CONFIGURED -> R.string.license_error_not_configured
    }

/** Built-in expense categories are stored in English and translated by their system key. */
fun expenseCategoryLabelRes(systemKey: String?): Int? = when (systemKey) {
    "electricity" -> R.string.cat_electricity
    "gas" -> R.string.cat_gas
    "water" -> R.string.cat_water
    "rent" -> R.string.cat_rent
    "internet" -> R.string.cat_internet
    "supplies" -> R.string.cat_supplies
    "cosmetics" -> R.string.cat_cosmetics
    "equipment" -> R.string.cat_equipment
    "maintenance" -> R.string.cat_maintenance
    "marketing" -> R.string.cat_marketing
    "staff_expenses" -> R.string.cat_staff_expenses
    "business_other", "personal_other" -> R.string.cat_other
    "food" -> R.string.cat_food
    "children" -> R.string.cat_children
    "education" -> R.string.cat_education
    "medical" -> R.string.cat_medical
    "home" -> R.string.cat_home
    "transport" -> R.string.cat_transport
    "shopping" -> R.string.cat_shopping
    else -> null
}
