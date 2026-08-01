package com.checkin.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.edit
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.data.TimeSource
import com.checkin.app.notify.NotificationAction
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows the ongoing timer for an active session.
 *
 * The service used to do considerably more. It ran a one-second loop that re-issued the notification
 * to advance its clock by hand and polled for the presence check on the same tick. Both jobs have
 * moved: the platform draws the elapsed time from a single post (see [NotificationSpec.chronometerBase])
 * and the session's reminder and day-boundary close run off alarms ([SessionReminderRunner]). What
 * remains posts on state changes only — a handful of times per session instead of once a second,
 * which over a long session was tens of thousands of main-thread binder calls into the system, tens
 * of thousands of chances for one of them to throw inside a scope that had no exception handler, and
 * the behavioural signature that OEM background management kills apps for.
 *
 * The database row remains authoritative for everything that ends up in a session's duration; the
 * fields here and the `checkin_timer_prefs` mirror are a cache for rendering.
 */
class CheckInService : Service() {

    /**
     * The scope every command runs on.
     *
     * Both halves of the context are load-bearing. The handler stops a refused or throwing platform
     * call from taking the process — and with it the user's running session — down with it. The
     * **supervisor** job is what stops that same throw from taking the *scope* down: an exception
     * handler reports a failure, it does not contain one, so under a plain `Job()` the first throw
     * cancels the scope for good and every later command becomes a silent no-op. That is worse than
     * the crash it replaces — the reconcile that would tear down an orphaned notification launches
     * onto a dead scope, and nothing happens, then or ever.
     */
    private val serviceScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() +
            CoroutineExceptionHandler { _, throwable -> logDegraded("scope: ${throwable.javaClass.simpleName}") },
    )

    private val container by lazy { (application as CheckInApplication).container }
    private val repository by lazy { container.repository }
    private val notifier: Notifier by lazy { container.notifier }
    private val notificationFactory: NotificationFactory by lazy { container.notificationFactory }
    private val sessionReminder: SessionReminderRunner by lazy { container.sessionReminderRunner }

    /** The same injectable clock the rest of the app reads, rather than a direct platform call. */
    private val timeSource: TimeSource by lazy { container.timeSource }

    // Analytics only. Service rows are scoped out of the nudge cap and attribution queries, so
    // writing here can't change what the engagement layer decides to send.
    private val engagementLog: EngagementLog by lazy { container.engagementLog }

    /** The in-flight DB reconciliation. A later command cancels it so a stale snapshot can't win. */
    private var reconcileJob: Job? = null

    /**
     * The in-flight arming of the session's alarms.
     *
     * Tracked for one reason: check-out has to be able to cancel it. Untracked, a session checked out
     * within moments of starting — a mistap, or a check-in the user immediately reverses — would run
     * [tearDown]'s cancel first and then let this coroutine schedule alarms behind it, leaving
     * wake-ups standing over a closed session. They drop themselves on firing, but there is no
     * reason to wake the device to find that out.
     */
    private var armJob: Job? = null
    private var startTime: Long = 0
    private var sessionId: Long = -1

    companion object {
        /**
         * Whether a session currently has a **foreground notification** behind it in this process.
         *
         * The watchdog reads this to decide whether a session has lost its timer, so it has to track
         * the notification and not merely the existence of a `Service` object. Those are not the same
         * state: [enterForeground] is guarded, and a caught `startForeground` failure leaves this
         * instance alive with nothing on the shade — the exact condition the watchdog exists to
         * repair, which a flag set in `onCreate` would have reported as healthy forever.
         *
         * A killed process resets it to false on restart, which is the other signal wanted.
         * Deliberately not `ActivityManager.getRunningServices`, which is deprecated and no longer
         * reports other processes' services reliably.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        /** The watchdog found an open session with no service. Distinct from [ACTION_START]. */
        const val ACTION_REVIVE = "REVIVE"

        /** Something outside the service changed the row; re-read it and redraw the notification. */
        const val ACTION_REFRESH = "REFRESH"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_START_TIME = "START_TIME"
        const val EXTRA_CHECK_OUT = "check_out"

        /** Set by an engagement nudge tap; opens the gate and checks in on success. */
        const val EXTRA_CHECK_IN = "check_in"
        const val PREFS_NAME = "checkin_timer_prefs"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"
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
            START_NOT_STICKY
        }
        ACTION_REVIVE -> handleRevive(intent)
        ACTION_REFRESH -> reconcileThen { }
        else -> handleStickyRestart()
    }

    private fun handleStart(intent: Intent): Int {
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        // Share the DB row's check-in instant so this notification and the on-screen ticker agree;
        // fall back to now only if the extra is missing.
        startTime = intent.getLongExtra(EXTRA_START_TIME, timeSource.nowMillis())
        saveState()

        enterForeground()
        logService(ServiceEventType.STARTED, sessionId.toString())
        armJob?.cancel()
        armJob = serviceScope.launch { sessionReminder.arm(startTime) }
        return START_STICKY
    }

    /**
     * Restores the notification for a session that is already running, after its service was killed.
     *
     * Deliberately **not** [ACTION_START]. That path is written for a fresh check-in: it takes the
     * session's timing from the intent and re-arms the alarms from it, which is correct for a
     * session that has not started yet and wrong for one already running — re-arming would push the
     * next reminder a full interval away from the revive rather than from the session.
     *
     * It also leaves the alarms strictly alone. A killed process does not cancel alarms, so there is
     * nothing to re-arm. Reboots are the exception — they *do* clear alarms — which is why
     * [BootReceiver] arms explicitly rather than relying on this.
     */
    private fun handleRevive(intent: Intent): Int {
        // The prefs mirror first, so the notification posted inside the foreground-start deadline is
        // not blank. Falling back to the extras only matters if it was cleared; the reconcile
        // corrects either way.
        if (startTime == 0L) restoreState()
        if (startTime == 0L) {
            sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
            startTime = intent.getLongExtra(EXTRA_START_TIME, 0L)
        }
        return reconcileThen { }
    }

    /**
     * `START_STICKY` re-delivery after a kill: restore the advisory mirror for an immediate redraw,
     * then reconcile against the DB.
     *
     * The restore is no longer allowed to *veto* the restart. It used to: an empty or cleared
     * `checkin_timer_prefs` returned early and stopped the service without ever asking the database,
     * which inverted the rule that the row is authoritative — precisely in the situation where the
     * cache is least trustworthy and the row most.
     */
    private fun handleStickyRestart(): Int = reconcileThen { }

    /**
     * Meets the foreground-start deadline, then reconciles against the active session row and runs
     * [onAdopted]. A closed or absent row is an orphan and tears down instead of re-posting.
     */
    private fun reconcileThen(onAdopted: suspend () -> Unit): Int {
        // The advisory mirror is read first purely so the notification that has to be posted inside
        // the foreground-start deadline shows the right elapsed time rather than counting from the
        // epoch. The DB read below then overwrites whatever it said.
        if (startTime == 0L) restoreState()
        enterForeground()
        reconcileJob?.cancel()
        reconcileJob = serviceScope.launch {
            when (val result = ServiceReconciler.reconcile(repository.getActiveSession())) {
                ServiceReconciler.Result.Stop -> tearDown()
                is ServiceReconciler.Result.Adopt -> {
                    adopt(result)
                    onAdopted()
                    saveState()
                    postTimerNotification()
                }
            }
        }
        return START_STICKY
    }

    /** Overwrites in-memory render state with the authoritative DB row's values. */
    private fun adopt(result: ServiceReconciler.Result.Adopt) {
        sessionId = result.sessionId
        startTime = result.startTime
    }

    /** Ends the service: no live session is left for it to show. */
    private fun tearDown() {
        reconcileJob?.cancel()
        reconcileJob = null
        // Before the cancel below, or the arming it races with would outlive it.
        armJob?.cancel()
        armJob = null
        isRunning = false
        sessionReminder.cancel()
        logService(ServiceEventType.STOPPED, sessionId.toString())
        clearState()
        cancelReminderNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Delegates to [SessionClock], which is where the arithmetic is unit-tested — a Service is not,
    // and this is the number the user actually reads off the notification.
    private fun chronometerBase(): Long? = SessionClock.chronometerBase(startTime)

    /**
     * Enters (or updates) the foreground state.
     *
     * Guarded because every reason this can throw is a reason to keep the session alive rather than
     * crash: a background-start refusal, a service the system has already demoted, a foreground-type
     * restriction. The old code called this once a second, unguarded, in a scope with no exception
     * handler — tens of thousands of opportunities per session for one throw to kill the process,
     * take the notification off the shade with it, and leave the row open with nothing timing it.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun enterForeground() {
        try {
            startForeground(NotificationIds.TIMER, buildTimerNotification())
            isRunning = true
        } catch (e: Exception) {
            // Cleared, not left standing: this instance is alive but has no notification, and saying
            // otherwise is what would keep the watchdog from putting one back.
            isRunning = false
            logDegraded("startForeground: ${e.javaClass.simpleName}")
        }
    }

    private fun postTimerNotification() = enterForeground()

    /** The ongoing timer, built rather than posted: `startForeground` takes the object itself. */
    private fun buildTimerNotification(): Notification = notificationFactory.build(timerSpec())

    private fun timerSpec() = NotificationSpec(
        id = NotificationIds.TIMER,
        channelId = NotificationChannels.TIMER,
        title = getString(R.string.notification_title),
        // The elapsed time comes from the platform chronometer in the timestamp slot, so printing it
        // here as well would show it twice — and the copy here would be the frozen one, since
        // nothing re-posts on a timer any more.
        body = getString(R.string.notification_running),
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
        chronometerBase = chronometerBase(),
    )

    private fun cancelReminderNotification() {
        notifier.cancel(NotificationIds.SESSION_REMINDER)
    }

    /**
     * Records an event, best-effort.
     *
     * The engagement log drives no tracking rule, so a failed write must not take the foreground
     * service — and with it the user's running timer — down.
     *
     * Written on the **application** scope rather than [serviceScope] on purpose. Half of what is
     * worth recording here happens as the service is ending — the `STOPPED` row, and the `DEGRADED`
     * row the scope's own exception handler writes — and a scope cancelled in `onDestroy` would drop
     * exactly those, which are the ones a user would be reporting. The app scope carries a
     * `SupervisorJob` and no exception handler, hence the `catch`.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun logService(type: ServiceEventType, detail: String) {
        val at = timeSource.nowMillis()
        container.applicationScope.launch {
            try {
                engagementLog.recordService(type, at, detail)
            } catch (e: Exception) {
                // Nothing to recover: analytics is the only thing lost.
            }
        }
    }

    private fun logDegraded(detail: String) = logService(ServiceEventType.DEGRADED, detail)

    private fun saveState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putLong(KEY_SESSION_ID, sessionId)
            putLong(KEY_START_TIME, startTime)
        }
    }

    /** Clears the render mirror but leaves the alarm state to [SessionReminderRunner.cancel]. */
    private fun clearState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            remove(KEY_SESSION_ID)
            remove(KEY_START_TIME)
        }
        startTime = 0
        sessionId = -1
    }

    /** Loads the advisory mirror so the first post is not blank while the DB read is in flight. */
    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStartTime = prefs.getLong(KEY_START_TIME, -1)
        if (savedStartTime == -1L) return

        sessionId = prefs.getLong(KEY_SESSION_ID, -1)
        startTime = savedStartTime
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        // Cancels the scope, not just the jobs held by name: nothing launched on it should outlive
        // the service. The log writes deliberately do not run here (see [logService]), so the
        // breadcrumbs for this very teardown still land.
        serviceScope.cancel()
        reconcileJob = null
        armJob = null
        super.onDestroy()
    }
}
