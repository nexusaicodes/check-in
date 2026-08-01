package com.checkin.app.service

/**
 * The two numbers the ongoing timer notification renders, extracted from [CheckInService] so they
 * can be tested.
 *
 * They are the most visible output of the whole service — the figure a user reads to decide whether
 * to check out — and they lived inside an Android `Service`, which is not JVM-unit-testable, so the
 * only way to verify them was to install the app and wait.
 *
 * Both used to net out presence-pause windows as well. That mechanism is gone: a session's elapsed
 * time is now simply wall-clock since check-in, and nothing subtracts from it.
 */
object SessionClock {

    /**
     * The instant a platform chronometer should count up from, or null when there is nothing for one
     * to count.
     *
     * Null for a non-positive start: a service posting before it has adopted a row would otherwise
     * count up from the epoch and flash a decades-long timer for the instant before the reconcile
     * corrects it.
     */
    fun chronometerBase(startTime: Long): Long? = startTime.takeIf { it > 0L }

    /**
     * Elapsed time at [nowMs]. Never negative: a clock that has run backwards is a corrupt row or a
     * changed system clock, and showing nothing is better than showing a negative duration.
     */
    fun elapsedMs(nowMs: Long, startTime: Long): Long {
        if (startTime <= 0L) return 0L
        return (nowMs - startTime).coerceAtLeast(0L)
    }
}
