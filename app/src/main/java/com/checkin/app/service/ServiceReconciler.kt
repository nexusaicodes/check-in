package com.checkin.app.service

import com.checkin.app.data.local.CheckInSession

/**
 * Decides how [CheckInService] should reconcile its restored (advisory) timer-prefs state against the
 * authoritative active-session row after a process restart. Pure so it is JVM-unit-testable — the
 * Service itself is not.
 */
object ServiceReconciler {

    sealed interface Result {
        /** No active session in the DB — the timer is an orphan; tear the service down. */
        data object Stop : Result

        /** Adopt the DB row's truth (it wins over any stale timer-prefs mirror). */
        data class Adopt(
            val sessionId: Long,
            val startTime: Long,
            val pausedMs: Long,
            val pauseStartedAt: Long?
        ) : Result
    }

    fun reconcile(dbActive: CheckInSession?): Result =
        dbActive?.let {
            Result.Adopt(
                sessionId = it.id,
                startTime = it.startedAt,
                pausedMs = it.pausedMs,
                pauseStartedAt = it.pauseStartedAt
            )
        } ?: Result.Stop
}
