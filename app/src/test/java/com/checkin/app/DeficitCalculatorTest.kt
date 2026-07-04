package com.checkin.app

import com.checkin.app.data.DeficitCalculator
import com.checkin.app.data.local.AttendanceStatus.FULL_DAY_LEAVE
import com.checkin.app.data.local.AttendanceStatus.HALF_DAY_LEAVE
import com.checkin.app.data.local.AttendanceStatus.PRESENT
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DeficitCalculatorTest {

    private val start = LocalDate.of(2026, 6, 1)
    private val end = LocalDate.of(2026, 6, 5) // 5-day inclusive window

    @Test
    fun `all present is zero deficit`() {
        val summaries = Summaries.of(
            start to PRESENT,
            start.plusDays(1) to PRESENT,
            start.plusDays(2) to PRESENT,
            start.plusDays(3) to PRESENT,
            end to PRESENT
        )
        assertEquals(0.0, DeficitCalculator.computeDeficit(summaries, start, end), 0.0)
    }

    @Test
    fun `missing days each count as a full day`() {
        assertEquals(5.0, DeficitCalculator.computeDeficit(emptyMap(), start, end), 0.0)
    }

    @Test
    fun `mixed statuses accumulate fractionally`() {
        val summaries = Summaries.of(
            start to PRESENT,                 // 0.0
            start.plusDays(1) to HALF_DAY_LEAVE, // 0.5
            start.plusDays(2) to FULL_DAY_LEAVE, // 1.0
            // start.plusDays(3) missing         // 1.0
            end to PRESENT                    // 0.0
        )
        assertEquals(2.5, DeficitCalculator.computeDeficit(summaries, start, end), 0.0)
    }

    @Test
    fun `start after end is zero`() {
        assertEquals(0.0, DeficitCalculator.computeDeficit(emptyMap(), end, start), 0.0)
    }
}
