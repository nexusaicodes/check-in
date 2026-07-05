package com.checkin.app

import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import com.checkin.app.ui.attendance.components.computeMonthTiles
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthSummaryTest {

    private fun summary(key: String, status: AttendanceStatus, hoursMs: Long) =
        DailySummary(key, hoursMs, 1, 0L, 0L, status)

    @Test
    fun `today is excluded from every tile`() {
        val today = "2026-06-15"
        val summaries = mapOf(
            "2026-06-13" to summary("2026-06-13", AttendanceStatus.PRESENT, 8 * 3_600_000L),
            "2026-06-14" to summary("2026-06-14", AttendanceStatus.HALF_DAY_LEAVE, 4 * 3_600_000L),
            today to summary(today, AttendanceStatus.PRESENT, 2 * 3_600_000L) // in-progress, must not count
        )

        val tiles = computeMonthTiles(summaries, todayKey = today, trackedDaysInMonth = 3)

        assertEquals(1, tiles.present)
        assertEquals(1, tiles.half)
        assertEquals(1, tiles.full) // 3 tracked - 1 present - 1 half
        assertEquals(12 * 3_600_000L, tiles.totalHoursMs) // 8h + 4h, today's 2h excluded
        assertEquals(4 * 3_600_000L, tiles.avgDailyMs)    // 12h / 3 tracked days
    }

    @Test
    fun `full day count is floored at zero`() {
        val tiles = computeMonthTiles(
            summaries = mapOf(
                "2026-06-13" to summary("2026-06-13", AttendanceStatus.PRESENT, 8 * 3_600_000L),
                "2026-06-14" to summary("2026-06-14", AttendanceStatus.PRESENT, 8 * 3_600_000L)
            ),
            todayKey = "2026-06-15",
            trackedDaysInMonth = 1
        )
        assertEquals(0, tiles.full)
    }

    @Test
    fun `zero tracked days yields zero totals`() {
        val tiles = computeMonthTiles(emptyMap(), todayKey = "2026-06-15", trackedDaysInMonth = 0)
        assertEquals(0L, tiles.avgDailyMs)
        assertEquals(0L, tiles.totalHoursMs)
    }
}
