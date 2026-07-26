package com.checkin.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.edit
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.DismissalTag
import com.checkin.app.notify.NotificationAction
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class CheckInService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val repository: CheckInRepository by lazy {
        (application as CheckInApplication).container.repository
    }

    // Analytics only. Presence-check rows are scoped out of the nudge cap and attribution queries, so
    // writing here can't change what the engagement layer decides to send.
    private val engagementLog: EngagementLog by lazy {
        (application as CheckInApplication).container.engagementLog
    }

    // The presence check is posted through the shared seam so it gets the POST_NOTIFICATIONS guard;
    // the ongoing timer is only ever built, because startForeground needs the object in hand and has
    // to succeed with or without the permission.
    private val notifier: Notifier by lazy {
        (application as CheckInApplication).container.notifier
    }
    private val notificationFactory: NotificationFactory by lazy {
        (application as CheckInApplication).container.notificationFactory
    }
    private var timerJob: Job? = null

    // The in-flight DB reconciliation launched by a START_STICKY restore. A later re-arm cancels it
    // before adopting, so a stale pre-resume DB snapshot can't clobber freshly re-armed state.
    private var reconcileJob: Job? = null
    private var startTime: Long = 0
    private var sessionId: Long = -1
    private var reminderAt: Long = 0
    private var reminderFired: Boolean = false

    // Presence-pause state mirrored into the active session row: [pausedMs] is settled unverified time
    // and [pauseStartedAt] (non-null) marks a fired-but-unacknowledged check that freezes the clock.
    private var pausedMs: Long = 0
    private var pauseStartedAt: Long? = null

    companion object {
        /** Cadence of the notification/presence-check loop. */
        private const val TICK_INTERVAL_MS = 1_000L

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_REARM_REMINDER = "REARM_REMINDER"

        /** Either presence-check setting was changed; the running session re-reads and re-arms. */
        const val ACTION_PRESENCE_SETTINGS_CHANGED = "PRESENCE_SETTINGS_CHANGED"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_START_TIME = "START_TIME"
        const val EXTRA_PRESENCE_CHECK = "presence_check"
        const val EXTRA_CHECK_OUT = "check_out"

        /** Set by an engagement nudge tap; opens the gate and checks in on success. */
        const val EXTRA_CHECK_IN = "check_in"

        /**
         * True when a re-arm followed a tap on the presence-check notification, false when it came
         * from the in-app Resume button. Only the former is an acknowledgement *of the notification*,
         * and only it is logged as one.
         */
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val PREFS_NAME = "checkin_timer_prefs"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"
        const val KEY_REMINDER_AT = "reminder_at"
        const val KEY_REMINDER_FIRED = "reminder_fired"
        const val KEY_PAUSED_MS = "paused_ms"
        const val KEY_PAUSE_STARTED_AT = "pause_started_at"
    }

    override fun onCreate() {
        super.onCreate()
        // Channels are registered app-wide by CheckInApplication; ensuring here too keeps the service
        // safe to start on its own (a START_STICKY restart can outlive an Application that hasn't
        // re-run onCreate in the expected order).
        NotificationChannels.ensureAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_START -> handleStart(intent)
        ACTION_STOP -> {
            tearDown()
            START_STICKY
        }
        ACTION_PRESENCE_SETTINGS_CHANGED -> handlePresenceSettingsChanged()
        ACTION_REARM_REMINDER -> handleRearmReminder(intent)
        else -> handleStickyRestart()
    }

    private fun handleStart(intent: Intent): Int {
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        // Share the DB row's check-in instant so this notification timer and the on-screen ticker
        // agree; fall back to now only if the extra is missing.
        startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
        pausedMs = 0
        pauseStartedAt = null
        scheduleReminder(startTime)
        saveState()

        startForeground(NotificationIds.TIMER, buildTimerNotification())
        startTimer()
        return START_STICKY
    }

    /**
     * Either presence-check setting changed. Only meaningful while a session is live; with nothing
     * to restore this is a stray start and the service tears itself down rather than sitting empty.
     */
    private fun handlePresenceSettingsChanged(): Int {
        if (startTime == 0L && !restoreState()) return stopStray()
        reconcileThen { applyPresenceSettingsChange() }
        return START_STICKY
    }

    /**
     * Re-auth confirmed presence. Reconciles against the authoritative DB row — for a warm process
     * and a cold START_STICKY restore alike — before closing the pause and scheduling the next
     * check, so a checked-out session tears down instead of orphaning a ticker.
     */
    private fun handleRearmReminder(intent: Intent): Int {
        if (startTime == 0L && !restoreState()) return stopStray()
        val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
        reconcileThen { rearmReminder(fromNotification) }
        return START_STICKY
    }

    /**
     * START_STICKY re-delivery after a kill: restores the advisory prefs, posts foreground to meet
     * the FGS deadline, then reconciles against the DB (the source of truth).
     */
    private fun handleStickyRestart(): Int {
        if (!restoreState()) return stopStray()
        reconcileThen {
            saveState()
            if (timerJob == null) startTimer()
        }
        return START_STICKY
    }

    /** A start with no session behind it: end the service rather than leave it running empty. */
    private fun stopStray(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    /**
     * Meets the foreground-start deadline, then reconciles the restored fields against the active
     * session row and runs [onAdopted]. A closed or absent row is an orphan ticker and tears down
     * instead of re-posting. Cancels any in-flight reconcile first, so a stale pre-resume snapshot
     * cannot clobber freshly re-armed state.
     */
    private fun reconcileThen(onAdopted: suspend () -> Unit) {
        startForeground(NotificationIds.TIMER, buildTimerNotification())
        reconcileJob?.cancel()
        reconcileJob = serviceScope.launch {
            when (val result = ServiceReconciler.reconcile(repository.getActiveSession())) {
                ServiceReconciler.Result.Stop -> stopReconciledOrphan()
                is ServiceReconciler.Result.Adopt -> {
                    adopt(result)
                    onAdopted()
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                postTimerNotification()
                if (presenceCheckDue()) firePresenceCheck()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * Whether this tick should fire the mid-session presence check.
     *
     * [presenceCheckEnabled] is tested last so the prefs read is short-circuited away on every
     * ordinary tick. It is the backstop for a settings change that never reached the service (a
     * stray start that tore itself down, a reconcile that lost the race): the prefs are always
     * current, so a disabled check can never fire from stale state.
     */
    private fun presenceCheckDue(): Boolean = !reminderFired &&
        reminderAt > 0L &&
        System.currentTimeMillis() >= reminderAt &&
        presenceCheckEnabled()

    /**
     * Posts the mid-session presence check and applies its consequence.
     *
     * A refused post degrades the check to continue mode for the rest of the session rather than
     * pausing anyway: the practical cause is POST_NOTIFICATIONS revoked mid-session, which also
     * takes the ongoing timer notification off the shade, so freezing the clock would penalise the
     * user over a question they were never shown and give them no cue until they next opened the
     * app. The check is still marked fired, or the ticker would retry it every second.
     */
    private fun firePresenceCheck() {
        val pauses = presenceCheckPauses()
        reminderFired = true

        if (!notifier.show(presenceCheckSpec(pauses))) {
            saveState()
            return
        }

        // The instant the question was actually asked, which is not `reminderAt` — that is only when
        // it was *scheduled*. The ticker can reach it arbitrarily late: Doze holds the loop while the
        // screen is off, a START_STICKY restart can re-fire a check whose pause write never landed,
        // and a check switched back on mid-session finds one already overdue. Pausing from the
        // scheduled time would delete hours the user worked while nothing had been asked of them, and
        // sessions are immutable, so there would be no correcting it.
        val firedAt = System.currentTimeMillis()
        logPresenceEvent(EngagementEventType.SHOWN, firedAt)

        if (pauses) {
            // Freeze the clock in-memory immediately and re-render, so the ongoing notification stops
            // accruing this instant instead of drifting forward then snapping back once the async DB
            // write lands. Persist first so a crash can't re-fire the reminder.
            pauseStartedAt = firedAt
            saveState()
            postTimerNotification()
            // Commit the authoritative pause to the DB row. If the process dies before this lands,
            // restart reconciliation reads the un-paused row and re-fires (see adopt()), so no pause
            // is silently lost.
            serviceScope.launch { repository.beginPause(firedAt) }
        } else {
            // Nothing to freeze and nothing to write: the reminder is a question, not a penalty.
            // `checkin_timer_prefs` is then the only record that it fired.
            saveState()
        }
    }

    /**
     * Records a presence-check event, best-effort.
     *
     * The engagement log drives no attendance rule, so a write that fails must not be able to take
     * the foreground service — and with it the user's running timer — down with it. `serviceScope`
     * has no exception handler, so an uncaught throw here would reach the default handler.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun logPresenceEvent(type: EngagementEventType, atMillis: Long) {
        serviceScope.launch {
            try {
                engagementLog.recordPresenceCheck(type, atMillis)
            } catch (e: Exception) {
                // Nothing to recover: analytics is the only thing lost. Deliberately broad and
                // deliberately swallowed — see this function's contract above.
            }
        }
    }

    /** Net worked time so far: wall-clock since check-in minus settled and in-progress paused time. */
    private fun elapsedNow(): Long {
        val now = System.currentTimeMillis()
        val openPause = pauseStartedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return (now - startTime - pausedMs - openPause).coerceAtLeast(0L)
    }

    /**
     * Closes any open pause, schedules the next check, and resumes the ticker/notification.
     *
     * [fromNotification] separates the two ways presence gets re-verified. Both resume the clock
     * identically, but only a tap on the notification is an acknowledgement *of the notification* —
     * logging the in-app Resume button as one would report an open rate for a message the user may
     * never have seen.
     */
    private fun rearmReminder(fromNotification: Boolean) {
        // Captured before scheduleReminder() clears the flag. A re-arm with nothing outstanding
        // (an intent replayed after the session was already acknowledged) is not an acknowledgement
        // of anything and must not be logged as one.
        if (fromNotification && reminderFired) {
            logPresenceEvent(EngagementEventType.OPENED, System.currentTimeMillis())
        }
        resumePauseIfOpen()
        scheduleReminder(System.currentTimeMillis())
        saveState()
        cancelReminderNotification()
        if (timerJob == null) {
            startForeground(NotificationIds.TIMER, buildTimerNotification())
            startTimer()
        }
    }

    /** Overwrites in-memory timer state with the authoritative DB row's values. */
    private fun adopt(result: ServiceReconciler.Result.Adopt) {
        sessionId = result.sessionId
        startTime = result.startTime
        pausedMs = result.pausedMs
        pauseStartedAt = result.pauseStartedAt
        // In pause mode the DB row is the authoritative record that the reminder fired, so re-derive
        // from it: an un-paused row means the pause write never committed before the crash, and the
        // reminder must be allowed to fire again rather than be suppressed by a stale persisted flag.
        //
        // In continue mode there is no such row — a fired reminder writes nothing to `sessions` — so
        // re-deriving would reset the flag on every restart and re-fire the reminder each time. There,
        // the restored `checkin_timer_prefs` value is the only record there is, and it stands. It is
        // advisory, but the cost of losing it is one extra question; no clock is stopped either way.
        if (presenceCheckPauses()) {
            reminderFired = result.pauseStartedAt != null
        }
    }

    /**
     * Applies a change to either presence-check setting to the session that is already running.
     *
     * Without this the settings only reach the service at the next check-in: an armed reminder stays
     * armed after the check is switched off (and, once switched back on, is overdue and fires on the
     * very next tick), a check that was off at check-in can never be armed at all, and — worst — a
     * pause already open is left open, because the only paths that close one are the notification tap
     * and the in-app Resume button. Turning the check off would then cost the user the rest of the
     * session's hours for a question they had just said they didn't want asked.
     */
    private fun applyPresenceSettingsChange() {
        val enabled = presenceCheckEnabled()
        // Neither "don't ask" nor "asking shouldn't cost me anything" can leave the clock stopped
        // over a check already outstanding.
        if (!enabled || !presenceCheckPauses()) {
            resumePauseIfOpen()
        }
        if (enabled) {
            // Re-arm only when nothing is pending: a check already fired is still outstanding, and a
            // reminder still in the future was armed under settings that haven't changed for it.
            val nothingPending =
                !reminderFired && (reminderAt == 0L || System.currentTimeMillis() >= reminderAt)
            if (nothingPending) scheduleReminder(System.currentTimeMillis())
        } else {
            reminderAt = 0L
            reminderFired = false
            cancelReminderNotification()
        }
        saveState()
        postTimerNotification()
    }

    /** Tears down a ticker whose DB session is already closed/absent (no active work to show). */
    private fun stopReconciledOrphan() = tearDown()

    /** Ends the service: no live session is left for it to time. */
    private fun tearDown() {
        // Cancelled first — a tick landing after stopSelf would re-post the very foreground
        // notification the next lines remove.
        timerJob?.cancel()
        timerJob = null
        clearState()
        cancelReminderNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Folds an open pause window into settled paused time and un-freezes the clock. */
    private fun resumePauseIfOpen() {
        val start = pauseStartedAt ?: return
        pausedMs += (System.currentTimeMillis() - start).coerceAtLeast(0L)
        pauseStartedAt = null
        serviceScope.launch { repository.resumeFromPause() }
    }

    /**
     * Sets the next re-auth reminder relative to [anchorMs] (check-in, or the last re-auth). A
     * `reminderAt` of 0 is the "never" value the ticker already tests for, so a user who has turned
     * the presence check off simply never arms one.
     */
    private fun scheduleReminder(anchorMs: Long) {
        reminderAt = if (presenceCheckEnabled()) {
            ReminderScheduler.computeReminderAt(anchorMs, presentThresholdMs())
        } else {
            0L
        }
        reminderFired = false
    }

    /** Today's "present" mark from the effective-target schedule, in millis. */
    private fun presentThresholdMs(): Long =
        TargetSchedule.effectiveTargetMs(AttendancePrefs.readSchedule(attendancePrefs()), LocalDate.now())

    /** Whether the mid-session presence check fires at all. Read live — the user can toggle it mid-session. */
    private fun presenceCheckEnabled(): Boolean = AttendancePrefs.presenceCheckEnabled(attendancePrefs())

    /** Whether an unanswered presence check stops the clock, or merely asks. */
    private fun presenceCheckPauses(): Boolean = AttendancePrefs.presenceCheckPauses(attendancePrefs())

    private fun attendancePrefs() = getSharedPreferences(AttendancePrefs.NAME, MODE_PRIVATE)

    /** The ongoing timer, built rather than posted: `startForeground` takes the object itself. */
    private fun buildTimerNotification(): Notification = notificationFactory.build(timerSpec(elapsedNow()))

    /**
     * Re-issues the ongoing notification. `startForeground` with the same id updates it in place and
     * is exempt from POST_NOTIFICATIONS, unlike the guarded poster — so a permission revoked
     * mid-session leaves the timer ticking rather than frozen at whatever it read at that instant.
     */
    private fun postTimerNotification() {
        startForeground(NotificationIds.TIMER, buildTimerNotification())
    }

    private fun timerSpec(elapsedMillis: Long) = NotificationSpec(
        id = NotificationIds.TIMER,
        channelId = NotificationChannels.TIMER,
        title = getString(R.string.notification_title),
        body = if (pauseStartedAt != null) {
            getString(R.string.notification_paused, TimeFormat.hms(elapsedMillis))
        } else {
            TimeFormat.hms(elapsedMillis)
        },
        actions = listOf(
            // "Check Out" opens the app so the presence gate runs — check-out stays gated, never silent.
            NotificationAction(
                iconRes = R.drawable.ic_stat_check_out,
                label = getString(R.string.notification_action_stop),
                launchExtra = EXTRA_CHECK_OUT,
            ),
        ),
        ongoing = true,
        silent = true,
    )

    /** [pauses] picks the copy: the consequence of ignoring this differs between the two modes. */
    private fun presenceCheckSpec(pauses: Boolean) = NotificationSpec(
        id = NotificationIds.PRESENCE_CHECK,
        channelId = NotificationChannels.REMINDER,
        title = getString(R.string.reminder_title),
        body = getString(
            if (pauses) R.string.reminder_text_paused else R.string.reminder_text_running,
        ),
        launchExtra = EXTRA_PRESENCE_CHECK,
        // Recorded for visibility only — presence rows drive no rule. Swiping this away in pause mode
        // is the user choosing to leave their own clock stopped, which is worth being able to see.
        dismissal = DismissalTag(EngagementSource.PRESENCE, PRESENCE_CHECK_KEY, variant = 0),
    )

    private fun cancelReminderNotification() {
        notifier.cancel(NotificationIds.PRESENCE_CHECK)
    }

    private fun saveState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putLong(KEY_SESSION_ID, sessionId)
            putLong(KEY_START_TIME, startTime)
            putLong(KEY_REMINDER_AT, reminderAt)
            putBoolean(KEY_REMINDER_FIRED, reminderFired)
            putLong(KEY_PAUSED_MS, pausedMs)
            putLong(KEY_PAUSE_STARTED_AT, pauseStartedAt ?: -1L)
        }
    }

    private fun clearState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { clear() }
        startTime = 0
        sessionId = -1
        reminderAt = 0
        reminderFired = false
        pausedMs = 0
        pauseStartedAt = null
    }

    /** Loads persisted state into fields; returns true when a valid active session was restored. */
    private fun restoreState(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedSessionId = prefs.getLong(KEY_SESSION_ID, -1)
        val savedStartTime = prefs.getLong(KEY_START_TIME, -1)
        if (savedSessionId == -1L || savedStartTime == -1L) return false

        sessionId = savedSessionId
        startTime = savedStartTime
        reminderAt = prefs.getLong(KEY_REMINDER_AT, 0)
        reminderFired = prefs.getBoolean(KEY_REMINDER_FIRED, false)
        pausedMs = prefs.getLong(KEY_PAUSED_MS, 0)
        pauseStartedAt = prefs.getLong(KEY_PAUSE_STARTED_AT, -1L).takeIf { it != -1L }
        return true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }
}
