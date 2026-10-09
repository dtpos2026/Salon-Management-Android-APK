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
    fun `booking for a saved customer with several services, then a quick walk-in token`() = runTest {
        env.setUp()
        val customerId = (
            env.customers.save(
                com.dtpos.salonmanager.data.repository.CustomerInput("Usman Ali", "0300-7654321", com.dtpos.salonmanager.domain.model.Gender.MALE, null, null, null),
            ) as DataResult.Success
            ).data

        // The name and phone fields find the customer by name or by part of the number.
        assertEquals(listOf(customerId), env.customers.search("usm", 5).first().map { it.customer.id })
        assertEquals(listOf(customerId), env.customers.search("0300-765", 5).first().map { it.customer.id })
        assertTrue(env.customers.search("Zz", 5).first().isEmpty())

        val services = com.dtpos.salonmanager.presentation.tokens.TokenDraft.joinServices(listOf("Hair cut", "Beard", "hair cut", " Facial "))
        assertEquals("Hair cut, Beard, Facial", services)
        val booked = (bookings.issue(today.plusDays(1), "Usman Ali", "0300-7654321", services, 17 * 60, customerId = customerId) as DataResult.Success).data
        val saved = bookings.observeDay(today.plusDays(1)).first().single()
        assertEquals(booked.id, saved.id)
        assertEquals(customerId, saved.customerId)
        assertEquals("Usman Ali", saved.customerName)
        assertEquals("0300-7654321", saved.customerPhone)
        assertEquals("Hair cut, Beard, Facial", saved.service)
        assertEquals(17 * 60, saved.timeMinutes)
        assertEquals(1, saved.tokenNumber)

        // A walk-in today stays separate (no time, its own day's numbers), even with nothing typed.
        val walkIn = (bookings.issue(today, "", "", "", null) as DataResult.Success).data
        assertEquals(1, walkIn.tokenNumber)
        assertNull(walkIn.timeMinutes)
        assertNull(walkIn.service)
        assertNull(walkIn.customerPhone)
        assertEquals("#1", walkIn.customerName)
        assertEquals(1, bookings.observeDay(today.plusDays(1)).first().size)
    }

    @Test
    fun `invalid times are refused`() = runTest {
        env.setUp()
        assertTrue(bookings.issue(today, "Ali", null, null, timeMinutes = 25 * 60) is DataResult.Failure)
    }
}
