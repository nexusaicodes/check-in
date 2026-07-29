package com.checkin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
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

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        val pending = goAsync()
        container.applicationScope.launch {
            try {
                if (container.repository.getActiveSession() == null) return@launch
                container.sessionWatchdog.reviveIfNeeded(source = "boot")
                // Anchored at now, not at the session's start: the check pending before the reboot
                // is gone with the alarm, and re-deriving it from the original anchor could put it
                // in the past and fire the moment the device finishes starting up.
                container.presenceCheckRunner.arm(container.timeSource.nowMillis())
            } finally {
                pending.finish()
            }
        }
    }
}
