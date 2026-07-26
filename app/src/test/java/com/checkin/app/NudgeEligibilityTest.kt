package com.checkin.app

import com.checkin.app.notify.engagement.EngagementSnapshot
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeConfig
import com.checkin.app.notify.engagement.NudgeEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /**
     * There is no app-level do-not-disturb window. The hour only ever gates a nudge through its own
     * trigger rule, so a late-evening hour past that rule is eligible; Android's per-channel settings
     * are what a user silences the night with.
     */
    @Test
    fun `no hour of the day is suppressed on its own`() {
        assertEquals(Nudge.NOT_CHECKED_IN_BY, NudgeEligibility.select(eligible(hourOfDay = 23)))
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
}
