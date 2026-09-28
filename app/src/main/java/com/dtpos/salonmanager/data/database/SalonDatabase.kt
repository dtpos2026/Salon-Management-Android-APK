package com.dtpos.salonmanager.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dtpos.salonmanager.data.database.dao.BusinessDao
import com.dtpos.salonmanager.data.database.dao.CashDao
import com.dtpos.salonmanager.data.database.dao.CustomerDao
import com.dtpos.salonmanager.data.database.dao.ExpenseDao
import com.dtpos.salonmanager.data.database.dao.LicenseDao
import com.dtpos.salonmanager.data.database.dao.SaleDao
import com.dtpos.salonmanager.data.database.dao.ServiceDao
import com.dtpos.salonmanager.data.database.dao.SettingsDao
import com.dtpos.salonmanager.data.database.dao.StaffDao
import com.dtpos.salonmanager.data.database.dao.TargetDao
import com.dtpos.salonmanager.data.database.entities.BudgetEntity
import com.dtpos.salonmanager.data.database.entities.BusinessEntity
import com.dtpos.salonmanager.data.database.entities.CashSessionEntity
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.data.database.entities.ExpenseCategoryEntity
import com.dtpos.salonmanager.data.database.entities.ExpenseEntity
import com.dtpos.salonmanager.data.database.entities.LicenseEntity
import com.dtpos.salonmanager.data.database.entities.ReceiptSequenceEntity
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.database.entities.SaleItemEntity
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.data.database.entities.SettingEntity
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.entities.StaffPaymentEntity
import com.dtpos.salonmanager.data.database.entities.TargetEntity
import com.dtpos.salonmanager.data.database.entities.VisitEntity

@Database(
    entities = [
        BusinessEntity::class,
        ReceiptSequenceEntity::class,
        TargetEntity::class,
        BudgetEntity::class,
        SettingEntity::class,
        LicenseEntity::class,
        CustomerEntity::class,
        ServiceEntity::class,
        StaffEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        VisitEntity::class,
        ExpenseCategoryEntity::class,
        ExpenseEntity::class,
        StaffPaymentEntity::class,
        CashSessionEntity::class,
        CashTransactionEntity::class,
    ],
    version = SalonDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SalonDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun settingsDao(): SettingsDao
    abstract fun licenseDao(): LicenseDao
    abstract fun targetDao(): TargetDao
    abstract fun customerDao(): CustomerDao
    abstract fun serviceDao(): ServiceDao
    abstract fun staffDao(): StaffDao
    abstract fun saleDao(): SaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun cashDao(): CashDao

    companion object {
        const val NAME = "salon.db"
        const val VERSION = 1

        /**
         * No destructive fallback is configured on purpose: if a migration is ever missing the
         * app fails loudly in testing instead of silently wiping a salon's data.
         */
        fun build(context: Context, name: String = NAME): SalonDatabase =
            Room.databaseBuilder(context.applicationContext, SalonDatabase::class.java, name)
                .addMigrations(*Migrations.ALL)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        fun inMemory(context: Context): SalonDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, SalonDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
