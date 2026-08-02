package com.checkin.app.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.checkin.app.notify.log.EngagementSource

/** A button on a notification. Tapping it opens the Activity with [launchExtra] set. */
data class NotificationAction(val iconRes: Int, val label: String, val launchExtra: String)

/**
 * Identifies a notification in the engagement log, so what the user did with it is attributed to the
 * notification itself rather than inferred from whichever was posted most recently.
 *
 * Carried on both intents a notification hands out: the tap intent and the delete intent. The two use
 * different extra keys because the delete intent's are frozen — a notification posted by an earlier
 * release survives an update holding the `PendingIntent` it was built with, and renaming what the
 * receiver reads drops that notification's dismissal on the floor.
 *
 * Only the delete intent carries [source]. A tap is routed by its own launch extra long before the
 * tag is read, so the handler already knows which subsystem it is holding.
 */
data class EngagementTag(val source: EngagementSource, val key: String, val variant: Int) {
    companion object {
        const val EXTRA_KEY = "engagement_key"
        const val EXTRA_VARIANT = "engagement_variant"
    }
}

/** A notification to post. Presentation only — the decision to send lives in the engagement rules. */
data class NotificationSpec(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    /** Extra flipped to true on the launch intent, so the Activity knows what the tap meant. */
    val launchExtra: String? = null,
    val actions: List<NotificationAction> = emptyList(),
    /**
     * A live status line rather than a message. The user can swipe one away at this minSdk, but it
     * carries no [tag]: dismissing a status line says nothing about whether its content was wanted,
     * which is the only question the engagement log asks of a dismissal.
     */
    val ongoing: Boolean = false,
    val silent: Boolean = false,
    /**
     * Epoch-millis origin for a platform-rendered elapsed counter, or null for static [body] text.
     *
     * The system draws the ticking clock itself from a single post, and keeps counting through deep
     * sleep. Do not advance it by re-posting on a timer instead: that costs a main-thread binder call
     * per second (tens of thousands over a long session), gives each one a chance to throw, and still
     * freezes in deep sleep, because a coroutine `delay` is scheduled on uptime and uptime stops when
     * the CPU does. The origin is the DB row's `started_at`, the same instant the on-screen ticker
     * counts from, so the two agree rather than drifting by the check-in→service-start latency (see
     * [com.checkin.app.service.CheckInService.timerSpec]).
     */
    val chronometerBase: Long? = null,
    /**
     * Set to record what the user does with this notification.
     *
     * Notifications that carry one are deliberately not auto-cancelling: the platform can deliver a
     * delete intent when an auto-cancel tap removes a notification, which is indistinguishable from
     * a real swipe. Whoever handles the tap cancels it instead, and an app-initiated cancel delivers
     * nothing — so everything that reaches the receiver is a genuine dismissal.
     */
    val tag: EngagementTag? = null,
)

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
