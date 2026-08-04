package com.checkin.app.ui.checkin

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges a completed check-out to the celebration shown at the UI root.
 *
 * Process-global rather than ViewModel state because **two paths write a check-out** — the Check-In
 * screen's button and the notification's Check Out action, which resolves in
 * [MainActivity][com.checkin.app.MainActivity] through the root presence gate. A celebration owned
 * by `CheckInViewModel` would simply not appear for the notification path, and the user cannot tell
 * which writer closed their session. This is the same shape, and the same reason, as
 * [PresenceCheckSignal][com.checkin.app.service.PresenceCheckSignal].
 *
 * **The day-boundary close deliberately does not raise this.** It runs through
 * `SessionReminderRunner.onDayBoundaryFired` and never reaches either writer, which is exactly
 * right: it closes a session the user forgot about, usually at midnight with the app dead.
 * Congratulating someone for a session the app ended on their behalf would be praise for the one
 * check-out they did not make.
 */
object CheckOutSignal {

    /**
     * What a finished session is worth saying, gathered by the writer at the moment it closed.
     *
     * The figures are carried rather than re-queried because the celebration renders above the nav
     * host, where there is no ViewModel to read them from — and re-reading would race the very
     * write that triggered this.
     */
    data class Completed(
        /** The closed session's recorded duration, read off the stored row, never recomputed. */
        val sessionMs: Long,
        /** Every completed session on the closed session's own day, including this one. */
        val dayTotalMs: Long,
        val daySessionCount: Int,
    )

    /** The session to celebrate, or null when there is nothing to show. */
    val completed = MutableStateFlow<Completed?>(null)

    fun raise(sessionMs: Long, dayTotalMs: Long, daySessionCount: Int) {
        completed.value = Completed(sessionMs, dayTotalMs, daySessionCount)
    }

    /** Retires the celebration once it has been dismissed or has timed out. */
    fun clear() {
        completed.value = null
    }
}

/**
 * Gathers what [closed] is worth saying and raises it, shared by both check-out writers so the two
 * cannot drift into showing different things for the same event.
 *
 * The day figures are read against the **closed session's own** `date_key`, not against today: a
 * session belongs wholly to the day it began on, so one started before midnight and checked out
 * after it reports the day it actually belongs to rather than the empty one it ended in.
 */
suspend fun raiseCheckOutCelebration(repository: CheckInRepository, closed: CheckInSession) {
    val day = repository.getDailySummaries(closed.dateKey, closed.dateKey)[closed.dateKey]
    CheckOutSignal.raise(
        sessionMs = closed.duration ?: 0L,
        dayTotalMs = day?.totalDurationMs ?: 0L,
        daySessionCount = day?.sessionCount ?: 0,
    )
}
