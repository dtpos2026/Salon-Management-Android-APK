package com.dtpos.salonmanager.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Date helpers. Business days are stored as epoch-day [Long] values so that daily, weekly and
 * monthly reports are simple indexed range queries, independent of the device time zone later.
 */
object DateTimeUtils {

    /** Weeks start on Monday for weekly targets and reports. */
    val WEEK_START: DayOfWeek = DayOfWeek.MONDAY

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun today(zone: ZoneId = zone()): LocalDate = LocalDate.now(zone)

    fun nowMillis(): Long = System.currentTimeMillis()

    fun toLocalDate(epochMillis: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalDateTime(epochMillis: Long, zone: ZoneId = zone()): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun startOfDayMillis(date: LocalDate, zone: ZoneId = zone()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(date: LocalDate, zone: ZoneId = zone()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    /** Material date pickers work with UTC-midnight millis. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(WEEK_START))

    fun monthStart(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun monthEnd(date: LocalDate): LocalDate = date.with(TemporalAdjusters.lastDayOfMonth())

    private val dateFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
    private val shortDateFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault()) }
    private val timeFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val monthFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    private val fileStampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.US)
    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

    fun formatDate(epochMillis: Long): String = formatDate(toLocalDate(epochMillis))

    fun formatTime(epochMillis: Long): String = toLocalDateTime(epochMillis).format(timeFormatter)

    fun formatDateTime(epochMillis: Long): String = "${formatDate(epochMillis)}, ${formatTime(epochMillis)}"

    fun formatMonth(date: LocalDate): String = date.format(monthFormatter)

    fun formatIso(date: LocalDate): String = date.format(isoDate)

    fun fileStamp(epochMillis: Long = nowMillis()): String = toLocalDateTime(epochMillis).format(fileStampFormatter)

    fun parseIso(value: String): LocalDate? = try {
        LocalDate.parse(value, isoDate)
    } catch (e: Exception) {
        null
    }
}
