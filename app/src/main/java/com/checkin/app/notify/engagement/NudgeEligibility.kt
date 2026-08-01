package com.checkin.app.notify.engagement

/**
 * Decides which nudge — if any — to send for a given [EngagementSnapshot].
 *
 * This is the whole decision surface of the engagement system, and it is a pure function: no clock,
 * no database, no Android. Every experiment in timing, capping or targeting is a change here and
 * nowhere else, which is what keeps engagement work from reaching into tracking logic.
 *
 * There is deliberately no do-not-disturb window: Android's per-channel settings already give the
 * user one, and an app-invented second policy only applied to nudges while the session reminder and
 * the timer notification ignored it.
 *
 * **What bounds nudges is the daily cap alone**, and that is the whole of it. A per-nudge cooldown
 * used to sit alongside it, which with `maxPerDay = 1` could only ever suppress a nudge the cap had
 * already suppressed — while making the two rules disagree about what a "day" was, since the cap
 * counts from the log's calendar day and the cooldown measured a rolling 20 hours from the last
 * send. Anything the cap allows is allowed.
 *
 * There is likewise **no tracking-started gate**. Requiring a first check-in before the app would
 * say anything meant the one nudge that exists — "you haven't checked in today" — could never reach
 * the user who had not yet started, which is exactly who it is for. A user who finds it unwelcome
 * has a switch.
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
