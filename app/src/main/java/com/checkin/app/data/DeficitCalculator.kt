package com.checkin.app.data

import com.checkin.app.data.local.AttendanceRules
import com.checkin.app.data.local.DailySummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pure rolling-deficit computation over an already-classified summary map. */
object DeficitCalculator {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Sums each day's leave fraction from [start] to [endInclusive]. A day with no sessions at all
     * (absent from [summaries]) counts as a full day of leave.
     */
    fun computeDeficit(
        summaries: Map<String, DailySummary>,
        start: LocalDate,
        endInclusive: LocalDate
    ): Double {
        if (start.isAfter(endInclusive)) return 0.0
        var deficit = 0.0
        var day = start
        while (!day.isAfter(endInclusive)) {
            val summary = summaries[day.format(dateFormatter)]
            deficit += if (summary == null) 1.0 else AttendanceRules.leaveFraction(summary.status)
            day = day.plusDays(1)
        }
        return deficit
    }
}
