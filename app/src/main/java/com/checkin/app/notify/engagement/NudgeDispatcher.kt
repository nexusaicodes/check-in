package com.checkin.app.notify.engagement

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.notify.DismissalTag
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.experiment.VariantAssigner
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource
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

    /**
     * Bypasses eligibility to post [nudge] now. A non-null [variant] overrides the install's own
     * bucket, which the harness needs because bucketing is deterministic per install — without it,
     * every other variant's copy is unreachable on a given device.
     */
    suspend fun forceSend(nudge: Nudge, variant: Int? = null): Nudge?
}

class NudgeDispatcher(
    private val strings: StringResolver,
    private val repository: CheckInRepository,
    private val settings: AttendanceSettings,
    private val prefs: EngagementSettings,
    private val notifier: Notifier,
    private val log: EngagementLog,
    private val timeSource: TimeSource
) : NudgeTrigger {

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /** Returns the nudge sent, or null when nothing was eligible or posting was refused. */
    override suspend fun runOnce(): Nudge? {
        val snapshot = buildSnapshot()
        val nudge = NudgeEligibility.select(snapshot) ?: return null
        return send(nudge, snapshot.nowMillis, variantOverride = null)
    }

    override suspend fun forceSend(nudge: Nudge, variant: Int?): Nudge? =
        send(nudge, timeSource.nowMillis(), variantOverride = variant)

    private suspend fun send(nudge: Nudge, nowMillis: Long, variantOverride: Int?): Nudge? {
        val variantCount = NudgeCatalog.variants(nudge).size
        val variant = variantOverride?.mod(variantCount)
            ?: VariantAssigner.assign(prefs.installId(), nudge.name, variantCount)
        val copy = NudgeCatalog.variant(nudge, variant)

        val posted = notifier.show(
            NotificationSpec(
                id = nudge.notificationId,
                channelId = NotificationChannels.ENGAGEMENT,
                title = strings.get(copy.titleRes),
                body = strings.get(copy.bodyRes),
                launchExtra = CheckInService.EXTRA_CHECK_IN,
                // Swiping a nudge away is the clearest signal it isn't wanted, and the only one the
                // log can't infer from the absence of a check-in.
                dismissal = DismissalTag(EngagementSource.NUDGE, nudge.name, variant)
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
            shownToday = log.shownCountSince(startOfDay)
        )
    }
}
