package com.checkin.app

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.history.components.DayIntensity
import com.checkin.app.ui.history.components.computeMonthTiles
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthSummaryTest {

    private val hours8 = 8 * 3_600_000L
    private val minutes45 = 45 * 60_000L

    private fun day(key: String, ms: Long) = key to DailyAggregate(key, ms, 1, 0L, 0L)

    // --- Month tiles ---

    @Test
    fun `showed-up counts every day with sessions and missed is the rest of the window`() {
        val summaries = mapOf(day("2026-06-01", hours8), day("2026-06-02", minutes45))

        val tiles = computeMonthTiles(summaries, todayKey = "2026-06-10", trackedDaysInMonth = 9)

        assertEquals(2, tiles.showedUp)
        assertEquals(7, tiles.missed)
    }

    /** Today is in progress everywhere else in the app, so it must not be counted here either. */
    @Test
    fun `today is excluded from every figure`() {
        val summaries = mapOf(day("2026-06-01", hours8), day("2026-06-10", hours8))

        val tiles = computeMonthTiles(summaries, todayKey = "2026-06-10", trackedDaysInMonth = 9)

        assertEquals(1, tiles.showedUp)
        assertEquals(hours8, tiles.totalHoursMs)
    }

    /**
     * The average divides by *tracked* days, not by days with sessions, so missed days stay in the
     * denominator — otherwise showing up once a month would report a perfect daily average.
     */
    @Test
    fun `the average keeps missed days in the denominator`() {
        val summaries = mapOf(day("2026-06-01", hours8))

        val tiles = computeMonthTiles(summaries, todayKey = "2026-06-10", trackedDaysInMonth = 8)

        assertEquals(hours8 / 8, tiles.avgDailyMs)
    }

    @Test
    fun `missed never goes negative when the window is shorter than the recorded days`() {
        val summaries = mapOf(day("2026-06-01", hours8), day("2026-06-02", hours8))

        val tiles = computeMonthTiles(summaries, todayKey = "2026-06-10", trackedDaysInMonth = 1)

        assertEquals(0, tiles.missed)
    }

    @Test
    fun `an empty month averages zero rather than dividing by zero`() {
        val tiles = computeMonthTiles(emptyMap(), todayKey = "2026-06-10", trackedDaysInMonth = 0)

        assertEquals(0L, tiles.avgDailyMs)
        assertEquals(0, tiles.showedUp)
    }

    // --- Day intensity, which is what a calendar cell is drawn at ---

    @Test
    fun `the peak day reads at full strength`() {
        assertEquals(1f, DayIntensity.fractionOf(hours8, hours8), 0.001f)
    }

    /**
     * The floor is the point: showing up briefly must still be visibly a day showed up, not an
     * almost-empty cell that reads the same as not turning up at all.
     */
    @Test
    fun `a very short day is floored rather than faded to nothing`() {
        assertEquals(DayIntensity.MIN_FRACTION, DayIntensity.fractionOf(minutes45, 20 * 3_600_000L), 0.001f)
    }

    @Test
    fun `a day with no time is fully transparent`() {
        assertEquals(0f, DayIntensity.fractionOf(0L, hours8), 0.001f)
    }

    /** First day ever recorded, or a set of zero-length days: nothing to compare against. */
    @Test
    fun `a non-positive peak gives full strength rather than dividing by zero`() {
        assertEquals(1f, DayIntensity.fractionOf(hours8, 0L), 0.001f)
        assertEquals(1f, DayIntensity.fractionOf(hours8, -1L), 0.001f)
    }

    /** A day longer than the recorded peak (stale peak, mid-update) must not overflow the scale. */
    @Test
    fun `a day above the peak is clamped to full`() {
        assertEquals(1f, DayIntensity.fractionOf(hours8 * 2, hours8), 0.001f)
    }
}
