package com.checkin.app.ui.checkin

import java.time.Instant
import java.time.ZoneId

/**
 * The part of the day a session began in, used to label its row in the day's list.
 *
 * Seven buckets rather than the usual four, so a day's rows read with some variety — but the variety
 * is **earned by the clock, never picked from a pool**. A record whose wording rotated arbitrarily
 * would invite the reader to look for a distinction that isn't there, and the one thing this list
 * must be is stable: the same session has to carry the same word on every recomposition, scroll and
 * relaunch.
 *
 * It is deliberately keyed to the **start time and nothing else**. Labelling by length instead —
 * a "quick one" against a "deep dive" — would grade the session, which is the one thing nothing in
 * this app is allowed to do.
 */
enum class SessionPeriod {
    EARLY_MORNING,
    MORNING,
    MIDDAY,
    AFTERNOON,
    EVENING,
    NIGHT,
    LATE_NIGHT,
}

/** The period [hour] (0..23, local) falls in. Boundaries are inclusive of their opening hour. */
fun sessionPeriodOfHour(hour: Int): SessionPeriod = when (hour) {
    in EARLY_MORNING_FROM until MORNING_FROM -> SessionPeriod.EARLY_MORNING
    in MORNING_FROM until MIDDAY_FROM -> SessionPeriod.MORNING
    in MIDDAY_FROM until AFTERNOON_FROM -> SessionPeriod.MIDDAY
    in AFTERNOON_FROM until EVENING_FROM -> SessionPeriod.AFTERNOON
    in EVENING_FROM until NIGHT_FROM -> SessionPeriod.EVENING
    in NIGHT_FROM..LAST_HOUR -> SessionPeriod.NIGHT
    // Everything before the early-morning cut, which wraps past midnight rather than starting the
    // day: a session begun at 02:00 belongs to the night that is still running.
    else -> SessionPeriod.LATE_NIGHT
}

/** [sessionPeriodOfHour] for an instant, read in the device zone the times beside it are shown in. */
fun sessionPeriodOf(epochMillis: Long): SessionPeriod = sessionPeriodOfHour(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).hour,
)

private const val EARLY_MORNING_FROM = 5
private const val MORNING_FROM = 8
private const val MIDDAY_FROM = 12
private const val AFTERNOON_FROM = 14
private const val EVENING_FROM = 17
private const val NIGHT_FROM = 21
private const val LAST_HOUR = 23
