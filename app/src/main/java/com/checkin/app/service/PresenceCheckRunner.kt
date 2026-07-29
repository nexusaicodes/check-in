package com.checkin.app.service

import com.checkin.app.R
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.notify.DismissalTag
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import com.checkin.app.notify.log.ServiceEventType

/**
 * Owns the mid-session presence check: arming it, firing it, and asking again when it is ignored.
 *
 * This used to live inside [CheckInService], polled once a second by the same loop that redrew the
 * notification. That coupled the check to the service's survival twice over — the loop only advanced
 * while the CPU was awake, and it stopped entirely if the process was killed — so a session whose
 * service died overnight was never asked anything again. Moving the check onto an alarm and its
 * handling here breaks both couplings: the alarm wakes the device, and this class needs nothing from
 * the service except an optional nudge to redraw a notification the user can see.
 *
 * Every decision reads the **database**, never the service's in-memory mirror, because the process
 * this runs in may have been created by the alarm broadcast moments earlier.
 */
class PresenceCheckRunner(
    private val repository: CheckInRepository,
    private val settings: AttendanceSettings,
    private val notifier: Notifier,
    private val strings: StringResolver,
    private val schedule: PresenceSchedule,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
) {

    /** What a fired alarm turned out to mean. Returned so the caller and the tests can assert on it. */
    sealed interface Outcome {
        /** The session closed before the alarm landed; the alarm was stale and is now cancelled. */
        data object NoSession : Outcome

        /** The user has turned the check off since it was armed. */
        data object Disabled : Outcome

        /**
         * The post was refused — in practice notifications revoked. Unlike the behaviour this
         * replaced, the check is **not** written off: a retry stays armed, so restoring the
         * permission resumes the checks instead of leaving the session unasked until check-out.
         */
        data object Refused : Outcome

        data class Fired(val pauseOpened: Boolean, val nextAt: Long, val attempt: Int) : Outcome
    }

    /**
     * Arms the first check of a session, anchored at [anchorMs] (check-in, or the last re-auth).
     * Clears any outstanding retry count: whatever was pending has just been answered or superseded.
     */
    suspend fun arm(anchorMs: Long) {
        if (!settings.presenceCheckEnabled) return cancel()

        // Cancelling first clears the outstanding count: a fresh anchor is a fresh question, and the
        // retry escalation from the answered one must not carry into it.
        cancel()
        val at = ReminderScheduler.computeReminderAt(anchorMs, presentThresholdMs())
        schedule.scheduleAt(at)
        log.recordService(ServiceEventType.ALARM_SET, timeSource.nowMillis(), at.toString())
    }

    /** Stops asking: check-out, or the check being switched off. */
    fun cancel() = schedule.cancel()

    /**
     * Handles a fired alarm. Safe to call in a process with no running service.
     *
     * The pause is stamped at the instant the question was actually asked, never at the instant it
     * was scheduled for: an inexact alarm is allowed to land late, and back-dating would delete
     * hours the user worked while nothing had been asked of them, on a row the app gives no way to
     * edit.
     */
    suspend fun onAlarmFired(): Outcome {
        val active = repository.getActiveSession() ?: return stale(Outcome.NoSession)
        if (!settings.presenceCheckEnabled) return stale(Outcome.Disabled)
        return ask(active.pauseStartedAt != null)
    }

    /** An alarm with nothing left to ask about. Dropped rather than left to fire again. */
    private fun stale(outcome: Outcome): Outcome {
        cancel()
        return outcome
    }

    private suspend fun ask(pauseAlreadyOpen: Boolean): Outcome {
        val attempt = schedule.attempts + 1
        val pauses = settings.presenceCheckPauses
        val firedAt = timeSource.nowMillis()

        // Retries are silent so a check ignored overnight accumulates on the shade instead of
        // buzzing every half hour. The first ask still alerts.
        if (!notifier.show(spec(pauses, silent = attempt > 1))) {
            log.recordService(ServiceEventType.DEGRADED, firedAt, "presence post refused")
            // Still re-armed, and deliberately without incrementing the attempt count: nothing was
            // shown, so nothing was ignored, and the escalation must not run out on silence.
            schedule.scheduleAt(PresenceCheckPolicy.retryAt(firedAt, schedule.attempts))
            return Outcome.Refused
        }

        schedule.attempts = attempt
        log.recordPresenceCheck(EngagementEventType.SHOWN, firedAt)

        // Only the first unanswered check opens a pause; the retries are reminders about the same
        // outstanding question, and re-opening would discard the window already accruing.
        val pauseOpened = pauses && !pauseAlreadyOpen
        if (pauseOpened) repository.beginPause(firedAt)

        val nextAt = PresenceCheckPolicy.retryAt(firedAt, attempt)
        schedule.scheduleAt(nextAt)
        log.recordService(ServiceEventType.ALARM_SET, firedAt, nextAt.toString())
        return Outcome.Fired(pauseOpened, nextAt, attempt)
    }

    private fun presentThresholdMs(): Long =
        TargetSchedule.effectiveTargetMs(settings.readSchedule(), timeSource.today())

    /** [pauses] picks the copy: the consequence of ignoring this differs between the two modes. */
    private fun spec(pauses: Boolean, silent: Boolean) = NotificationSpec(
        id = NotificationIds.PRESENCE_CHECK,
        channelId = NotificationChannels.REMINDER,
        title = strings.get(R.string.reminder_title),
        body = strings.get(if (pauses) R.string.reminder_text_paused else R.string.reminder_text_running),
        launchExtra = CheckInService.EXTRA_PRESENCE_CHECK,
        silent = silent,
        // Recorded for visibility only — presence rows drive no rule. Swiping this away in pause
        // mode is the user choosing to leave their own clock stopped, which is worth being able to see.
        dismissal = DismissalTag(EngagementSource.PRESENCE, PRESENCE_CHECK_KEY, variant = 0),
    )
}
