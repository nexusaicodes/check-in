package com.checkin.app.data

import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pure attendance statistics over a date-keyed summary map. */
object AttendanceStats {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun isPresent(summaries: Map<String, DailySummary>, date: LocalDate): Boolean =
        summaries[date.format(dateFormatter)]?.status == AttendanceStatus.PRESENT

    /** Consecutive PRESENT days ending at [end], walking backwards but not past [start]. */
    fun currentStreak(summaries: Map<String, DailySummary>, start: LocalDate, end: LocalDate): Int {
        if (start.isAfter(end)) return 0
        var streak = 0
        var day = end
        while (!day.isBefore(start) && isPresent(summaries, day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Longest run of consecutive PRESENT days within [start]..[end] inclusive. */
    fun bestStreak(summaries: Map<String, DailySummary>, start: LocalDate, end: LocalDate): Int {
        var best = 0
        var run = 0
        var day = start
        while (!day.isAfter(end)) {
            if (isPresent(summaries, day)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    fun presentDays(summaries: Map<String, DailySummary>): Int =
        summaries.values.count { it.status == AttendanceStatus.PRESENT }

    fun totalWorkedMs(summaries: Map<String, DailySummary>): Long =
        summaries.values.sumOf { it.totalDurationMs }
}
