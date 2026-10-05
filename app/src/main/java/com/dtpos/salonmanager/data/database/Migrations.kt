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
    /** Version 2: customer dues (udhaar / pending bills) and tokens / bookings. Existing data is untouched. */
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
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`businessId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `tokenNumber` INTEGER NOT NULL, " +
                    "`timeMinutes` INTEGER, `customerId` INTEGER, `customerName` TEXT NOT NULL, `customerPhone` TEXT, " +
                    "`service` TEXT, `staffName` TEXT, `note` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookings_businessId_dateEpochDay_tokenNumber` " +
                    "ON `bookings` (`businessId`, `dateEpochDay`, `tokenNumber`)",
            )
        }
    }

    /**
     * Version 3: payment accounts (JazzCash / EasyPaisa / bank), sale account + credit (udhaar)
     * + edit time, service photo and bold printing, and the sale a due came from. Only new
     * tables and columns; every existing row keeps its values (new columns start empty / 0).
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `payment_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`businessId` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `accountTitle` TEXT, " +
                    "`accountNumber` TEXT, `isActive` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`businessId`) REFERENCES `businesses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_accounts_businessId_isActive` ON `payment_accounts` (`businessId`, `isActive`)")
            db.execSQL("ALTER TABLE `sales` ADD COLUMN `paymentAccountId` INTEGER")
            db.execSQL("ALTER TABLE `sales` ADD COLUMN `paymentAccountName` TEXT")
            db.execSQL("ALTER TABLE `sales` ADD COLUMN `creditMinor` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `sales` ADD COLUMN `editedAt` INTEGER")
            db.execSQL("ALTER TABLE `services` ADD COLUMN `imagePath` TEXT")
            db.execSQL("ALTER TABLE `services` ADD COLUMN `boldName` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `services` ADD COLUMN `boldPrice` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `customer_dues` ADD COLUMN `saleId` INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
