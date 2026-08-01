package com.checkin.app.service

import com.checkin.app.R
import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.EngagementTag
import com.checkin.app.notify.NotificationAction
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
import java.time.ZoneId

/**
 * Owns what happens to a session while it is open: reminding the user it is still running, and
 * closing it at the day boundary.
 *
 * This replaces the mid-session presence check. That check verified the user was still there and
 * *stopped their clock* when they didn't answer, which meant a question nobody saw silently deleted
 * hours they had worked. Nothing is verified here and nothing is deducted — the reminder only asks,
 * and ignoring it costs nothing at all. The one thing that now ends a forgotten session is the day
 * boundary, which is a fact about the calendar rather than a judgement about the user.
 *
 * Every decision reads the **database**, never the service's in-memory mirror, because the process
 * this runs in may have been created by the alarm broadcast moments earlier.
 */
class SessionReminderRunner(
    private val repository: CheckInRepository,
    private val notifier: Notifier,
    private val strings: StringResolver,
    private val alarms: SessionAlarms,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    /** What a fired alarm turned out to mean. Returned so the caller and the tests can assert on it. */
    sealed interface Outcome {
        /** The session closed before the alarm landed; the alarm was stale and is now cancelled. */
        data object NoSession : Outcome

        /**
         * The post was refused — in practice notifications revoked, switched off for the app, or
         * this channel blocked. The next reminder stays armed and the count is **not** advanced, so
         * restoring notifications resumes the reminders instead of leaving the session silent.
         */
        data object Refused : Outcome

        data class Reminded(val nextAt: Long, val silent: Boolean) : Outcome

        /** The day boundary closed the session, stamped at [atMillis]. */
        data class Closed(val atMillis: Long) : Outcome
    }

    /**
     * Arms both alarms for the currently open session, with the reminder cadence anchored at
     * [anchorMs] (check-in, or a reboot's re-arm). A no-op when nothing is open.
     *
     * The boundary comes from the session's own `date_key`, not from [anchorMs]: a session revived
     * after a reboot must still close at the end of the day it *began*, not the end of the day the
     * device happened to restart on. An instant already in the past is left as-is — the platform
     * delivers a past-due alarm immediately, which is exactly the wanted behaviour for a session
     * that outlived its boundary while the process was dead.
     */
    suspend fun arm(anchorMs: Long) {
        alarms.cancelAll()
        val active = repository.getActiveSession() ?: return

        val reminderAt = SessionSchedule.nextReminderAt(anchorMs)
        alarms.scheduleReminderAt(reminderAt)

        val boundaryAt = SessionSchedule.dayBoundaryOf(active.dateKey, zone())
            ?: SessionSchedule.nextDayBoundaryAfter(anchorMs, zone())
        alarms.scheduleDayBoundaryAt(boundaryAt)

        log.recordService(ServiceEventType.ALARM_SET, timeSource.nowMillis(), "$reminderAt/$boundaryAt")
    }

    /** Stops both alarms: check-out, or a session that turned out not to exist. */
    fun cancel() = alarms.cancelAll()

    /** Handles a fired reminder alarm. Safe to call in a process with no running service. */
    suspend fun onReminderFired(): Outcome {
        repository.getActiveSession() ?: return stale()

        val count = alarms.remindersSent + 1
        // Only the first reminder of a session alerts. A two-hour cadence that buzzes every time
        // would wake a user all night over a session they may have left running deliberately.
        val silent = count > 1
        val firedAt = timeSource.nowMillis()

        if (!notifier.show(reminderSpec(silent))) return refused(firedAt)

        alarms.remindersSent = count
        log.recordPresenceCheck(EngagementEventType.SHOWN, firedAt)

        val nextAt = SessionSchedule.nextReminderAt(firedAt)
        alarms.scheduleReminderAt(nextAt)
        log.recordService(ServiceEventType.ALARM_SET, firedAt, nextAt.toString())
        return Outcome.Reminded(nextAt, silent)
    }

    /**
     * Handles the day boundary: closes the open session and drops both alarms.
     *
     * The check-out is **un-gated**, the only one in the app that is. It is bounded in a way no
     * other check-out is — it can only ever *end* a session, always follows a gated check-in, and
     * writes an instant fixed by the calendar rather than by anything the caller chooses — so there
     * is nothing for a gate to protect. Requiring one would defeat the purpose: the whole point is
     * to close a session the user has forgotten, and a forgotten session is precisely the one nobody
     * is present to authenticate.
     *
     * Stamped from the session's `date_key`, never from the fire time. The alarm is inexact and may
     * land hours late; stamping when it fired would hand a forgotten session hours on a day it does
     * not belong to, on a row the app deliberately gives no way to edit.
     */
    suspend fun onDayBoundaryFired(): Outcome {
        val active = repository.getActiveSession() ?: return stale()

        // A malformed date_key should not strand a session open forever; closing at the boundary of
        // the day the alarm landed in is wrong by at most a day and leaves an editable-looking row
        // rather than an unbounded one.
        val closeAt = SessionSchedule.dayBoundaryOf(active.dateKey, zone())
            ?: SessionSchedule.nextDayBoundaryAfter(active.startedAt, zone())

        repository.checkOutAt(active.id, closeAt)
        alarms.cancelAll()
        notifier.cancel(NotificationIds.SESSION_REMINDER)
        log.recordService(ServiceEventType.STOPPED, timeSource.nowMillis(), "day boundary @$closeAt")
        return Outcome.Closed(closeAt)
    }

    /** An alarm with nothing left to act on. Dropped rather than left to fire again. */
    private fun stale(): Outcome {
        cancel()
        return Outcome.NoSession
    }

    /**
     * The platform would not display the reminder. Logged and re-armed without advancing the count,
     * so the first reminder the user can actually see still alerts — silence is not an answer, and
     * a reminder nobody saw must not be treated as one that was ignored.
     */
    private suspend fun refused(firedAt: Long): Outcome {
        log.recordService(ServiceEventType.DEGRADED, firedAt, "reminder post refused")
        alarms.scheduleReminderAt(SessionSchedule.nextReminderAt(firedAt))
        return Outcome.Refused
    }

    private fun reminderSpec(silent: Boolean) = NotificationSpec(
        id = NotificationIds.SESSION_REMINDER,
        channelId = NotificationChannels.REMINDER,
        title = strings.get(R.string.reminder_title),
        body = strings.get(R.string.reminder_text),
        actions = listOf(
            // Check-out from here runs the root gate exactly like the timer notification's action —
            // an ordinary check-out is still never un-gated. Only the day boundary is.
            NotificationAction(
                iconRes = R.drawable.ic_stat_check_out,
                label = strings.get(R.string.notification_action_stop),
                launchExtra = CheckInService.EXTRA_CHECK_OUT,
            ),
        ),
        silent = silent,
        // Recorded for visibility only — these rows drive no rule, and are scoped out of the nudge
        // cap and attribution queries by their source.
        tag = EngagementTag(EngagementSource.PRESENCE, PRESENCE_CHECK_KEY, variant = 0),
    )
}
