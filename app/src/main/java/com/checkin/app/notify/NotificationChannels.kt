package com.checkin.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.checkin.app.R

/**
 * The single place every notification channel is declared.
 *
 * The two ids predate this registry and are kept verbatim: a channel id is the key the OS stores the
 * user's per-channel choices under, so renaming one silently resets the importance, sound and
 * do-not-disturb settings an existing user had already chosen.
 */
object NotificationChannels {

    const val TIMER = "checkin_timer_channel"
    const val REMINDER = "reminder_channel"
    const val ENGAGEMENT = "engagement_channel"

    fun ensureAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                TIMER,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
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
