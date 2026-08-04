package com.checkin.app

import com.checkin.app.ui.checkin.SessionPeriod
import com.checkin.app.ui.checkin.sessionPeriodOfHour
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The label under a session's row. Pinned at every boundary hour, because the cuts are the whole of
 * what the function decides and an off-by-one puts a 12:00 session in the morning.
 */
class SessionPeriodTest {

    @Test
    fun `each period starts on its own boundary hour`() {
        assertEquals(SessionPeriod.EARLY_MORNING, sessionPeriodOfHour(5))
        assertEquals(SessionPeriod.MORNING, sessionPeriodOfHour(8))
        assertEquals(SessionPeriod.MIDDAY, sessionPeriodOfHour(12))
        assertEquals(SessionPeriod.AFTERNOON, sessionPeriodOfHour(14))
        assertEquals(SessionPeriod.EVENING, sessionPeriodOfHour(17))
        assertEquals(SessionPeriod.NIGHT, sessionPeriodOfHour(21))
    }

    @Test
    fun `each period runs up to the hour before the next one`() {
        assertEquals(SessionPeriod.EARLY_MORNING, sessionPeriodOfHour(7))
        assertEquals(SessionPeriod.MORNING, sessionPeriodOfHour(11))
        assertEquals(SessionPeriod.MIDDAY, sessionPeriodOfHour(13))
        assertEquals(SessionPeriod.AFTERNOON, sessionPeriodOfHour(16))
        assertEquals(SessionPeriod.EVENING, sessionPeriodOfHour(20))
        assertEquals(SessionPeriod.NIGHT, sessionPeriodOfHour(23))
    }

    /** The small hours belong to the night that is still running, not to the day just begun. */
    @Test
    fun `the hours before the early-morning cut are late night`() {
        assertEquals(SessionPeriod.LATE_NIGHT, sessionPeriodOfHour(0))
        assertEquals(SessionPeriod.LATE_NIGHT, sessionPeriodOfHour(2))
        assertEquals(SessionPeriod.LATE_NIGHT, sessionPeriodOfHour(4))
    }

    /** Every hour of the clock lands somewhere: the label can never be absent from a row. */
    @Test
    fun `every hour of the day has a period`() {
        val covered = (0..23).map { sessionPeriodOfHour(it) }
        assertEquals(24, covered.size)
        assertEquals(SessionPeriod.entries.toSet(), covered.toSet())
    }
}
