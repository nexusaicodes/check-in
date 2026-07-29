package com.checkin.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * The scheduled mid-session presence check: when the next one fires, and how many have gone
 * unanswered.
 *
 * The two belong together because they are only ever meaningful together — a cancelled check has no
 * outstanding count, and a count with no alarm behind it can never be acted on. Keeping them in one
 * seam makes [cancel] atomic by construction rather than by remembering to call two things.
 *
 * The alarm is deliberately **inexact**. The check fires at a random point inside a multi-hour
 * window, so exactness buys nothing — while an exact alarm costs a runtime permission that is denied
 * by default from Android 14, or the install-time alternative that Play restricts to apps whose core
 * function is an alarm clock. Neither describes this app. What the app does need is the *wake*:
 * [AlarmManager.setAndAllowWhileIdle] fires through Doze, which is the one thing the one-second
 * polling loop it replaces could never do — that loop was scheduled on uptime, and uptime stops when
 * the CPU suspends, so an overnight check simply never came due.
 *
 * Deliberately **not** `setAlarmClock`: that is the only API that puts an alarm icon in the status
 * bar and an entry on the lock screen, which would tell every user the app had set them an alarm.
 */
interface PresenceSchedule {
    /** Arms a single wake-up at [atMillis], replacing any alarm already set. */
    fun scheduleAt(atMillis: Long)

    /** Drops the alarm and the outstanding count together. */
    fun cancel()

    /** Checks asked against the currently outstanding question. Zero when nothing is outstanding. */
    var attempts: Int
}

class AndroidPresenceSchedule(private val context: Context) : PresenceSchedule {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    // The service's own namespace: this is live session mechanics, and splitting one session's state
    // across two files would only make it easier for the halves to disagree.
    private val prefs
        get() = context.getSharedPreferences(CheckInService.PREFS_NAME, Context.MODE_PRIVATE)

    override fun scheduleAt(atMillis: Long) {
        // RTC_WAKEUP, not ELAPSED_REALTIME: the check is anchored to a wall-clock instant derived
        // from the session's start, and the rest of the app reasons in wall time throughout.
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
    }

    override fun cancel() {
        alarmManager?.cancel(pendingIntent())
        prefs.edit { remove(KEY_ATTEMPTS) }
    }

    override var attempts: Int
        get() = prefs.getInt(KEY_ATTEMPTS, 0)
        set(value) = prefs.edit { putInt(KEY_ATTEMPTS, value) }

    /**
     * One PendingIntent, reused, so scheduling replaces rather than accumulates and [cancel] can
     * find it. The request code sits in its own band clear of the notification codes in
     * [com.checkin.app.notify.NotificationFactory], which share this process-wide namespace.
     */
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PresenceAlarmReceiver::class.java).setAction(PresenceAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 20_000
        const val KEY_ATTEMPTS = "presence_attempts"
    }
}
