package com.dtpos.salonmanager

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dtpos.salonmanager.data.database.Migrations
import com.dtpos.salonmanager.data.database.SalonDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards salon data across app updates, using the schema JSON committed in app/schemas.
 *
 * - The database created from the committed schema of every version must open with the current
 *   code after running [Migrations.ALL]. Room checks the schema identity hash on open, so this
 *   also fails if an entity changed without bumping [SalonDatabase.VERSION] and exporting a new
 *   schema.
 * - For each new migration, add a test that inserts rows in the old version and reads them back
 *   after migrating (see the example in Migrations.kt).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SalonDatabase::class.java,
    )

    @Test
    fun everyExportedVersionOpensWithTheCurrentCode() {
        for (version in 1..SalonDatabase.VERSION) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase(testDb)
            helper.createDatabase(testDb, version).close()

            val db = Room.databaseBuilder(context, SalonDatabase::class.java, testDb)
                .addMigrations(*Migrations.ALL)
                .build()
            try {
                // Opening runs the migrations and Room's schema validation.
                db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sales").use {
                    it.moveToFirst()
                    assertEquals(0, it.getInt(0))
                }
            } finally {
                db.close()
                context.deleteDatabase(testDb)
            }
        }
    }

    /** Version 3 only adds a table and columns: old services and pending bills keep their values. */
    @Test
    fun migration2To3KeepsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                "INSERT INTO businesses (id, name, phone, address, logoPath, currencyCode, currencySymbol, receiptPrefix, " +
                    "receiptHeaderNote, receiptFooter, showLogoOnReceipt, showStaffOnReceipt, isSetupComplete, createdAt, updatedAt) " +
                    "VALUES (1, 'Royal Cuts', NULL, NULL, NULL, 'PKR', 'Rs.', 'SAL', NULL, NULL, 1, 1, 1, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO services (id, businessId, name, category, priceMinor, durationMinutes, isActive, sortOrder, createdAt, updatedAt) " +
                    "VALUES (7, 1, 'Hair Cut', 'Hair', 50000, 30, 1, 0, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO customer_dues (id, businessId, customerId, customerName, customerPhone, amountMinor, paidMinor, note, createdAt, settledAt, lastReminderAt) " +
                    "VALUES (3, 1, NULL, 'Ali', NULL, 80000, 30000, 'old bill', 1, NULL, NULL)",
            )
        }
        helper.runMigrationsAndValidate(testDb, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT name, priceMinor, imagePath, boldName, boldPrice FROM services WHERE id = 7").use {
                it.moveToFirst()
                assertEquals("Hair Cut", it.getString(0))
                assertEquals(50000L, it.getLong(1))
                assertEquals(true, it.isNull(2))
                assertEquals(0, it.getInt(3))
                assertEquals(0, it.getInt(4))
            }
            db.query("SELECT amountMinor, paidMinor, saleId FROM customer_dues WHERE id = 3").use {
                it.moveToFirst()
                assertEquals(80000L, it.getLong(0))
                assertEquals(30000L, it.getLong(1))
                assertEquals(true, it.isNull(2))
            }
            db.query("SELECT COUNT(*) FROM payment_accounts").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
        context.deleteDatabase(testDb)
    }

    /** Version 4 adds the udhaar payment ledger; old cash collections become its first rows. */
    @Test
    fun migration3To4CopiesCashUdhaarIntoTheLedger() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                "INSERT INTO businesses (id, name, phone, address, logoPath, currencyCode, currencySymbol, receiptPrefix, " +
                    "receiptHeaderNote, receiptFooter, showLogoOnReceipt, showStaffOnReceipt, isSetupComplete, createdAt, updatedAt) " +
                    "VALUES (1, 'Royal Cuts', NULL, NULL, NULL, 'PKR', 'Rs.', 'SAL', NULL, NULL, 1, 1, 1, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO customer_dues (id, businessId, customerId, customerName, customerPhone, amountMinor, paidMinor, note, createdAt, settledAt, lastReminderAt, saleId) " +
                    "VALUES (5, 1, NULL, 'Ali', NULL, 80000, 30000, NULL, 1, NULL, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO cash_transactions (id, businessId, type, amountMinor, txDate, referenceType, referenceId, note, createdAt) " +
                    "VALUES (9, 1, 'CASH_IN', 30000, 20000, 'DUE', 5, 'Ali', 1700000000000)",
            )
            db.execSQL(
                "INSERT INTO cash_transactions (id, businessId, type, amountMinor, txDate, referenceType, referenceId, note, createdAt) " +
                    "VALUES (10, 1, 'SALE', 50000, 20000, 'SALE', 2, 'SAL-000002', 1700000000000)",
            )
        }
        helper.runMigrationsAndValidate(testDb, 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT dueId, amountMinor, paymentMethod, paymentAccountId, businessDate FROM due_payments").use {
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals(5L, it.getLong(0))
                assertEquals(30000L, it.getLong(1))
                assertEquals("CASH", it.getString(2))
                assertEquals(true, it.isNull(3))
                assertEquals(20000L, it.getLong(4))
            }
            db.query("SELECT paidMinor FROM customer_dues WHERE id = 5").use {
                it.moveToFirst()
                assertEquals(30000L, it.getLong(0))
            }
        }
        context.deleteDatabase(testDb)
    }
}
