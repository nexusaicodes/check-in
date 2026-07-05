package com.checkin.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.checkin.app.CheckInApplication
import com.checkin.app.MainActivity
import com.checkin.app.R
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
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
        const val CHANNEL_ID = "checkin_timer_channel"
        const val REMINDER_CHANNEL_ID = "reminder_channel"
        const val NOTIFICATION_ID = 1
        const val REMINDER_NOTIFICATION_ID = 2
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_REARM_REMINDER = "REARM_REMINDER"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_START_TIME = "START_TIME"
        const val EXTRA_PRESENCE_CHECK = "presence_check"
        const val EXTRA_CHECK_OUT = "check_out"
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
        createNotificationChannel()
        createReminderChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                // Share the DB row's check-in instant so this notification timer and the on-screen
                // ticker agree; fall back to now only if the extra is missing.
                startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                pausedMs = 0
                pauseStartedAt = null
                scheduleReminder(startTime)
                saveState()

                startForeground(NOTIFICATION_ID, createNotification(elapsedNow()))
                startTimer()
            }
            ACTION_STOP -> {
                clearState()
                cancelReminderNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_REARM_REMINDER -> {
                // Re-auth confirmed presence. Reconcile against the authoritative DB row — for a warm
                // process and a cold START_STICKY restore alike — before closing the pause and
                // scheduling the next check, so a checked-out session tears down instead of orphaning.
                if (startTime == 0L && !restoreState()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification(elapsedNow()))
                reconcileJob?.cancel()
                reconcileJob = serviceScope.launch {
                    when (val result = ServiceReconciler.reconcile(repository.getActiveSession())) {
                        ServiceReconciler.Result.Stop -> stopReconciledOrphan()
                        is ServiceReconciler.Result.Adopt -> {
                            adopt(result)
                            rearmReminder()
                        }
                    }
                }
            }
            else -> {
                // START_STICKY re-delivery after a kill: restore the advisory prefs, post foreground to
                // meet the FGS deadline, then reconcile against the DB (the source of truth).
                if (!restoreState()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification(elapsedNow()))
                reconcileJob?.cancel()
                reconcileJob = serviceScope.launch {
                    when (val result = ServiceReconciler.reconcile(repository.getActiveSession())) {
                        // DB row already closed/absent (e.g. check-out committed before clearState) —
                        // this is an orphan ticker; tear it down instead of re-posting.
                        ServiceReconciler.Result.Stop -> stopReconciledOrphan()
                        is ServiceReconciler.Result.Adopt -> {
                            adopt(result)
                            saveState()
                            if (timerJob == null) startTimer()
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification(elapsedNow()))

                if (!reminderFired && reminderAt > 0L && System.currentTimeMillis() >= reminderAt) {
                    notificationManager.notify(REMINDER_NOTIFICATION_ID, createReminderNotification())
                    // Freeze the clock in-memory immediately (back-dated to the fire instant) and
                    // re-render, so the ongoing notification stops accruing this instant instead of
                    // drifting forward then snapping back once the async DB write lands. Persist first
                    // so a crash can't re-fire the reminder.
                    reminderFired = true
                    pauseStartedAt = reminderAt
                    saveState()
                    notificationManager.notify(NOTIFICATION_ID, createNotification(elapsedNow()))
                    // Commit the authoritative pause to the DB row. If the process dies before this
                    // lands, restart reconciliation reads the un-paused row and re-fires (see adopt()),
                    // so no pause is silently lost.
                    serviceScope.launch { repository.beginPause(reminderAt) }
                }

                delay(1000)
            }
        }
    }

    /** Net worked time so far: wall-clock since check-in minus settled and in-progress paused time. */
    private fun elapsedNow(): Long {
        val now = System.currentTimeMillis()
        val openPause = pauseStartedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return (now - startTime - pausedMs - openPause).coerceAtLeast(0L)
    }

    /** Closes any open pause, schedules the next check, and resumes the ticker/notification. */
    private fun rearmReminder() {
        resumePauseIfOpen()
        scheduleReminder(System.currentTimeMillis())
        saveState()
        cancelReminderNotification()
        if (timerJob == null) {
            startForeground(NOTIFICATION_ID, createNotification(elapsedNow()))
            startTimer()
        }
    }

    /** Overwrites in-memory timer state with the authoritative DB row's values. */
    private fun adopt(result: ServiceReconciler.Result.Adopt) {
        sessionId = result.sessionId
        startTime = result.startTime
        pausedMs = result.pausedMs
        pauseStartedAt = result.pauseStartedAt
        // Re-derive from the authoritative pause state: if the DB isn't paused, allow the reminder to
        // (re)fire once its time passes. This recovers a reminder that showed but whose pause write
        // never committed before a crash — the stale persisted reminderFired would otherwise suppress it.
        reminderFired = result.pauseStartedAt != null
    }

    /** Tears down a ticker whose DB session is already closed/absent (no active work to show). */
    private fun stopReconciledOrphan() {
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

    /** Sets the next re-auth reminder relative to [anchorMs] (check-in, or the last re-auth). */
    private fun scheduleReminder(anchorMs: Long) {
        reminderAt = ReminderScheduler.computeReminderAt(anchorMs, presentThresholdMs())
        reminderFired = false
    }

    /** Today's "present" mark from the effective-target schedule, in millis. */
    private fun presentThresholdMs(): Long {
        val attendancePrefs = getSharedPreferences(AttendancePrefs.NAME, MODE_PRIVATE)
        return TargetSchedule.effectiveTargetMs(AttendancePrefs.readSchedule(attendancePrefs), LocalDate.now())
    }

    private fun createNotification(elapsedMillis: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Check Out" opens the app so the presence gate runs — check-out stays gated (never silent).
        val checkOutIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CHECK_OUT, true)
        }
        val checkOutPendingIntent = PendingIntent.getActivity(
            this, 2, checkOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val paused = pauseStartedAt != null
        val contentText = if (paused) {
            getString(R.string.notification_paused, TimeFormat.hms(elapsedMillis))
        } else {
            TimeFormat.hms(elapsedMillis)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_action_stop), checkOutPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createReminderNotification(): Notification {
        val presenceIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PRESENCE_CHECK, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, presenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
            .setContentTitle(getString(R.string.reminder_title))
            .setContentText(getString(R.string.reminder_text))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun cancelReminderNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(REMINDER_NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun createReminderChannel() {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.reminder_channel_description)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
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
