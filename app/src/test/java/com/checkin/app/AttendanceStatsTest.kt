package com.checkin.app

import com.checkin.app.data.AttendanceStats
import com.checkin.app.data.local.AttendanceStatus.FULL_DAY_LEAVE
import com.checkin.app.data.local.AttendanceStatus.PRESENT
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AttendanceStatsTest {

    private val d1 = LocalDate.of(2026, 6, 1)

    @Test
    fun `current streak counts back from the end`() {
        val summaries = Summaries.of(
            d1 to FULL_DAY_LEAVE,
            d1.plusDays(1) to FULL_DAY_LEAVE,
            d1.plusDays(2) to PRESENT,
            d1.plusDays(3) to PRESENT,
            d1.plusDays(4) to PRESENT
        )
        assertEquals(3, AttendanceStats.currentStreak(summaries, d1, d1.plusDays(4)))
    }

    @Test
    fun `current streak is zero when the last day is not present`() {
        val summaries = Summaries.of(
            d1 to PRESENT,
            d1.plusDays(1) to FULL_DAY_LEAVE
        )
        assertEquals(0, AttendanceStats.currentStreak(summaries, d1, d1.plusDays(1)))
    }

    @Test
    fun `best streak finds the longest run`() {
        val summaries = Summaries.of(
            d1 to PRESENT,
            d1.plusDays(1) to PRESENT,
            d1.plusDays(2) to PRESENT,
            d1.plusDays(3) to FULL_DAY_LEAVE,
            d1.plusDays(4) to PRESENT,
            d1.plusDays(5) to PRESENT
        )
        assertEquals(3, AttendanceStats.bestStreak(summaries, d1, d1.plusDays(5)))
        assertEquals(2, AttendanceStats.currentStreak(summaries, d1, d1.plusDays(5)))
    }

    @Test
    fun `empty window yields zero streaks`() {
        assertEquals(0, AttendanceStats.currentStreak(emptyMap(), d1, d1.plusDays(4)))
        assertEquals(0, AttendanceStats.bestStreak(emptyMap(), d1, d1.plusDays(4)))
    }

    @Test
    fun `present days and total worked are aggregated`() {
        val summaries = Summaries.withDurations(
            Triple(d1, PRESENT, 3_600_000L),
            Triple(d1.plusDays(1), FULL_DAY_LEAVE, 600_000L),
            Triple(d1.plusDays(2), PRESENT, 7_200_000L)
        )
        assertEquals(2, AttendanceStats.presentDays(summaries))
        assertEquals(11_400_000L, AttendanceStats.totalWorkedMs(summaries))
    }
}
