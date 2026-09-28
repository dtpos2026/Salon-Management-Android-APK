package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.domain.insights.BusinessSnapshot
import com.dtpos.salonmanager.domain.insights.CompositeInsightEngine
import com.dtpos.salonmanager.domain.insights.Insight
import com.dtpos.salonmanager.domain.insights.InsightEngine
import com.dtpos.salonmanager.domain.insights.NamedAmount
import com.dtpos.salonmanager.domain.insights.RuleBasedInsightEngine
import com.dtpos.salonmanager.domain.insights.WeekdayStat
import com.dtpos.salonmanager.services.backup.BackupCrypto
import com.dtpos.salonmanager.services.backup.BackupCryptoException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class BackupCryptoAndInsightsTest {

    @Test
    fun `encrypted backup round trip`() {
        val data = ByteArray(10_000) { (it % 251).toByte() }
        val encrypted = BackupCrypto.encrypt(data, "s3cret!".toCharArray(), iterations = 2_000)
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(BackupCrypto.isEncrypted(data))
        assertArrayEquals(data, BackupCrypto.decrypt(encrypted, "s3cret!".toCharArray()))
    }

    @Test
    fun `wrong password and tampering are detected`() {
        val encrypted = BackupCrypto.encrypt("hello".toByteArray(), "right".toCharArray(), iterations = 2_000)
        try {
            BackupCrypto.decrypt(encrypted, "wrong".toCharArray())
            fail("expected failure")
        } catch (e: BackupCryptoException) {
            assertEquals(BackupCryptoException.Reason.WRONG_PASSWORD, e.reason)
        }
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        try {
            BackupCrypto.decrypt(tampered, "right".toCharArray())
            fail("expected failure")
        } catch (e: BackupCryptoException) {
            assertEquals(BackupCryptoException.Reason.WRONG_PASSWORD, e.reason)
        }
    }

    private fun rs(units: Long) = units * 100

    private fun snapshot(
        today: LocalDate = LocalDate.of(2026, 9, 11),
        target: Long = rs(500_000),
        sales: Long = rs(150_000),
        previousSales: Long = rs(120_000),
        expenses: Long = rs(40_000),
        previousExpenses: Long = rs(30_000),
    ) = BusinessSnapshot(
        today = today,
        monthlyTargetMinor = target,
        monthToDateSalesMinor = sales,
        previousMonthSamePeriodSalesMinor = previousSales,
        monthToDateBusinessExpensesMinor = expenses,
        previousMonthSamePeriodBusinessExpensesMinor = previousExpenses,
        monthToDateStaffPaymentsMinor = rs(10_000),
        monthToDatePersonalExpensesMinor = rs(80_000),
        topServices = listOf(NamedAmount("Hair Cut", rs(60_000), 120)),
        topStaff = listOf(NamedAmount("Ahmed", rs(70_000)), NamedAmount("Bilal", rs(50_000))),
        salesByWeekday = mapOf(
            DayOfWeek.MONDAY to WeekdayStat(rs(40_000), 8),
            DayOfWeek.TUESDAY to WeekdayStat(rs(24_000), 8),
            DayOfWeek.SATURDAY to WeekdayStat(rs(120_000), 8),
            DayOfWeek.SUNDAY to WeekdayStat(rs(96_000), 8),
        ),
        customersLast60Days = 50,
        returningCustomersLast60Days = 22,
        salesCountMonthToDate = 300,
    )

    @Test
    fun `insights explain target pace trends and weekdays`() = runTest {
        val insights = RuleBasedInsightEngine().generate(snapshot())
        val behind = insights.filterIsInstance<Insight.TargetBehind>().single()
        assertEquals(rs(350_000), behind.remainingMinor)
        assertEquals(20, behind.daysLeft) // 11 Sep -> 30 Sep inclusive
        assertEquals(rs(17_500), behind.requiredPerDayMinor)
        assertTrue(insights.filterIsInstance<Insight.SalesTrend>().single().changePercent > 0)
        assertTrue(insights.filterIsInstance<Insight.ExpenseTrend>().single().changePercent > 0)
        assertEquals("Hair Cut", insights.filterIsInstance<Insight.BestService>().single().name)
        assertEquals("Ahmed", insights.filterIsInstance<Insight.TopStaff>().single().name)
        assertEquals(DayOfWeek.SATURDAY, insights.filterIsInstance<Insight.BusiestDay>().single().day)
        assertEquals(DayOfWeek.TUESDAY, insights.filterIsInstance<Insight.SlowestDay>().single().day)
        assertEquals(44.0, insights.filterIsInstance<Insight.ReturningCustomers>().single().percent, 0.01)
        assertTrue(insights.any { it is Insight.PersonalSpendingHigh })
    }

    @Test
    fun `target achieved and empty data`() = runTest {
        val achieved = RuleBasedInsightEngine().generate(snapshot(sales = rs(600_000)))
        assertTrue(achieved.any { it is Insight.TargetAchieved })

        val empty = BusinessSnapshot(
            LocalDate.of(2026, 9, 1), 0, 0, 0, 0, 0, 0, 0, emptyList(), emptyList(), emptyMap(), 0, 0, 0,
        )
        assertEquals(listOf(Insight.NotEnoughData), RuleBasedInsightEngine().generate(empty))
    }

    @Test
    fun `composite engine falls back when optional AI fails`() = runTest {
        val failing = InsightEngine { error("no network") }
        val engine = CompositeInsightEngine(fallback = RuleBasedInsightEngine(), primary = failing)
        assertTrue(engine.generate(snapshot()).isNotEmpty())
        val custom = CompositeInsightEngine(RuleBasedInsightEngine(), InsightEngine { listOf(Insight.NotEnoughData) })
        assertEquals(listOf(Insight.NotEnoughData), custom.generate(snapshot()))
    }
}
