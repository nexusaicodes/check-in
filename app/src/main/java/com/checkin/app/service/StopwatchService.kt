package com.checkin.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.checkin.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.content.edit
import java.util.Locale

class StopwatchService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var startTime: Long = 0
    private var sessionId: Long = -1

    companion object {
        const val CHANNEL_ID = "stopwatch_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val PREFS_NAME = "stopwatch_prefs"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"

        const val ACTION_UPDATE_TIME = "com.checkin.app.UPDATE_TIME"
        const val EXTRA_ELAPSED_TIME = "elapsed_time"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                startTime = System.currentTimeMillis()

                // Save to SharedPreferences for persistence
                saveState(sessionId, startTime)

                startForeground(NOTIFICATION_ID, createNotification(0))
                startTimer()
            }
            ACTION_STOP -> {
                clearState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Restore state if service was killed and restarted
                restoreState()?.let { (savedSessionId, savedStartTime) ->
                    sessionId = savedSessionId
                    startTime = savedStartTime
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

                // Update notification
                val notification = createNotification(elapsed)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)

                // Broadcast elapsed time to UI
                broadcastElapsedTime(elapsed)

                delay(1000) // Update every second
            }
        }
    }

    private fun broadcastElapsedTime(elapsed: Long) {
        val intent = Intent(ACTION_UPDATE_TIME).apply {
            putExtra(EXTRA_ELAPSED_TIME, elapsed)
        }
        sendBroadcast(intent)
    }

    private fun createNotification(elapsedMillis: Long): Notification {
        val timeString = formatTime(elapsedMillis)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StopwatchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Check-In Active")
            .setContentText(timeString)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history) // Using system icon
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Check-In",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the current check-in session timer"
            setSound(null, null)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun saveState(sessionId: Long, startTime: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putLong(KEY_SESSION_ID, sessionId)
            putLong(KEY_START_TIME, startTime)
            apply()
        }
    }

    private fun clearState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit { clear() }
    }

    private fun restoreState(): Pair<Long, Long>? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedSessionId = prefs.getLong(KEY_SESSION_ID, -1)
        val savedStartTime = prefs.getLong(KEY_START_TIME, -1)

        return if (savedSessionId != -1L && savedStartTime != -1L) {
            Pair(savedSessionId, savedStartTime)
        } else {
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }
}
