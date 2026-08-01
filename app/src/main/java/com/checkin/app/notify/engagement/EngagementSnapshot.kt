package com.checkin.app.notify.engagement

/**
 * Everything [NudgeEligibility] is allowed to look at, gathered by the caller. Passing a plain value
 * object — rather than letting the decision reach into a repository or a clock — is what keeps the
 * rules pure, exhaustively testable, and unable to touch attendance logic.
 */
data class EngagementSnapshot(
    val nowMillis: Long,
    /** Local hour of day, 0-23. */
    val hourOfDay: Int,
    /** A session is open right now. */
    val isCheckedIn: Boolean,
    /** Any session, open or closed, exists for today. */
    val hasCheckedInToday: Boolean,
    val enabledNudges: Set<Nudge>,
    /** Nudges already shown in the current day. */
    val shownToday: Int = 0,
    val config: NudgeConfig = NudgeConfig(),
)

/** Tunables for the eligibility rules — the surface an engagement experiment varies. */
data class NudgeConfig(
    /**
     * [Nudge.NOT_CHECKED_IN_BY] can fire from this local hour onward.
     *
     * Deliberately not quoted in any user-facing string. Delivery is best-effort — the pass that
     * sends it runs hourly and is deferrable — so naming an exact time promises a punctuality the
     * app cannot keep, and changing the value would then silently make the copy wrong.
     */
    val notCheckedInByHour: Int = 10,
    /**
     * The only frequency bound there is. A per-nudge cooldown used to sit beside it and was removed:
     * at one nudge a day it could only suppress what the cap already had, while measuring a rolling
     * window the cap did not, so the two could disagree about where a day ended.
     */
    val maxPerDay: Int = 1,
)
