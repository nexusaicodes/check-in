package com.checkin.app.data

import com.checkin.app.data.local.DailyAggregate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure statistics over a date-keyed map of days that had sessions.
 *
 * Showing up is the unit. A day counts because it has an entry, not because its hours cleared a
 * bar — so a 45-minute day on a bad week counts exactly as much as a nine-hour one. That is the
 * point: a streak measured against a target mostly reports failure, because targets are missed as
 * the normal case, and a system that mostly reports failure attacks the behaviour it exists to
 * build.
 */
object AttendanceStats {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun showedUp(summaries: Map<String, DailyAggregate>, date: LocalDate): Boolean =
        summaries.containsKey(date.format(dateFormatter))

    /** Consecutive days showed up ending at [end], walking backwards but not past [start]. */
    fun currentStreak(summaries: Map<String, DailyAggregate>, start: LocalDate, end: LocalDate): Int {
        if (start.isAfter(end)) return 0
        var streak = 0
        var day = end
        while (!day.isBefore(start) && showedUp(summaries, day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Longest run of consecutive days showed up within [start]..[end] inclusive. */
    fun bestStreak(summaries: Map<String, DailyAggregate>, start: LocalDate, end: LocalDate): Int {
        var best = 0
        var run = 0
        var day = start
        while (!day.isAfter(end)) {
            if (showedUp(summaries, day)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    fun showedUpDays(summaries: Map<String, DailyAggregate>): Int = summaries.size

    fun totalWorkedMs(summaries: Map<String, DailyAggregate>): Long = summaries.values.sumOf { it.totalDurationMs }

    /**
     * The longest single day in [summaries], used to normalize how strongly a calendar cell reads.
     *
     * Self-relative on purpose: there is no configured target left to measure against, and a fixed
     * constant would be the same hidden bar under another name. Zero when there is nothing to
     * compare — callers must treat that as "no intensity" rather than dividing by it.
     */
    fun peakDayMs(summaries: Map<String, DailyAggregate>): Long =
        summaries.values.maxOfOrNull { it.totalDurationMs } ?: 0L
}
