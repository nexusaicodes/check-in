package com.checkin.app

import com.checkin.app.data.AttendanceStats
import com.checkin.app.data.local.DailyAggregate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Showing up is the unit. These pin the thing that makes the streak worth keeping: a short day
 * counts exactly as much as a long one, so a bad week does not break a chain the user was still
 * turning up for.
 */
class AttendanceStatsTest {

    private fun days(vararg entries: Pair<LocalDate, Long>): Map<String, DailyAggregate> =
        entries.associate { (date, ms) ->
            date.toString() to DailyAggregate(date.toString(), ms, 1, 0L, null)
        }

    private val minutes45 = 45 * 60_000L
    private val hours9 = 9 * 3_600_000L

    private fun day(n: Int) = LocalDate.of(2026, 6, n)

    // --- Current streak ---

    @Test
    fun `current streak counts back from the end while days have sessions`() {
        val summaries = days(day(12) to hours9, day(13) to hours9, day(14) to hours9)

        assertEquals(3, AttendanceStats.currentStreak(summaries, day(1), day(14)))
    }

    /** The whole point: a 45-minute day is a day showed up, and it keeps the chain alive. */
    @Test
    fun `a short day does not break the streak`() {
        val summaries = days(day(12) to hours9, day(13) to minutes45, day(14) to hours9)

        assertEquals(3, AttendanceStats.currentStreak(summaries, day(1), day(14)))
    }

    @Test
    fun `a day with no sessions breaks the streak`() {
        val summaries = days(day(12) to hours9, day(14) to hours9)

        assertEquals(1, AttendanceStats.currentStreak(summaries, day(1), day(14)))
    }

    @Test
    fun `the streak stops at the tracking start rather than walking past it`() {
        val summaries = days(day(12) to hours9, day(13) to hours9, day(14) to hours9)

        assertEquals(2, AttendanceStats.currentStreak(summaries, day(13), day(14)))
    }

    @Test
    fun `an inverted range is zero rather than negative`() {
        assertEquals(0, AttendanceStats.currentStreak(days(day(14) to hours9), day(15), day(14)))
    }

    // --- Best streak ---

    @Test
    fun `best streak finds the longest run, not the most recent`() {
        val summaries = days(
            day(1) to hours9,
            day(2) to minutes45,
            day(3) to hours9,
            // gap on the 4th
            day(5) to hours9,
        )

        assertEquals(3, AttendanceStats.bestStreak(summaries, day(1), day(5)))
    }

    @Test
    fun `best streak is zero when nothing was recorded`() {
        assertEquals(0, AttendanceStats.bestStreak(emptyMap(), day(1), day(30)))
    }

    // --- Totals ---

    @Test
    fun `showed-up days counts entries, whatever their length`() {
        val summaries = days(day(1) to minutes45, day(2) to hours9)

        assertEquals(2, AttendanceStats.showedUpDays(summaries))
    }

    @Test
    fun `total worked sums every day`() {
        val summaries = days(day(1) to minutes45, day(2) to hours9)

        assertEquals(minutes45 + hours9, AttendanceStats.totalWorkedMs(summaries))
    }

    // --- Peak, which is what the calendar shades against ---

    @Test
    fun `peak is the longest single day`() {
        val summaries = days(day(1) to minutes45, day(2) to hours9, day(3) to hours9 / 2)

        assertEquals(hours9, AttendanceStats.peakDayMs(summaries))
    }

    /** No history is a real state on a first run; the caller must not divide by this. */
    @Test
    fun `peak is zero with no days`() {
        assertEquals(0L, AttendanceStats.peakDayMs(emptyMap()))
    }
}
