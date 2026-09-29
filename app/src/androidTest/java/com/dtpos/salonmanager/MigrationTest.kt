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
}
