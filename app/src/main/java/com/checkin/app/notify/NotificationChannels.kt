package com.checkin.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.checkin.app.R

/**
 * The single place every notification channel is declared.
 *
 * Channel ids are frozen: the id is the key the OS stores the user's per-channel choices under, so
 * renaming one silently resets the importance, sound and do-not-disturb settings they had chosen.
 *
 * **Importance is frozen too, and more quietly**: the system applies it only at creation, so a
 * changed value here is ignored on any install that already has the channel. Revising it for
 * everyone means a new id, which resets the settings above. Get these right before an install base.
 */
object NotificationChannels {

    const val TIMER = "checkin_timer_channel"
    const val REMINDER = "reminder_channel"
    const val ENGAGEMENT = "engagement_channel"

    fun ensureAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // DEFAULT rather than LOW buys placement, not noise. Below DEFAULT the shade files a
        // notification in the collapsed "Silent" group — and this one carries a deliberately stale
        // `when` (the chronometer needs it pinned to the session's start), so it sinks further the
        // longer the session runs, exactly when it most needs seeing. It stays silent regardless:
        // no channel sound, `setSilent(true)` on the spec, and heads-up needs IMPORTANCE_HIGH.
        manager.createNotificationChannel(
            NotificationChannel(
                TIMER,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(null, null)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )

        // Separate from the reminder channel on purpose: the two are silenced for different reasons,
        // and muting optional encouragement must not also mute the reminder that catches a session
        // the user forgot to close.
        manager.createNotificationChannel(
            NotificationChannel(
                ENGAGEMENT,
                context.getString(R.string.engagement_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.engagement_channel_description)
            },
        )
    }
}
