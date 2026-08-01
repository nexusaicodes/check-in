package com.checkin.app.data.local

/**
 * One day's completed sessions, rolled up. Room aggregate query result — not an entity.
 *
 * There is deliberately no status alongside it. Days used to be classified PRESENT / HALF_DAY_LEAVE
 * / FULL_DAY_LEAVE against a configurable target, which meant a system whose primary output was a
 * verdict — and since targets are missed as the normal case, mostly a failing one. A day that has an
 * entry here is a day the user showed up; a day that has none is a day they didn't. The hours are
 * still carried, and still shown, but nothing grades them.
 */
data class DailyAggregate(
    val dateKey: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstCheckIn: Long,
    val lastCheckOut: Long?,
)
