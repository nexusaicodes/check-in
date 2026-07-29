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
 * Identifies a notification in the engagement log so that a user dismissal can be attributed back to
 * it. Carried only by notifications whose dismissal is worth recording.
 */
data class DismissalTag(val source: EngagementSource, val key: String, val variant: Int)

/** A notification to post. Presentation only — the decision to send lives in the engagement rules. */
data class NotificationSpec(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    /** Extra flipped to true on the launch intent, so the Activity knows what the tap meant. */
    val launchExtra: String? = null,
    val actions: List<NotificationAction> = emptyList(),
    /** An ongoing notification can't be swiped away, so it never carries a [dismissal]. */
    val ongoing: Boolean = false,
    val silent: Boolean = false,
    /**
     * Epoch-millis origin for a platform-rendered elapsed counter, or null for static [body] text.
     *
     * Set this and the system draws the ticking clock itself, from one post. The app previously
     * re-issued the notification every second to advance it by hand, which cost ~57,000 binder
     * round-trips over a long session, kept the main thread busy at 1 Hz whenever the device was
     * awake, and gave every one of those calls a chance to throw — while still freezing during deep
     * sleep, because a coroutine `delay` is scheduled on uptime and uptime stops when the CPU does.
     * The platform counter has none of those properties: it costs one post and stays correct across
     * suspend. Paused time is folded in by moving the origin forward rather than by re-posting on a
     * timer (see [com.checkin.app.service.CheckInService.timerSpec]).
     */
    val chronometerBase: Long? = null,
    /**
     * Set to record a user dismissal of this notification.
     *
     * Notifications that carry one are deliberately not auto-cancelling: the platform can deliver a
     * delete intent when an auto-cancel tap removes a notification, which is indistinguishable from
     * a real swipe. Whoever handles the tap cancels it instead, and an app-initiated cancel delivers
     * nothing — so everything that reaches the receiver is a genuine dismissal.
     */
    val dismissal: DismissalTag? = null,
)

/**
 * Seam over the platform notification manager so the engagement layer can be unit-tested without
 * Android, and so posting is refused rather than thrown when notifications aren't permitted.
 */
interface Notifier {
    /**
     * Posts [spec], returning false when it could not be displayed.
     *
     * The return value is load-bearing, not advisory: the presence check stops the user's clock on
     * the strength of it, so "true" has to mean the notification is genuinely on the shade.
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
     * Whether a post to [channelId] would actually be seen.
     *
     * Three separate switches can silence a notification, and `notify` reports none of them — it
     * returns void and drops the post. Checking only the runtime permission was not enough: a user
     * can hold `POST_NOTIFICATIONS` and still have notifications off for the whole app, or have this
     * one channel set to "None", and the app would then record a `SHOWN` for a notification nobody
     * saw. For the presence check that is not merely bad analytics — it opens the pause that stops
     * the user's clock, over a question that was never asked, on a row the app gives no way to edit.
     *
     * A missing channel counts as blocked for the same reason: from Android 8 a post to a channel
     * that was never created is discarded, and minSdk here is well past that.
     */
    private fun canPost(channelId: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannelCompat(channelId)
        return manager.areNotificationsEnabled() &&
            channel != null &&
            channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    override fun cancel(id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
