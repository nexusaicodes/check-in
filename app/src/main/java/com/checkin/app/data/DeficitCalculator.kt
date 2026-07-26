package com.checkin.app.data

import com.checkin.app.data.local.AttendanceRules
import com.checkin.app.data.local.DailySummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure rolling-deficit computation over an already-classified summary map.
 *
 * No screen renders a deficit and no `UiState` carries one — the redesign traded it for
 * forward-looking encouragement. It is kept because it encodes the app's leave rule, and the
 * classification thresholds every screen does show derive from the same target. It is not dead code
 * to be tidied away; surfacing a deficit again is adding a field to a ViewModel, not rewriting this.
 */
object DeficitCalculator {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Sums each day's leave fraction from [start] to [endInclusive]. A day with no sessions at all
     * (absent from [summaries]) counts as a full day of leave.
     */
    fun computeDeficit(summaries: Map<String, DailySummary>, start: LocalDate, endInclusive: LocalDate): Double {
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
