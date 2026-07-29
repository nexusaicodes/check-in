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
    /** False before the first check-in has ever happened — nothing to nudge about yet. */
    val trackingStarted: Boolean,
    /** A session is open right now. */
    val isCheckedIn: Boolean,
    /** Any session, open or closed, exists for today. */
    val hasCheckedInToday: Boolean,
    val enabledNudges: Set<Nudge>,
    /** When each nudge was last shown, epoch millis. Absent means never. */
    val lastShownAt: Map<Nudge, Long> = emptyMap(),
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
    val maxPerDay: Int = 1,
    /** Minimum spacing between two showings of the *same* nudge. */
    val minGapMs: Long = DEFAULT_MIN_GAP_MS,
)

/** 20 hours. */
private const val DEFAULT_MIN_GAP_MS = 20L * 60L * 60L * 1_000L
