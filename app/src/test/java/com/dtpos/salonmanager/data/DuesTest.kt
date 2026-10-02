package com.dtpos.salonmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.data.repository.DueRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuesTest {
    private val env = TestEnvironment(ApplicationProvider.getApplicationContext())
    private val dues = DueRepository(env.db, env.businessId, clock = { 1_000L })

    @Test
    fun `partial and full payments settle a pending bill`() = runTest {
        env.setUp()
        val id = (dues.add(null, "Ali", "0300-1234567", 150_000, "Bridal package") as DataResult.Success).data
        assertEquals(150_000L, dues.observeOpenTotal().first())

        assertEquals(DataResult.Success(50_000L), dues.recordPayment(id, 100_000))
        assertEquals(50_000L, dues.observeOpenTotal().first())

        dues.markReminded(id)
        assertNotNull(dues.observeOpen().first().single().lastReminderAt)

        // Paying more than the balance only settles it.
        assertEquals(DataResult.Success(0L), dues.recordPayment(id, 90_000))
        assertTrue(dues.observeOpen().first().isEmpty())
        assertEquals(150_000L, dues.observeSettled().first().single().paidMinor)
        assertEquals(0L, dues.observeOpenTotal().first())
    }

    @Test
    fun `invalid bills are refused`() = runTest {
        env.setUp()
        assertTrue(dues.add(null, " ", null, 100, null) is DataResult.Failure)
        assertTrue(dues.add(null, "Ali", null, 0, null) is DataResult.Failure)
    }
}
