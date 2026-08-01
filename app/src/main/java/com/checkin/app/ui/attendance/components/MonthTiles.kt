package com.checkin.app.ui.attendance.components

import com.checkin.app.data.local.DailyAggregate

/** Month-summary values (all today-excluded). See [computeMonthTiles]. */
data class MonthTiles(val showedUp: Int, val missed: Int, val totalHoursMs: Long, val avgDailyMs: Long)

/**
 * Tile values for the month card, all excluding [todayKey] (in-progress, uncounted).
 *
 * [missed] is derived by subtraction, so a tracked day with no sessions is a day not shown up for.
 * The daily average divides the today-excluded total by [trackedDaysInMonth] rather than by the days
 * that had sessions, which keeps missed days in the denominator and every figure consistent about
 * "today".
 */
fun computeMonthTiles(summaries: Map<String, DailyAggregate>, todayKey: String, trackedDaysInMonth: Int): MonthTiles {
    val counted = summaries.filterKeys { it != todayKey }.values
    val showedUp = counted.size
    val missed = (trackedDaysInMonth - showedUp).coerceAtLeast(0)
    val totalHoursMs = counted.sumOf { it.totalDurationMs }
    val avgDailyMs = if (trackedDaysInMonth > 0) totalHoursMs / trackedDaysInMonth else 0L
    return MonthTiles(showedUp, missed, totalHoursMs, avgDailyMs)
}
