package com.checkin.app

import com.checkin.app.notify.log.AttributionRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionRulesTest {

    private val hour = 60 * 60 * 1000L
    private val window = 4 * hour

    @Test
    fun `an action shortly after a nudge is credited`() {
        assertTrue(
            AttributionRules.canCredit(shownAt = 0L, actionAt = hour, windowMs = window, latestConvertedAt = null),
        )
    }

    @Test
    fun `an action outside the window is not credited`() {
        assertFalse(AttributionRules.canCredit(0L, 5 * hour, window, null))
    }

    @Test
    fun `the window boundary is inclusive`() {
        assertTrue(AttributionRules.canCredit(0L, window, window, null))
    }

    /** Clock skew or a stale read could present an action that precedes the nudge. */
    @Test
    fun `an action before the nudge is not credited`() {
        assertFalse(
            AttributionRules.canCredit(
                shownAt = 2 * hour,
                actionAt = hour,
                windowMs = window,
                latestConvertedAt = null,
            ),
        )
    }

    /**
     * The rule that keeps a conversion rate at or below 100%: a second check-in inside the same
     * window is a second check-in, not a second conversion.
     */
    @Test
    fun `a nudge is credited at most once`() {
        assertFalse(
            AttributionRules.canCredit(shownAt = 0L, actionAt = 2 * hour, windowMs = window, latestConvertedAt = hour),
        )
    }

    /** A conversion belonging to an *earlier* nudge must not block the current one. */
    @Test
    fun `an older conversion does not block a newer nudge`() {
        assertTrue(
            AttributionRules.canCredit(
                shownAt = 3 * hour,
                actionAt = 4 * hour,
                windowMs = window,
                latestConvertedAt = hour,
            ),
        )
    }

    /** A conversion recorded at the exact instant of the showing still counts as already credited. */
    @Test
    fun `a conversion at the showing instant blocks a repeat credit`() {
        assertFalse(
            AttributionRules.canCredit(
                shownAt = hour,
                actionAt = 2 * hour,
                windowMs = window,
                latestConvertedAt = hour,
            ),
        )
    }
}
