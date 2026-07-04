package com.checkin.app.service

import kotlin.random.Random

/** Pure scheduling math for the forgot-to-punch-out re-auth reminder. */
object ReminderScheduler {

    /**
     * The reminder fires once elapsed time passes 50% of the day's present mark, at a random offset
     * within the `[50%, 100%]` window. Returns the absolute epoch-millis instant.
     *
     * @param startMs session anchor time (epoch millis) — punch-in, or the last re-auth
     * @param presentThresholdMs the day's "present" mark in millis
     * @param random source of the offset within the window (injected for tests)
     */
    fun computeReminderAt(
        startMs: Long,
        presentThresholdMs: Long,
        random: Random = Random.Default
    ): Long {
        val halfMark = presentThresholdMs / 2
        val windowSpan = presentThresholdMs - halfMark
        val offset = if (windowSpan > 0) random.nextLong(windowSpan + 1) else 0L
        return startMs + halfMark + offset
    }
}
