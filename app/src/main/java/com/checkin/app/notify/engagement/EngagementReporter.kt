package com.checkin.app.notify.engagement

import com.checkin.app.notify.Notifier
import com.checkin.app.notify.log.EngagementLog

/**
 * The one hook app code calls to report what the user did, so the engagement layer stays a listener
 * rather than something the check-in paths have to know the internals of.
 *
 * [onCheckedIn] exists because a check-in is reachable two ways — the Check-In screen and a nudge tap
 * — and both matter to the engagement layer identically. Wiring it only to the notification path
 * would credit none of the check-ins a nudge prompted indirectly, and would leave an acted-on nudge
 * sitting in the shade.
 */
interface EngagementReporter {

    /** A nudge was tapped: attribute the open to whichever nudge was most recently shown. */
    suspend fun onNudgeOpened(atMillis: Long)

    /** A session was opened, by any path: retire a now-stale nudge and credit it if attributable. */
    suspend fun onCheckedIn(atMillis: Long)
}

class DefaultEngagementReporter(
    private val notifier: Notifier,
    private val log: EngagementLog,
    private val conversionWindowMs: Long = CONVERSION_WINDOW_MS
) : EngagementReporter {

    override suspend fun onNudgeOpened(atMillis: Long) {
        log.recordOpenedForLastShown(atMillis, conversionWindowMs)
    }

    override suspend fun onCheckedIn(atMillis: Long) {
        // A nudge asking for a check-in is stale the moment one happens. Left posted, tapping it
        // later puts the user through the full presence gate and then resolves to nothing, which
        // reads as a check-in that silently failed.
        notifier.cancel(NudgeDispatcher.NOTIFICATION_ID)
        log.recordConversionIfAttributable(atMillis, conversionWindowMs)
    }

    companion object {
        /**
         * How long after a nudge a check-in can still be credited to it. Long enough to cover "saw
         * it, acted a couple of hours later"; short enough that the next morning's unprompted
         * check-in isn't attributed to yesterday's notification.
         */
        const val CONVERSION_WINDOW_MS = 4 * 60 * 60 * 1000L
    }
}
