package com.checkin.app.data.local

/**
 * One day's completed sessions, rolled up. Room aggregate query result — not an entity.
 *
 * There is deliberately no status alongside it. A day that has an entry here is a day the user
 * showed up; a day that has none is a day they didn't. The hours are carried, and shown, but nothing
 * grades them — classifying a day against a target would make the app's primary output a verdict,
 * and a mostly failing one, since targets are missed as the normal case.
 */
data class DailyAggregate(
    val dateKey: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstCheckIn: Long,
    val lastCheckOut: Long?,
)
