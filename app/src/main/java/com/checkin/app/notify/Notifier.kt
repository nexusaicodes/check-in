package com.checkin.app.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.checkin.app.MainActivity

/** A notification to post. Presentation only — the decision to send lives in the engagement rules. */
data class NotificationSpec(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    /** Extra flipped to true on the launch intent, so the Activity knows what the tap meant. */
    val launchExtra: String? = null,
    val autoCancel: Boolean = true
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

class AndroidNotifier(private val context: Context) : Notifier {

    override fun show(spec: NotificationSpec): Boolean {
        // POST_NOTIFICATIONS is runtime-granted and revocable at any time; posting without it is a
        // silent no-op at the platform level, which would otherwise log a phantom SHOWN event.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            spec.launchExtra?.let { putExtra(it, true) }
        }
        val pending = PendingIntent.getActivity(
            context,
            spec.id,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, spec.channelId)
            // Matches the timer/reminder notifications rather than introducing a second icon.
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
            .setContentIntent(pending)
            .setAutoCancel(spec.autoCancel)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.notify(spec.id, notification)
        return true
    }

    override fun cancel(id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
