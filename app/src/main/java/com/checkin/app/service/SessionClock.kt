package com.checkin.app.service

/**
 * The two numbers the ongoing timer notification renders, extracted from [CheckInService] so they
 * can be tested.
 *
 * They are the most visible output of the whole service — the figure a user reads to decide whether
 * to check out — and they lived inside an Android `Service`, which is not JVM-unit-testable, so the
 * only way to verify them was to install the app and wait. Both are pure arithmetic over the session
 * row's three fields, so there was never a reason for them to be in there.
 *
 * A session's net worked time is wall-clock since check-in minus every unverified-presence window:
 * the settled ones already folded into `paused_ms`, plus the one still open, if any.
 */
object SessionClock {

    /**
     * The instant a platform chronometer should count up from, or null when there is nothing for one
     * to count.
     *
     * A chronometer can only run forward from a fixed origin, so paused time is expressed by pushing
     * that origin later rather than by re-posting on a timer: an origin of `started_at + paused_ms`
     * reads exactly the net worked time, for free, and stays correct through deep sleep. While a
     * pause is *open* no fixed origin stays correct, so there is no chronometer and the caller falls
     * back to static text frozen at the moment the clock stopped — which is what a stopped clock
     * should look like anyway.
     */
    fun chronometerBase(startTime: Long, pausedMs: Long, pauseStartedAt: Long?): Long? = when {
        // A service posting before it has adopted a row. Counting up from the epoch for the instant
        // before the reconcile corrects it would flash a decades-long timer.
        startTime <= 0L -> null
        pauseStartedAt != null -> null
        else -> startTime + pausedMs
    }

    /**
     * Net worked time at [nowMs]. Never negative: a clock that has run backwards is a corrupt row or
     * a changed system clock, and showing nothing is better than showing a negative duration.
     */
    fun elapsedMs(nowMs: Long, startTime: Long, pausedMs: Long, pauseStartedAt: Long?): Long {
        if (startTime <= 0L) return 0L
        val openPause = pauseStartedAt?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        return (nowMs - startTime - pausedMs - openPause).coerceAtLeast(0L)
    }
}
