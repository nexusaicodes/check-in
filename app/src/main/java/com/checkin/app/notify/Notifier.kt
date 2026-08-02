package com.checkin.app.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Seam over the platform notification manager so the engagement layer can be unit-tested without
 * Android, and so posting is refused rather than thrown when notifications aren't permitted.
 */
interface Notifier {
    /**
     * Posts [spec], returning false when it could not be displayed.
     *
     * The return value is load-bearing, not advisory: the session reminder decides whether to
     * advance its alert ladder on the strength of it, and the engagement log records a `SHOWN` from
     * it, so "true" has to mean the notification is genuinely on the shade.
     */
    fun show(spec: NotificationSpec): Boolean
    fun cancel(id: Int)
}

class AndroidNotifier(
    private val context: Context,
    private val factory: NotificationFactory = NotificationFactory(context),
) : Notifier {

    override fun show(spec: NotificationSpec): Boolean {
        if (!canPost(spec.channelId)) return false

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.notify(spec.id, factory.build(spec))
        return true
    }

    /**
     * Reads the three switches that can silence a post to [channelId] and hands them to
     * [NotificationDelivery], which owns the decision itself and is unit-tested for each of them.
     */
    private fun canPost(channelId: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return NotificationDelivery.canDeliver(
            permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            appEnabled = manager.areNotificationsEnabled(),
            channelImportance = manager.getNotificationChannelCompat(channelId)?.importance,
        )
    }

    override fun cancel(id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
