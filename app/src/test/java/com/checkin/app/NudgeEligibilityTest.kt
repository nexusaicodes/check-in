package com.checkin.app

import com.checkin.app.notify.engagement.EngagementSnapshot
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeConfig
import com.checkin.app.notify.engagement.NudgeEligibility
import com.checkin.app.notify.engagement.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeEligibilityTest {

    private val hour = 60 * 60 * 1000L

    /** The state in which NOT_CHECKED_IN_BY should fire; individual tests break one thing at a time. */
    private fun eligible(
        hourOfDay: Int = 12,
        trackingStarted: Boolean = true,
        isCheckedIn: Boolean = false,
        hasCheckedInToday: Boolean = false,
        enabled: Set<Nudge> = setOf(Nudge.NOT_CHECKED_IN_BY),
        lastShownAt: Map<Nudge, Long> = emptyMap(),
        shownToday: Int = 0,
        quietHours: QuietHours = QuietHours(21, 8),
        config: NudgeConfig = NudgeConfig(),
        nowMillis: Long = 100 * hour
    ) = EngagementSnapshot(
        nowMillis = nowMillis,
        hourOfDay = hourOfDay,
        trackingStarted = trackingStarted,
        isCheckedIn = isCheckedIn,
        hasCheckedInToday = hasCheckedInToday,
        enabledNudges = enabled,
        lastShownAt = lastShownAt,
        shownToday = shownToday,
        quietHours = quietHours,
        config = config
    )

    @Test
    fun `the baseline snapshot is eligible`() {
        assertEquals(Nudge.NOT_CHECKED_IN_BY, NudgeEligibility.select(eligible()))
    }

    /** Nudging someone who has never checked in would be messaging a user mid-onboarding. */
    @Test
    fun `nothing fires before tracking has started`() {
        assertNull(NudgeEligibility.select(eligible(trackingStarted = false)))
    }

    @Test
    fun `nothing fires when the nudge is disabled`() {
        assertNull(NudgeEligibility.select(eligible(enabled = emptySet())))
    }

    @Test
    fun `nothing fires once the daily cap is reached`() {
        assertNull(NudgeEligibility.select(eligible(shownToday = 1)))
        assertEquals(
            Nudge.NOT_CHECKED_IN_BY,
            NudgeEligibility.select(eligible(shownToday = 1, config = NudgeConfig(maxPerDay = 2)))
        )
    }

    @Test
    fun `nothing fires during quiet hours`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 23)))
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 3)))
    }

    @Test
    fun `nothing fires before the trigger hour`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 10)))
        assertEquals(Nudge.NOT_CHECKED_IN_BY, NudgeEligibility.select(eligible(hourOfDay = 11)))
    }

    @Test
    fun `nothing fires once the user has already checked in today`() {
        assertNull(NudgeEligibility.select(eligible(hasCheckedInToday = true)))
    }

    /** A session open since yesterday means today has no row yet, but the user is plainly working. */
    @Test
    fun `nothing fires while a session is open`() {
        assertNull(NudgeEligibility.select(eligible(isCheckedIn = true)))
    }

    @Test
    fun `the same nudge does not repeat inside its cooldown`() {
        val now = 100 * hour
        val recent = mapOf(Nudge.NOT_CHECKED_IN_BY to now - 5 * hour)
        assertNull(NudgeEligibility.select(eligible(nowMillis = now, lastShownAt = recent)))

        val old = mapOf(Nudge.NOT_CHECKED_IN_BY to now - 21 * hour)
        assertEquals(
            Nudge.NOT_CHECKED_IN_BY,
            NudgeEligibility.select(eligible(nowMillis = now, lastShownAt = old))
        )
    }

    /**
     * A clock moved backwards (timezone change, manual set) makes elapsed negative; that must read as
     * "still cooling down", not as a huge gap that unlocks an immediate repeat.
     */
    @Test
    fun `a backwards clock does not unlock the cooldown`() {
        val now = 100 * hour
        val future = mapOf(Nudge.NOT_CHECKED_IN_BY to now + 5 * hour)

        assertNull(NudgeEligibility.select(eligible(nowMillis = now, lastShownAt = future)))
    }

    // --- QuietHours ---

    @Test
    fun `a quiet window wrapping midnight covers both sides`() {
        val quiet = QuietHours(startHour = 22, endHour = 7)

        assertTrue(quiet.contains(22))
        assertTrue(quiet.contains(23))
        assertTrue(quiet.contains(0))
        assertTrue(quiet.contains(6))
        assertFalse(quiet.contains(7))
        assertFalse(quiet.contains(12))
    }

    @Test
    fun `a same-day quiet window is a plain range`() {
        val quiet = QuietHours(startHour = 9, endHour = 17)

        assertFalse(quiet.contains(8))
        assertTrue(quiet.contains(9))
        assertTrue(quiet.contains(16))
        assertFalse(quiet.contains(17))
    }

    /** Equal bounds must disable the window, not silence all 24 hours. */
    @Test
    fun `an empty quiet window silences nothing`() {
        val quiet = QuietHours(startHour = 8, endHour = 8)

        (0..23).forEach { assertFalse("hour $it", quiet.contains(it)) }
    }
}
