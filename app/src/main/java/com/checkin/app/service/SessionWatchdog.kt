package com.checkin.app.service

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.ServiceController
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.ServiceEventType

/**
 * Puts back whatever an open session has lost: its foreground service, its alarms, or both.
 *
 * This state is reachable and was previously terminal. `START_STICKY` is best effort — a force stop,
 * an OEM background-management kill or a crash can all leave the process dead with the row still
 * open — and nothing in the app ever restarted the service afterwards: it was started at check-in
 * and nowhere else. Opening the app did not help, because the Check-In screen renders from the row
 * itself and so kept showing a cheerfully running timer with no service behind it. The session then
 * ran to check-out with no notification and no sign anything was wrong.
 *
 * **The service and the alarms are repaired independently, because they are lost independently.** A
 * force stop and a package replace cancel a package's alarms; a plain process kill does not. The
 * service running says nothing about whether the day-boundary close is still standing, and that
 * close is the only thing that ends a session the user has forgotten — so the alarms are ensured on
 * every pass, before the service is even looked at.
 *
 * The revive is best-effort by necessity. Starting a foreground service from the background is
 * restricted, so the call can be refused outright depending on where it is invoked from; the callers
 * are ordered by how likely they are to be allowed — a visible Activity always is, a
 * `BOOT_COMPLETED` receiver is exempt, and the hourly background pass may not be. A refusal is
 * logged rather than thrown, and the next caller tries again. Re-arming an alarm carries no such
 * restriction, which is the other reason it does not wait behind the service.
 */
class SessionWatchdog(
    private val repository: CheckInRepository,
    private val serviceController: ServiceController,
    private val sessionReminder: SessionReminderRunner,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
    /** Injected so the decision is testable without a live service. */
    private val serviceRunning: () -> Boolean = { CheckInService.isRunning },
) {

    /**
     * Ensures the open session's alarms, then returns true when a service revive was *also*
     * attempted (whether or not the platform allowed it).
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
        // Alarms first, and unconditionally: a package replace clears them while leaving the row
        // open and the service perfectly able to restart itself, so gating this on the service
        // being down is exactly how a session loses its day-boundary close and keeps its timer.
        // Reports false when there is no open session, in which case it has dropped the alarms.
        if (!sessionReminder.ensureArmed(timeSource.nowMillis())) return false

        if (serviceRunning()) return false
        val active = repository.getActiveSession() ?: return false

        // revive(), not startTimer(): that path takes its timing from the intent and re-anchors the
        // reminder cadence from it, which is right for a session that has not begun and wrong for
        // one already running.
        val started = serviceController.revive(active.id, active.startedAt)
        log.recordService(
            if (started) ServiceEventType.REVIVED else ServiceEventType.DEGRADED,
            timeSource.nowMillis(),
            if (started) source else "revive refused ($source)",
        )
        return true
    }
}
