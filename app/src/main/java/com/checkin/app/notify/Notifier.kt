package com.checkin.app.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
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
    /** Returns false when the notification could not be posted (permission denied). */
    fun show(spec: NotificationSpec): Boolean
    fun cancel(id: Int)
}

class AndroidNotifier(
    private val context: Context,
    private val factory: NotificationFactory = NotificationFactory(context),
) : Notifier {

    override fun show(spec: NotificationSpec): Boolean {
        // POST_NOTIFICATIONS is runtime-granted and revocable at any time; posting without it is a
        // silent no-op at the platform level, which would otherwise log a phantom SHOWN event.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.notify(spec.id, factory.build(spec))
        return true
    }

    override fun cancel(id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
