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
import com.checkin.app.MainActivity
import com.checkin.app.R
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class CheckInService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var startTime: Long = 0
    private var sessionId: Long = -1
    private var reminderAt: Long = 0
    private var reminderFired: Boolean = false

    companion object {
        const val CHANNEL_ID = "checkin_timer_channel"
        const val REMINDER_CHANNEL_ID = "reminder_channel"
        const val NOTIFICATION_ID = 1
        const val REMINDER_NOTIFICATION_ID = 2
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_REARM_REMINDER = "REARM_REMINDER"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_PRESENCE_CHECK = "presence_check"
        const val PREFS_NAME = "checkin_timer_prefs"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"
        const val KEY_REMINDER_AT = "reminder_at"
        const val KEY_REMINDER_FIRED = "reminder_fired"
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
                startTime = System.currentTimeMillis()
                scheduleReminder(startTime)
                saveState()

                startForeground(NOTIFICATION_ID, createNotification(0))
                startTimer()
            }
            ACTION_STOP -> {
                clearState()
                cancelReminderNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_REARM_REMINDER -> {
                // Re-auth confirmed presence: schedule the next check and drop the current reminder.
                if (startTime == 0L && !restoreState()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                scheduleReminder(System.currentTimeMillis())
                saveState()
                cancelReminderNotification()
                if (timerJob == null) {
                    startForeground(NOTIFICATION_ID, createNotification(0))
                    startTimer()
                }
            }
            else -> {
                // Restore state if the service was killed and restarted.
                if (restoreState()) {
                    startForeground(NOTIFICATION_ID, createNotification(0))
                    startTimer()
                }
            }
        }
        return START_STICKY
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification(elapsed))

                if (!reminderFired && reminderAt > 0L && System.currentTimeMillis() >= reminderAt) {
                    notificationManager.notify(REMINDER_NOTIFICATION_ID, createReminderNotification())
                    reminderFired = true
                    saveState()
                }

                delay(1000)
            }
        }
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

        val stopIntent = Intent(this, CheckInService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(TimeFormat.hms(elapsedMillis))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_action_stop), stopPendingIntent)
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
        }
    }

    private fun clearState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { clear() }
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
        return true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }
}
