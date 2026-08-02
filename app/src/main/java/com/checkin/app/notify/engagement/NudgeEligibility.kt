package com.checkin.app.notify.engagement

/**
 * Decides which nudge — if any — to send for a given [EngagementSnapshot].
 *
 * This is the whole decision surface of the engagement system, and it is a pure function: no clock,
 * no database, no Android. Every experiment in timing, capping or targeting is a change here and
 * nowhere else, which is what keeps engagement work from reaching into tracking logic.
 *
 * There is deliberately no do-not-disturb window: Android's per-channel settings already give the
 * user one, and an app-invented second policy would apply only to nudges while the session reminder
 * and the timer notification ignored it — a quiet window the app could not actually honour.
 *
 * **The daily cap is the whole frequency bound**; anything it allows is allowed. A per-nudge cooldown
 * beside it would, at `maxPerDay = 1`, only ever suppress a nudge the cap had already suppressed,
 * while measuring a rolling window against the cap's calendar day — two rules disagreeing about what
 * a day is.
 *
 * There is likewise **no tracking-started gate**. The one nudge that exists — "you haven't checked in
 * today" — is for exactly the user who has not started yet, so requiring a first check-in would lock
 * it away from its audience. A user who finds it unwelcome has a switch.
 *
 * Gates run cheapest-and-broadest first: the global cap, then per-nudge ones.
 */
object NudgeEligibility {

    fun select(snapshot: EngagementSnapshot): Nudge? {
        if (snapshot.shownToday >= snapshot.config.maxPerDay) return null

        // Declaration order in Nudge is the priority order.
        return Nudge.entries.firstOrNull { nudge ->
            nudge in snapshot.enabledNudges && triggers(snapshot, nudge)
        }
    }

    private fun triggers(snapshot: EngagementSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN_BY ->
            !snapshot.hasCheckedInToday &&
                !snapshot.isCheckedIn &&
                snapshot.hourOfDay >= snapshot.config.notCheckedInByHour
    }
}
