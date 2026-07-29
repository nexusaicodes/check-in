package com.checkin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.launch

/**
 * Restores a session that was running when the device restarted.
 *
 * A reboot ends the process and takes the foreground service with it, and `START_STICKY` does not
 * survive one. Without this, a session checked in before a restart stayed open in the database with
 * nothing timing it and no notification, until the user happened to open the app.
 *
 * `BOOT_COMPLETED` is one of the few contexts explicitly permitted to start a foreground service
 * from the background, which is why the revive is attempted here rather than left to the next app
 * launch. The alarm is re-armed too: alarms do not survive a reboot either.
 */
class BootReceiver : BroadcastReceiver() {

    /** See [PresenceAlarmReceiver] for why the `catch` is required rather than tidy. */
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        val pending = goAsync()
        container.applicationScope.launch {
            try {
                if (container.repository.getActiveSession() == null) return@launch
                // Reviving does not touch the alarm — a killed process leaves its alarms standing,
                // so there is normally nothing to re-arm. A reboot is the one case that does clear
                // them, which is why this is the only caller that arms explicitly.
                container.sessionWatchdog.reviveIfNeeded(source = "boot")
                // Anchored at now, not at the session's start: re-deriving from the original anchor
                // would put the instant in the past on any session older than its target, firing the
                // moment the device finishes starting up.
                container.presenceCheckRunner.arm(container.timeSource.nowMillis())
            } catch (e: Exception) {
                runCatching {
                    container.engagementLog.recordService(
                        ServiceEventType.DEGRADED,
                        container.timeSource.nowMillis(),
                        "boot threw: ${e.javaClass.simpleName}",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
