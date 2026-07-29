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

    /**
     * Returns true when a revive was attempted (whether or not the platform allowed it).
     *
     * Never throws. Every caller is a fire-and-forget `launch` on the app-wide scope, which has no
     * exception handler, so anything escaping here would reach the default handler and kill the
     * process — an especially poor outcome for a mechanism whose entire job is recovering from a
     * process that died. A recovery attempt that fails is worth a breadcrumb, not a crash.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun reviveIfNeeded(source: String): Boolean = try {
        attemptRevive(source)
    } catch (e: Exception) {
        runCatching {
            log.recordService(
                ServiceEventType.DEGRADED,
                timeSource.nowMillis(),
                "revive threw ($source): ${e.javaClass.simpleName}",
            )
        }
        false
    }

    private suspend fun attemptRevive(source: String): Boolean {
        if (serviceRunning()) return false
        val active = repository.getActiveSession() ?: return false

        // revive(), not startTimer(): the session is already running, and the check-in path would
        // reset its pause accounting and re-arm an alarm that was never cancelled.
        val started = serviceController.revive(active.id, active.startedAt)
        log.recordService(
            if (started) ServiceEventType.REVIVED else ServiceEventType.DEGRADED,
            timeSource.nowMillis(),
            if (started) source else "revive refused ($source)",
        )
        return true
    }
}
