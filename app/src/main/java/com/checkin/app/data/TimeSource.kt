package com.checkin.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Abstraction over wall-clock reads so date-dependent logic is deterministically testable. */
interface TimeSource {
    fun nowMillis(): Long
    fun today(): LocalDate

    /** Emits the current local date immediately, then re-emits whenever it rolls over (at midnight). */
    fun currentDay(): Flow<LocalDate>
}

/**
 * The current local date, re-emitted on every [refresh] tick (screen resume / prefs change) and at
 * each local midnight. The single place the "recompute on resume or at day rollover" trigger lives,
 * shared by every ViewModel so the idiom can't drift between screens.
 */
fun TimeSource.dayTrigger(refresh: Flow<Int>): Flow<LocalDate> =
    combine(refresh, currentDay()) { _, day -> day }

/** Pure timing for the day-rollover flow so it can be unit-tested without a real clock. */
object DayClock {
    /** Cap so a timezone/DST change is picked up within the hour without a broadcast receiver. */
    const val MAX_TICK_MS = 60 * 60 * 1000L

    fun millisUntilNextMidnight(now: ZonedDateTime): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, nextMidnight).toMillis().coerceIn(0L, MAX_TICK_MS)
    }
}

object SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()

    override fun currentDay(): Flow<LocalDate> = flow {
        var last = LocalDate.now()
        emit(last)
        while (true) {
            delay(DayClock.millisUntilNextMidnight(ZonedDateTime.now()))
            val current = LocalDate.now()
            if (current != last) {
                last = current
                emit(current)
            }
        }
    }
}
