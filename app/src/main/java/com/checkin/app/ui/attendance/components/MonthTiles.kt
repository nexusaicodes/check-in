package com.checkin.app.ui.attendance.components

import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary

/** Month-summary values (all today-excluded). See [computeMonthTiles]. */
data class MonthTiles(val present: Int, val half: Int, val full: Int, val totalHoursMs: Long, val avgDailyMs: Long)

/**
 * Tile values for the month card, all excluding [todayKey] (in-progress, uncounted). [full] is derived
 * by subtraction so absent tracked days count as full-day leave; the daily average divides the
 * today-excluded total by [trackedDaysInMonth], keeping every figure consistent about "today".
 */
fun computeMonthTiles(summaries: Map<String, DailySummary>, todayKey: String, trackedDaysInMonth: Int): MonthTiles {
    val classified = summaries.filterKeys { it != todayKey }.values
    val present = classified.count { it.status == AttendanceStatus.PRESENT }
    val half = classified.count { it.status == AttendanceStatus.HALF_DAY_LEAVE }
    val full = (trackedDaysInMonth - present - half).coerceAtLeast(0)
    val totalHoursMs = classified.sumOf { it.totalDurationMs }
    val avgDailyMs = if (trackedDaysInMonth > 0) totalHoursMs / trackedDaysInMonth else 0L
    return MonthTiles(present, half, full, totalHoursMs, avgDailyMs)
}
