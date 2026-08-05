package com.checkin.app.ui.settings

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.service.CheckInService
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionSchedule
import java.time.ZoneId

/**
 * Gathers the live state a [DebugSnapshot] describes.
 *
 * Its own object rather than four more constructor parameters on [SettingsViewModel]: none of what it
 * reads is settings, and the ViewModel would otherwise be the only thing in the app holding the
 * repository, the alarms and the service flag together — which reads as a dependency the screen has
 * rather than as the one-off inspection it is.
 *
 * Everything here is read fresh on each call. Nothing it touches is reactive — the armed instants are
 * `SharedPreferences` and [CheckInService.isRunning] is a static — and a cached snapshot would be
 * exactly the stale picture the card exists to avoid.
 */
class DebugSnapshotReader(
    private val repository: CheckInRepository,
    private val alarms: SessionAlarms,
    private val timeSource: TimeSource,
    /** Injected for the same reason [com.checkin.app.service.SessionWatchdog] injects it: a live service is not testable. */
    private val serviceRunning: () -> Boolean = { CheckInService.isRunning },
) {

    /**
     * [channels] arrives from the caller rather than being read here, exactly as
     * [com.checkin.app.notify.NotificationDelivery] takes its three switches as parameters: those are
     * platform reads needing a `Context`, and this class is deliberately reachable from the JVM suite.
     */
    suspend fun read(channels: List<ChannelState>): DebugSnapshot {
        val session = repository.getActiveSession()
        return DebugSnapshot(
            nowMs = timeSource.nowMillis(),
            session = session?.let { SessionState(it.id, it.startedAt, it.dateKey) },
            serviceRunning = serviceRunning(),
            nextReminderAt = alarms.nextReminderAt,
            dayBoundaryAt = alarms.dayBoundaryAt,
            remindersSent = alarms.remindersSent,
            expectedDayBoundaryAt = session?.let {
                SessionSchedule.dayBoundaryOf(it.dateKey, ZoneId.systemDefault())
            },
            channels = channels,
        )
    }
}
