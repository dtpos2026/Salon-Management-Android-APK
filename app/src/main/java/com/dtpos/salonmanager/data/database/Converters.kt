package com.dtpos.salonmanager.data.database

import androidx.room.TypeConverter
import com.dtpos.salonmanager.domain.model.AccountKind
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.CashSessionStatus
import com.dtpos.salonmanager.domain.model.BookingStatus
import com.dtpos.salonmanager.domain.model.CashTxType
import com.dtpos.salonmanager.domain.model.DiscountType
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.SaleStatus
import com.dtpos.salonmanager.domain.model.StaffPaymentType
import com.dtpos.salonmanager.domain.model.StaffRole
import com.dtpos.salonmanager.domain.model.TargetPeriod

/**
 * Enums are stored by name (TEXT) so the database stays readable in exports and robust
 * against enum reordering. Raw SQL in DAOs compares against these names (e.g. 'COMPLETED').
 */
class Converters {
    @TypeConverter fun fromPaymentMethod(value: PaymentMethod): String = value.name
    @TypeConverter fun toPaymentMethod(value: String): PaymentMethod = enumOrDefault(value, PaymentMethod.OTHER)

    @TypeConverter fun fromAccountKind(value: AccountKind): String = value.name
    @TypeConverter fun toAccountKind(value: String): AccountKind = enumOrDefault(value, AccountKind.OTHER)

    @TypeConverter fun fromGender(value: Gender): String = value.name
    @TypeConverter fun toGender(value: String): Gender = enumOrDefault(value, Gender.UNSPECIFIED)

    @TypeConverter fun fromStaffRole(value: StaffRole): String = value.name
    @TypeConverter fun toStaffRole(value: String): StaffRole = enumOrDefault(value, StaffRole.OTHER)

    @TypeConverter fun fromSalaryType(value: SalaryType): String = value.name
    @TypeConverter fun toSalaryType(value: String): SalaryType = enumOrDefault(value, SalaryType.FIXED)

    @TypeConverter fun fromExpenseType(value: ExpenseType): String = value.name
    @TypeConverter fun toExpenseType(value: String): ExpenseType = enumOrDefault(value, ExpenseType.BUSINESS)

    @TypeConverter fun fromStaffPaymentType(value: StaffPaymentType): String = value.name
    @TypeConverter fun toStaffPaymentType(value: String): StaffPaymentType = enumOrDefault(value, StaffPaymentType.OTHER)

    @TypeConverter fun fromBookingStatus(value: BookingStatus): String = value.name
    @TypeConverter fun toBookingStatus(value: String): BookingStatus = enumOrDefault(value, BookingStatus.WAITING)

    @TypeConverter fun fromSaleStatus(value: SaleStatus): String = value.name
    @TypeConverter fun toSaleStatus(value: String): SaleStatus = enumOrDefault(value, SaleStatus.COMPLETED)

    @TypeConverter fun fromDiscountType(value: DiscountType): String = value.name
    @TypeConverter fun toDiscountType(value: String): DiscountType = enumOrDefault(value, DiscountType.AMOUNT)

    @TypeConverter fun fromCashTxType(value: CashTxType): String = value.name
    @TypeConverter fun toCashTxType(value: String): CashTxType = enumOrDefault(value, CashTxType.CASH_IN)

    @TypeConverter fun fromCashSessionStatus(value: CashSessionStatus): String = value.name
    @TypeConverter fun toCashSessionStatus(value: String): CashSessionStatus = enumOrDefault(value, CashSessionStatus.OPEN)

    @TypeConverter fun fromTargetPeriod(value: TargetPeriod): String = value.name
    @TypeConverter fun toTargetPeriod(value: String): TargetPeriod = enumOrDefault(value, TargetPeriod.MONTHLY)

    @TypeConverter fun fromBudgetGroup(value: BudgetGroup): String = value.name
    @TypeConverter fun toBudgetGroup(value: String): BudgetGroup = enumOrDefault(value, BudgetGroup.HOUSEHOLD)

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
