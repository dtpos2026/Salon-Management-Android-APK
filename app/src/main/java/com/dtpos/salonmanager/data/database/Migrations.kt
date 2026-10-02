package com.dtpos.salonmanager.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Rules that keep salon data safe across app updates:
 *
 * 1. Bump [SalonDatabase.VERSION] and add a Migration(old, new) here for every schema change.
 * 2. Keep the exported schema JSON files in app/schemas under version control.
 * 3. Add a MigrationTestHelper test (see androidTest/MigrationTest.kt) for each migration.
 * 4. Never enable fallbackToDestructiveMigration().
 *
 * Example for a future version 2:
 *
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT")
 *     }
 * }
 */
object Migrations {
    /** Version 2: customer dues (udhaar / pending bills). Existing data is untouched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customer_dues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`businessId` INTEGER NOT NULL, `customerId` INTEGER, `customerName` TEXT NOT NULL, `customerPhone` TEXT, " +
                    "`amountMinor` INTEGER NOT NULL, `paidMinor` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`settledAt` INTEGER, `lastReminderAt` INTEGER)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_dues_businessId_settledAt` ON `customer_dues` (`businessId`, `settledAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_dues_customerId` ON `customer_dues` (`customerId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
