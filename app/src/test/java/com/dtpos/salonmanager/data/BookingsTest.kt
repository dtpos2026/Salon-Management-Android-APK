package com.dtpos.salonmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dtpos.salonmanager.data.repository.BookingRepository
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.BookingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BookingsTest {
    private val env = TestEnvironment(ApplicationProvider.getApplicationContext())
    private val bookings = BookingRepository(env.db, env.businessId, clock = { 1_000L })
    private val today = LocalDate.of(2026, 10, 2)

    private suspend fun token(day: LocalDate, name: String, time: Int? = null) =
        (bookings.issue(day, name, "0300-1234567", "Hair cut", time) as DataResult.Success).data

    @Test
    fun `tokens count from 1 each day and the queue is served in order`() = runTest {
        env.setUp()
        assertEquals(1, token(today, "Ali").tokenNumber)
        assertEquals(2, token(today, "Bilal").tokenNumber)
        // A booking for 5 PM shares the day's numbers and is served before walk-ins.
        assertEquals(3, token(today, "Sara", time = 17 * 60).tokenNumber)
        assertEquals(1, token(today.plusDays(1), "Usman", time = 11 * 60).tokenNumber)

        var queue = bookings.observeDay(today).first()
        assertEquals(listOf("Sara", "Ali", "Bilal"), queue.map { it.customerName })

        assertEquals("Sara", bookings.callNext(queue)?.customerName)
        queue = bookings.observeDay(today).first()
        assertEquals(BookingStatus.SERVING, queue.first().status)

        // Calling next finishes the current customer.
        assertEquals("Ali", bookings.callNext(queue)?.customerName)
        queue = bookings.observeDay(today).first()
        assertEquals(BookingStatus.DONE, queue.single { it.customerName == "Sara" }.status)

        bookings.setStatus(queue.single { it.customerName == "Bilal" }.id, BookingStatus.CANCELLED)
        queue = bookings.observeDay(today).first()
        assertEquals(BookingStatus.SERVING, queue.first().status)
        bookings.callNext(queue)
        assertNull(bookings.callNext(bookings.observeDay(today).first()))

        assertEquals(listOf("Usman"), bookings.observeUpcoming(today).first().map { it.customerName })
    }

    @Test
    fun `invalid times are refused`() = runTest {
        env.setUp()
        assertTrue(bookings.issue(today, "Ali", null, null, timeMinutes = 25 * 60) is DataResult.Failure)
    }
}
