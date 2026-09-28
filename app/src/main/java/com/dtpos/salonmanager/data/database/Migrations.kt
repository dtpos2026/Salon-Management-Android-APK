package com.dtpos.salonmanager.data.database

import androidx.room.migration.Migration

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
    val ALL: Array<Migration> = arrayOf()
}
