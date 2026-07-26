package com.checkin.app.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.checkin.app.service.CheckInService

/** Seam over the [CheckInService] foreground-service intents so ViewModels don't hold a Context. */
interface ServiceController {
    fun startTimer(sessionId: Long, startedAt: Long)
    fun stop()

    /**
     * Confirms presence: closes any open pause and schedules the next check.
     *
     * [fromNotification] is true only when the presence-check notification was tapped, and false for
     * the in-app Resume button. The clock resumes either way — the flag decides nothing about the
     * session, only whether the notification is credited with the acknowledgement.
     */
    fun rearm(fromNotification: Boolean)

    /**
     * Tells a running session that a presence-check setting changed.
     *
     * The prefs alone reach the service only at the next check-in, which leaves the current session
     * running under the settings it started with — including an already-open pause that nothing
     * would ever close once the check is switched off. A no-op when no session is running.
     */
    fun presenceSettingsChanged()
}

class DefaultServiceController(private val context: Context) : ServiceController {

    override fun startTimer(sessionId: Long, startedAt: Long) {
        val intent = Intent(context, CheckInService::class.java).apply {
            action = CheckInService.ACTION_START
            putExtra(CheckInService.EXTRA_SESSION_ID, sessionId)
            putExtra(CheckInService.EXTRA_START_TIME, startedAt)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stop() {
        context.startService(
            Intent(context, CheckInService::class.java).apply { action = CheckInService.ACTION_STOP }
        )
    }

    override fun rearm(fromNotification: Boolean) {
        context.startService(
            Intent(context, CheckInService::class.java).apply {
                action = CheckInService.ACTION_REARM_REMINDER
                putExtra(CheckInService.EXTRA_FROM_NOTIFICATION, fromNotification)
            }
        )
    }

    override fun presenceSettingsChanged() {
        context.startService(
            Intent(context, CheckInService::class.java).apply {
                action = CheckInService.ACTION_PRESENCE_SETTINGS_CHANGED
            }
        )
    }
}
