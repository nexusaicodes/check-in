package com.checkin.app.notify.engagement

/**
 * Decides which nudge — if any — to send for a given [EngagementSnapshot].
 *
 * This is the whole decision surface of the engagement system, and it is a pure function: no clock,
 * no database, no Android. Every experiment in timing, capping or targeting is a change here and
 * nowhere else, which is what keeps engagement work from reaching into attendance logic.
 *
 * There is deliberately no do-not-disturb window: Android's per-channel settings already give the
 * user one, and an app-invented second policy only applied to nudges while the presence check and
 * the timer notification ignored it. What bounds nudges here is the daily cap and the per-nudge
 * cooldown, both of which are about frequency rather than the hour on the clock.
 *
 * Gates run cheapest-and-broadest first: global suppressions, then per-nudge ones.
 */
object NudgeEligibility {

    fun select(snapshot: EngagementSnapshot): Nudge? {
        if (!snapshot.trackingStarted) return null
        if (snapshot.shownToday >= snapshot.config.maxPerDay) return null

        // Declaration order in Nudge is the priority order.
        return Nudge.entries.firstOrNull { nudge ->
            nudge in snapshot.enabledNudges &&
                !withinCooldown(snapshot, nudge) &&
                triggers(snapshot, nudge)
        }
    }

    private fun withinCooldown(snapshot: EngagementSnapshot, nudge: Nudge): Boolean {
        val last = snapshot.lastShownAt[nudge] ?: return false
        // A clock that has moved backwards (timezone change, manual set) would otherwise read as a
        // huge elapsed gap and let a nudge fire early; treat any negative elapsed as still cooling.
        val elapsed = snapshot.nowMillis - last
        return elapsed < snapshot.config.minGapMs
    }

    private fun triggers(snapshot: EngagementSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN_BY ->
            !snapshot.hasCheckedInToday &&
                !snapshot.isCheckedIn &&
                snapshot.hourOfDay >= snapshot.config.notCheckedInByHour
    }
}
