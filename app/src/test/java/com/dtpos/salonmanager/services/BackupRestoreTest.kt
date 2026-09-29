package com.dtpos.salonmanager.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.TestEnvironment
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.repository.CustomerInput
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.DateRange
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.services.backup.BackupError
import com.dtpos.salonmanager.services.backup.BackupInspection
import com.dtpos.salonmanager.services.backup.BackupManager
import com.dtpos.salonmanager.services.branding.LogoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRestoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun manager(env: TestEnvironment, holder: Array<SalonDatabase?>) = BackupManager(
        context = context,
        database = { holder[0]!! },
        closeDatabase = { holder[0]?.close(); holder[0] = null },
        settings = env.settings,
        logoStore = LogoStore(context),
    )

    @Test
    fun `backup and restore round trip replaces data and keeps a safety copy`() = runTest {
        val holder = arrayOf<SalonDatabase?>(SalonDatabase.build(context))
        val env = TestEnvironment(context, holder[0]!!).setUp()
        val today = DateTimeUtils.today()
        env.sales.completeSale(env.request(listOf(env.line(env.service("Hair Cut")))), env.at(today))
        val backup = manager(env, holder).createBackupBytes(password = null)

        // Data created after the backup must disappear after restoring it.
        env.customers.save(CustomerInput("After Backup", null, Gender.UNSPECIFIED, null, null, null))

        val manager = manager(env, holder)
        val inspection = manager.prepareRestore(backup, password = null)
        assertTrue(inspection is BackupInspection.Ready)
        assertNull(manager.applyRestore((inspection as BackupInspection.Ready).prepared))
        assertNull(holder[0]) // closed by the restore

        val reopened = SalonDatabase.build(context)
        try {
            val restored = TestEnvironment(context, reopened)
            assertEquals(1, restored.sales.observeSummary(DateRange.single(today)).first().saleCount)
            assertTrue(restored.customers.getAll().none { it.name == "After Backup" })
            assertEquals("Royal Barber Shop", restored.business.getProfile()!!.name)
        } finally {
            reopened.close()
        }
        assertTrue(manager.localBackups().any { it.file.name.contains("before-restore") })
    }

    @Test
    fun `encrypted backups need the right password and junk is rejected`() = runTest {
        val holder = arrayOf<SalonDatabase?>(SalonDatabase.build(context))
        val env = TestEnvironment(context, holder[0]!!).setUp()
        val manager = manager(env, holder)
        val encrypted = manager.createBackupBytes("secret".toCharArray())

        assertEquals(BackupInspection.NeedsPassword, manager.prepareRestore(encrypted, null))
        assertEquals(BackupInspection.Invalid(BackupError.WRONG_PASSWORD), manager.prepareRestore(encrypted, "nope".toCharArray()))
        assertTrue(manager.prepareRestore(encrypted, "secret".toCharArray()) is BackupInspection.Ready)
        assertEquals(BackupInspection.Invalid(BackupError.NOT_A_BACKUP), manager.prepareRestore("hello".toByteArray(), null))
        holder[0]?.close()
    }

    @Test
    fun `automatic backups run once per day`() = runTest {
        val holder = arrayOf<SalonDatabase?>(SalonDatabase.build(context))
        val env = TestEnvironment(context, holder[0]!!).setUp()
        val manager = manager(env, holder)
        val before = manager.localBackups().size
        manager.runAutoBackupIfDue()
        manager.runAutoBackupIfDue()
        assertEquals(before + 1, manager.localBackups().size)
        assertTrue(env.customers.save(CustomerInput("Still works", null, Gender.UNSPECIFIED, null, null, null)) is DataResult.Success)
        holder[0]?.close()
    }
}
