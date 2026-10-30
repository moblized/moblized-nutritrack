package com.moblized.nutritrack.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

// java.time-style helpers over kotlinx-datetime, so the shared code reads the same on both phones.

fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDateTime.Companion.now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun LocalDate.minusDays(days: Long): LocalDate = minus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)
fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

fun LocalDateTime.toLocalDate(): LocalDate = date

/** Midnight UTC of this date in epoch millis (what Material's date picker uses). */
fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** The UTC calendar date of epoch millis (from Material's date picker). */
fun utcDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/** The phone's local date of epoch millis. */
fun localDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

private val SHORT_DAYS = mapOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed", DayOfWeek.THURSDAY to "Thu",
    DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun",
)

private fun Month.short(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** "Thu, Oct 30" this year, "Oct 30, 2025" otherwise. */
fun LocalDate.shortLabel(today: LocalDate): String =
    if (year == today.year) "${SHORT_DAYS.getValue(dayOfWeek)}, ${month.short()} $dayOfMonth"
    else "${month.short()} $dayOfMonth, $year"

/** "7:30 PM" for minutes after midnight. */
fun clockLabel(minutesOfDay: Int): String {
    val h = minutesOfDay / 60
    val m = minutesOfDay % 60
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
}
