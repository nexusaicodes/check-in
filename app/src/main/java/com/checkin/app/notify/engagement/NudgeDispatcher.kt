package com.checkin.app.notify.engagement

import android.content.Context
import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.experiment.VariantAssigner
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.service.CheckInService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Assembles a snapshot, asks [NudgeEligibility] what to send, and posts the result.
 *
 * All of the reads here are read-only observations of attendance state — this layer never writes to
 * the sessions table, and the decision itself stays in the pure rules.
 */
/** The subset the Settings debug harness needs, so the ViewModel doesn't depend on a Context. */
interface NudgeTrigger {
    suspend fun runOnce(): Nudge?
    suspend fun forceSend(nudge: Nudge): Nudge?
}

class NudgeDispatcher(
    private val context: Context,
    private val repository: CheckInRepository,
    private val settings: AttendanceSettings,
    private val prefs: EngagementSettings,
    private val notifier: Notifier,
    private val log: EngagementLog,
    private val timeSource: TimeSource
) : NudgeTrigger {

    companion object {
        const val NOTIFICATION_ID = 3
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /** Returns the nudge sent, or null when nothing was eligible or posting was refused. */
    override suspend fun runOnce(): Nudge? {
        val snapshot = buildSnapshot()
        val nudge = NudgeEligibility.select(snapshot) ?: return null
        return send(nudge, snapshot.nowMillis)
    }

    /** Bypasses eligibility — used by the debug harness to preview copy on demand. */
    override suspend fun forceSend(nudge: Nudge): Nudge? = send(nudge, timeSource.nowMillis())

    private suspend fun send(nudge: Nudge, nowMillis: Long): Nudge? {
        val variantCount = NudgeCatalog.variants(nudge).size
        val variant = VariantAssigner.assign(prefs.installId(), nudge.name, variantCount)
        val copy = NudgeCatalog.variant(nudge, variant)

        val posted = notifier.show(
            NotificationSpec(
                id = NOTIFICATION_ID,
                channelId = NotificationChannels.ENGAGEMENT,
                title = context.getString(copy.titleRes),
                body = context.getString(copy.bodyRes),
                launchExtra = CheckInService.EXTRA_CHECK_IN
            )
        )
        // Notifications can be refused (permission revoked). Logging a SHOWN we never showed would
        // put an un-convertible event in the denominator and understate every conversion rate.
        if (!posted) return null

        prefs.markShown(nudge, nowMillis)
        log.record(nudge, variant, EngagementEventType.SHOWN, nowMillis)
        return nudge
    }

    private suspend fun buildSnapshot(): EngagementSnapshot {
        val now = timeSource.nowMillis()
        val today = timeSource.today()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        val active = repository.getActiveSession()
        val todaySessions = repository.getSessionsByDate(today.format(dateFormatter))
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return EngagementSnapshot(
            nowMillis = now,
            hourOfDay = hour,
            trackingStarted = settings.readTrackingStartOrNull() != null,
            isCheckedIn = active != null,
            hasCheckedInToday = todaySessions.isNotEmpty(),
            enabledNudges = prefs.enabledNudges(),
            lastShownAt = prefs.lastShownAt(),
            // Counted from the log rather than a prefs tally, so the cap survives a prefs wipe and
            // can never drift out of step with what was actually sent.
            shownToday = log.shownCountSince(startOfDay),
            quietHours = prefs.quietHours
        )
    }
}
