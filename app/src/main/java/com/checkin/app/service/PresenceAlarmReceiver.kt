package com.checkin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.launch

/**
 * Receives the presence-check alarm and hands it to [PresenceCheckRunner].
 *
 * Deliberately does its work here rather than delegating to [CheckInService]: the alarm's whole
 * purpose is to be reliable when the service is not, and a broadcast receiver can run in a process
 * the broadcast itself just created — whereas starting a foreground service from the background is
 * restricted and would be refused in exactly that case. Posting a notification and writing the pause
 * need neither a service nor a foreground state.
 *
 * The service is told afterwards only so its notification stops advancing, and only when it is
 * already alive in this process. If it is not, the watchdog picks it up at the next opportunity.
 */
class PresenceAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        // The work is a DB read, a notification and a DB write — past what onReceive may block for.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.presenceCheckRunner.onAlarmFired()
                container.engagementLog.recordService(
                    ServiceEventType.ALARM_FIRED,
                    container.timeSource.nowMillis(),
                )
                // Only a live service has a notification to correct, and only a live service can be
                // sent a command from here without tripping the background-start restriction.
                if (CheckInService.isRunning) container.serviceController.refreshFromDb()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.checkin.app.PRESENCE_CHECK_DUE"
    }
}
