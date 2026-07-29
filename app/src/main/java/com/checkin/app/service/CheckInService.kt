package com.checkin.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.edit
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.notify.NotificationAction
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.ServiceEventType
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shows the ongoing timer for an active session.
 *
 * The service used to do considerably more. It ran a one-second loop that re-issued the notification
 * to advance its clock by hand and polled for the presence check on the same tick. Both jobs have
 * moved: the platform draws the elapsed time from a single post (see [NotificationSpec.chronometerBase])
 * and the presence check runs off an alarm ([PresenceCheckRunner]). What remains posts on state
 * changes only — a handful of times per session instead of once a second, which over a long session
 * was tens of thousands of main-thread binder calls into the system, tens of thousands of chances
 * for one of them to throw inside a scope that had no exception handler, and the behavioural
 * signature that OEM background management kills apps for.
 *
 * The database row remains authoritative for everything that ends up in a session's duration; the
 * fields here and the `checkin_timer_prefs` mirror are a cache for rendering.
 */
class CheckInService : Service() {

    // The handler is the point: a refused or throwing platform call must degrade this service, not
    // take the process — and with it the user's running session — down with it.
    private val serviceScope = CoroutineScope(
        Dispatchers.Main + Job() +
            CoroutineExceptionHandler { _, throwable -> logDegraded("scope: ${throwable.javaClass.simpleName}") },
    )

    private val container by lazy { (application as CheckInApplication).container }
    private val repository by lazy { container.repository }
    private val notifier: Notifier by lazy { container.notifier }
    private val notificationFactory: NotificationFactory by lazy { container.notificationFactory }
    private val presenceCheck: PresenceCheckRunner by lazy { container.presenceCheckRunner }

    // Analytics only. Service rows are scoped out of the nudge cap and attribution queries, so
    // writing here can't change what the engagement layer decides to send.
    private val engagementLog: EngagementLog by lazy { container.engagementLog }

    /** The in-flight DB reconciliation. A later command cancels it so a stale snapshot can't win. */
    private var reconcileJob: Job? = null
    private var startTime: Long = 0
    private var sessionId: Long = -1

    // Mirrors of the session row's pause accounting, used only to render the notification: [pausedMs]
    // is settled unverified time and [pauseStartedAt] marks a check that has frozen the clock.
    private var pausedMs: Long = 0
    private var pauseStartedAt: Long? = null

    companion object {
        /**
         * Whether a service instance is live in this process. The watchdog reads it to decide
         * whether a session has lost its timer; a killed process resets it to false on restart,
         * which is exactly the signal wanted. Deliberately not `ActivityManager.getRunningServices`,
         * which is deprecated and no longer reports other processes' services reliably.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_REARM_REMINDER = "REARM_REMINDER"

        /** Either presence-check setting was changed; the running session re-reads and re-arms. */
        const val ACTION_PRESENCE_SETTINGS_CHANGED = "PRESENCE_SETTINGS_CHANGED"

        /** Something outside the service changed the row; re-read it and redraw the notification. */
        const val ACTION_REFRESH = "REFRESH"
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
        const val KEY_PAUSED_MS = "paused_ms"
        const val KEY_PAUSE_STARTED_AT = "pause_started_at"
    }

    override fun onCreate() {
        super.onCreate()
        // Channels are registered app-wide by CheckInApplication; ensuring here too keeps the service
        // safe to start on its own (a START_STICKY restart can outlive an Application that hasn't
        // re-run onCreate in the expected order).
        NotificationChannels.ensureAll(this)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_START -> handleStart(intent)
        ACTION_STOP -> {
            tearDown()
            START_NOT_STICKY
        }
        ACTION_PRESENCE_SETTINGS_CHANGED -> handlePresenceSettingsChanged()
        ACTION_REARM_REMINDER -> handleRearmReminder(intent)
        ACTION_REFRESH -> reconcileThen { }
        else -> handleStickyRestart()
    }

    private fun handleStart(intent: Intent): Int {
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        // Share the DB row's check-in instant so this notification and the on-screen ticker agree;
        // fall back to now only if the extra is missing.
        startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
        pausedMs = 0
        pauseStartedAt = null
        saveState()

        enterForeground()
        logService(ServiceEventType.STARTED, sessionId.toString())
        serviceScope.launch { presenceCheck.arm(startTime) }
        return START_STICKY
    }

    /**
     * Either presence-check setting changed. Only meaningful while a session is live — the reconcile
     * tears the service down rather than leaving it running empty if none is.
     */
    private fun handlePresenceSettingsChanged(): Int = reconcileThen { applyPresenceSettingsChange() }

    /**
     * Re-auth confirmed presence. Reconciles against the authoritative DB row before closing the
     * pause and arming the next check, so a checked-out session tears down instead of orphaning a
     * notification.
     */
    private fun handleRearmReminder(intent: Intent): Int {
        val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
        return reconcileThen { rearmReminder(fromNotification) }
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
        pausedMs = result.pausedMs
        pauseStartedAt = result.pauseStartedAt
    }

    /**
     * Closes any open pause, arms the next check, and redraws.
     *
     * [fromNotification] separates the two ways presence gets re-verified. Both resume the clock
     * identically, but only a tap on the notification is an acknowledgement *of the notification* —
     * logging the in-app Resume button as one would report an open rate for a message the user may
     * never have seen.
     */
    private suspend fun rearmReminder(fromNotification: Boolean) {
        // A re-arm with nothing outstanding (an intent replayed after the session was already
        // acknowledged) is not an acknowledgement of anything and must not be logged as one.
        if (fromNotification && pauseStartedAt != null) {
            logPresenceEvent(EngagementEventType.OPENED, System.currentTimeMillis())
        }
        resumePauseIfOpen()
        presenceCheck.arm(System.currentTimeMillis())
        cancelReminderNotification()
    }

    /**
     * Applies a change to either presence-check setting to the session already running.
     *
     * The case that forces this is a check already outstanding: turning it off, or turning off its
     * penalty, has to release the clock it froze. Nothing else can — a pause closes only on a
     * notification tap or the in-app Resume button, neither of which will happen with the check off,
     * so the user would lose the rest of the session over a question they had just declined.
     * Already-settled paused time is never revisited; only the open window is.
     */
    private suspend fun applyPresenceSettingsChange() {
        val enabled = container.settings.presenceCheckEnabled
        if (!enabled || !container.settings.presenceCheckPauses) {
            resumePauseIfOpen()
        }
        if (enabled) {
            presenceCheck.arm(System.currentTimeMillis())
        } else {
            presenceCheck.cancel()
            cancelReminderNotification()
        }
    }

    /** Ends the service: no live session is left for it to show. */
    private fun tearDown() {
        reconcileJob?.cancel()
        reconcileJob = null
        presenceCheck.cancel()
        logService(ServiceEventType.STOPPED, sessionId.toString())
        clearState()
        cancelReminderNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Folds an open pause window into settled paused time and un-freezes the clock. */
    private suspend fun resumePauseIfOpen() {
        val start = pauseStartedAt ?: return
        pausedMs += (System.currentTimeMillis() - start).coerceAtLeast(0L)
        pauseStartedAt = null
        repository.resumeFromPause()
    }

    /**
     * The origin the platform counts up from, or null while paused.
     *
     * A chronometer can only count forward from a fixed instant, so paused time is expressed by
     * pushing that instant later rather than by re-posting on a timer: an origin of
     * `started_at + paused_ms` reads exactly the net worked time. While a pause is *open* there is
     * no fixed origin that stays correct, so the notification falls back to static text frozen at
     * the moment the clock stopped — which is what a frozen clock should look like anyway.
     */
    private fun chronometerBase(): Long? = when {
        // A start with nothing behind it yet — the reconcile below will tear this down. Counting up
        // from the epoch for the instant before it does would flash a decades-long timer.
        startTime == 0L -> null
        pauseStartedAt != null -> null
        else -> startTime + pausedMs
    }

    /** Net worked time so far: wall-clock since check-in minus settled and in-progress paused time. */
    private fun elapsedNow(): Long {
        val now = System.currentTimeMillis()
        val openPause = pauseStartedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return (now - startTime - pausedMs - openPause).coerceAtLeast(0L)
    }

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
        } catch (e: Exception) {
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
        // While running, the elapsed time comes from the platform chronometer in the timestamp slot,
        // so printing it here as well would show it twice — and the copy here would be the frozen
        // one, since nothing re-posts on a timer any more. While paused there is no chronometer (it
        // can only count forward), so the stopped time is printed instead.
        body = if (pauseStartedAt != null) {
            getString(R.string.notification_paused, TimeFormat.hms(elapsedNow()))
        } else {
            getString(R.string.notification_running)
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
        chronometerBase = chronometerBase(),
    )

    private fun cancelReminderNotification() {
        notifier.cancel(NotificationIds.PRESENCE_CHECK)
    }

    /**
     * Records an event, best-effort.
     *
     * The engagement log drives no attendance rule, so a failed write must not take the foreground
     * service — and with it the user's running timer — down.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun logPresenceEvent(type: EngagementEventType, atMillis: Long) {
        serviceScope.launch {
            try {
                engagementLog.recordPresenceCheck(type, atMillis)
            } catch (e: Exception) {
                // Nothing to recover: analytics is the only thing lost.
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun logService(type: ServiceEventType, detail: String) {
        serviceScope.launch {
            try {
                engagementLog.recordService(type, System.currentTimeMillis(), detail)
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
            putLong(KEY_PAUSED_MS, pausedMs)
            putLong(KEY_PAUSE_STARTED_AT, pauseStartedAt ?: -1L)
        }
    }

    /** Clears the render mirror but leaves the presence-check state to [PresenceCheckRunner.cancel]. */
    private fun clearState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            remove(KEY_SESSION_ID)
            remove(KEY_START_TIME)
            remove(KEY_PAUSED_MS)
            remove(KEY_PAUSE_STARTED_AT)
        }
        startTime = 0
        sessionId = -1
        pausedMs = 0
        pauseStartedAt = null
    }

    /** Loads the advisory mirror so the first post is not blank while the DB read is in flight. */
    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStartTime = prefs.getLong(KEY_START_TIME, -1)
        if (savedStartTime == -1L) return

        sessionId = prefs.getLong(KEY_SESSION_ID, -1)
        startTime = savedStartTime
        pausedMs = prefs.getLong(KEY_PAUSED_MS, 0)
        pauseStartedAt = prefs.getLong(KEY_PAUSE_STARTED_AT, -1L).takeIf { it != -1L }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        reconcileJob?.cancel()
        super.onDestroy()
    }
}
