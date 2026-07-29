package com.checkin.app.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.checkin.app.service.CheckInService

/** Seam over the [CheckInService] foreground-service intents so ViewModels don't hold a Context. */
interface ServiceController {
    /**
     * Starts (or revives) the timer service. Returns false when the platform refused the start —
     * background foreground-service starts are restricted, and the watchdog calls this from contexts
     * where that refusal is a normal outcome to be logged and retried, not a crash.
     */
    fun startTimer(sessionId: Long, startedAt: Long): Boolean
    fun stop()

    /** Tells a running service the session row changed underneath it, so it redraws from the DB. */
    fun refreshFromDb()

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

    override fun startTimer(sessionId: Long, startedAt: Long): Boolean {
        val intent = Intent(context, CheckInService::class.java).apply {
            action = CheckInService.ACTION_START
            putExtra(CheckInService.EXTRA_SESSION_ID, sessionId)
            putExtra(CheckInService.EXTRA_START_TIME, startedAt)
        }
        return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
    }

    override fun stop() = send(CheckInService.ACTION_STOP)

    override fun refreshFromDb() = send(CheckInService.ACTION_REFRESH)

    // Both of these are raised from a visible screen — the presence gate and the Settings screen —
    // so a foreground start is always permitted, and starting rather than merely commanding is what
    // lets them reach a session whose service has been killed. Sending these as plain commands would
    // silently do nothing there, and for the re-arm that means leaving the user's clock paused with
    // no remaining way to release it.
    override fun rearm(fromNotification: Boolean) {
        startOrSend(CheckInService.ACTION_REARM_REMINDER) {
            putExtra(CheckInService.EXTRA_FROM_NOTIFICATION, fromNotification)
        }
    }

    override fun presenceSettingsChanged() = startOrSend(CheckInService.ACTION_PRESENCE_SETTINGS_CHANGED)

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun startOrSend(action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(context, CheckInService::class.java).apply {
            this.action = action
            extras()
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // Refused (background start). The row is still authoritative and the watchdog retries.
        }
    }

    /**
     * Delivers a command to an already-running service.
     *
     * Guarded because `startService` throws when the app is in the background and the service is not
     * already running — a real outcome for every one of these, since each is sent in response to
     * something (an alarm, a notification tap) that may arrive after the service has been killed.
     * There is nothing to command in that case, and the watchdog is what puts the service back.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun send(action: String, extras: Intent.() -> Unit = {}) {
        try {
            context.startService(
                Intent(context, CheckInService::class.java).apply {
                    this.action = action
                    extras()
                },
            )
        } catch (e: Exception) {
            // No service to receive it. The command is advisory in every case; the DB row is truth.
        }
    }
}
