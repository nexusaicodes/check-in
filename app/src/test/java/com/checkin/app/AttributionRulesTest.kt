package com.checkin.app

import com.checkin.app.notify.log.AttributionRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionRulesTest {

    private val hour = 60 * 60 * 1000L
    private val window = 4 * hour

    /** Defaults for the two suppressions, so each test states only the condition it is about. */
    private fun credit(
        shownAt: Long,
        actionAt: Long,
        windowMs: Long = window,
        latestConvertedAt: Long? = null,
        latestDismissedAt: Long? = null,
    ) = AttributionRules.canCredit(shownAt, actionAt, windowMs, latestConvertedAt, latestDismissedAt)

    @Test
    fun `an action shortly after a nudge is credited`() {
        assertTrue(credit(shownAt = 0L, actionAt = hour))
    }

    @Test
    fun `an action outside the window is not credited`() {
        assertFalse(credit(shownAt = 0L, actionAt = 5 * hour))
    }

    @Test
    fun `the window boundary is inclusive`() {
        assertTrue(credit(shownAt = 0L, actionAt = window))
    }

    /** Clock skew or a stale read could present an action that precedes the nudge. */
    @Test
    fun `an action before the nudge is not credited`() {
        assertFalse(credit(shownAt = 2 * hour, actionAt = hour))
    }

    /**
     * The rule that keeps a conversion rate at or below 100%: a second check-in inside the same
     * window is a second check-in, not a second conversion.
     */
    @Test
    fun `a nudge is credited at most once`() {
        assertFalse(credit(shownAt = 0L, actionAt = 2 * hour, latestConvertedAt = hour))
    }

    /** A conversion belonging to an *earlier* nudge must not block the current one. */
    @Test
    fun `an older conversion does not block a newer nudge`() {
        assertTrue(credit(shownAt = 3 * hour, actionAt = 4 * hour, latestConvertedAt = hour))
    }

    /** A conversion recorded at the exact instant of the showing still counts as already credited. */
    @Test
    fun `a conversion at the showing instant blocks a repeat credit`() {
        assertFalse(credit(shownAt = hour, actionAt = 2 * hour, latestConvertedAt = hour))
    }

    /**
     * The rule that keeps a rejection from being reported as a success. Swiping the notification away
     * and then checking in anyway is a check-in the nudge did not cause.
     */
    @Test
    fun `a nudge dismissed after it was shown earns no credit`() {
        assertFalse(credit(shownAt = 0L, actionAt = 2 * hour, latestDismissedAt = hour))
    }

    /** Symmetric with the conversion rule: a dismissal at the showing instant still suppresses. */
    @Test
    fun `a dismissal at the showing instant blocks credit`() {
        assertFalse(credit(shownAt = hour, actionAt = 2 * hour, latestDismissedAt = hour))
    }

    /** A dismissal of an *earlier* nudge says nothing about the one shown since. */
    @Test
    fun `a dismissal before the showing does not block credit`() {
        assertTrue(credit(shownAt = 3 * hour, actionAt = 4 * hour, latestDismissedAt = hour))
    }
}
