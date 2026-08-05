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
 * **Importance is frozen too, and more quietly.** The system applies it only when a channel is
 * created; on an install that already has the channel, a changed value here is ignored, because that
 * field belongs to the user once they have had the chance to set it. So an importance is effectively
 * chosen once per install and can only be revised for everyone by minting a new id — which resets
 * every per-channel setting they made. Get these right before an install base exists.
 */
object NotificationChannels {

    const val TIMER = "checkin_timer_channel"
    const val REMINDER = "reminder_channel"
    const val ENGAGEMENT = "engagement_channel"

    fun ensureAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // DEFAULT rather than LOW, and the difference is placement, not noise. Below DEFAULT the
        // shade files a notification in the collapsed "Silent" group at the bottom — and this one
        // carries a *stale* timestamp by construction, since the chronometer needs `when` pinned to
        // the session's start, so it sinks further the longer the session runs. A user who picks up
        // their phone after a call and a dozen messages then has no way to know a session is open,
        // and an unnoticed session runs to the day boundary and records hours nobody worked, onto a
        // row the app deliberately gives no way to edit.
        //
        // It stays completely quiet: `setSound(null, null)` here, `setSilent(true)` on the spec, and
        // DEFAULT is below the IMPORTANCE_HIGH that heads-up requires. Nothing pops, nothing sounds.
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
