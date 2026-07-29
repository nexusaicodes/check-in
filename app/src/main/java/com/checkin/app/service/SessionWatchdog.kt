package com.checkin.app.service

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.ServiceController
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.ServiceEventType

/**
 * Restarts the foreground service when the database says a session is running and no service is
 * timing it.
 *
 * This state is reachable and was previously terminal. `START_STICKY` is best effort — a force stop,
 * an OEM background-management kill or a crash can all leave the process dead with the row still
 * open — and nothing in the app ever restarted the service afterwards: it was started at check-in
 * and nowhere else. Opening the app did not help, because the Check-In screen renders from the row
 * itself and so kept showing a cheerfully running timer with no service behind it. The session then
 * ran to check-out with no notification, no presence check, and no sign anything was wrong.
 *
 * The revive is best-effort by necessity. Starting a foreground service from the background is
 * restricted, so the call can be refused outright depending on where it is invoked from; the callers
 * are ordered by how likely they are to be allowed — a visible Activity always is, a
 * `BOOT_COMPLETED` receiver is exempt, and the hourly background pass may not be. A refusal is
 * logged rather than thrown, and the next caller tries again.
 */
class SessionWatchdog(
    private val repository: CheckInRepository,
    private val serviceController: ServiceController,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
    /** Injected so the decision is testable without a live service. */
    private val serviceRunning: () -> Boolean = { CheckInService.isRunning },
) {

    /** Returns true when a revive was attempted (whether or not the platform allowed it). */
    suspend fun reviveIfNeeded(source: String): Boolean {
        if (serviceRunning()) return false
        val active = repository.getActiveSession() ?: return false

        val started = serviceController.startTimer(active.id, active.startedAt)
        log.recordService(
            if (started) ServiceEventType.REVIVED else ServiceEventType.DEGRADED,
            timeSource.nowMillis(),
            if (started) source else "revive refused ($source)",
        )
        return true
    }
}
