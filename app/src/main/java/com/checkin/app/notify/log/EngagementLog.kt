package com.checkin.app.notify.log

import com.checkin.app.notify.engagement.Nudge
import kotlinx.coroutines.flow.Flow

/**
 * The credit decision, kept pure and separate from storage so the Room implementation and any test
 * double share one definition of "did this nudge cause this check-in" rather than each restating it.
 */
object AttributionRules {

    /**
     * A nudge shown at [shownAt] earns credit for an action at [actionAt] when the action came after
     * it, inside [windowMs], nothing has already been credited since that showing, and the user has
     * not swiped a nudge away since it was shown.
     *
     * [latestConvertedAt] is what keeps a conversion rate at or below 100%: without it, every
     * subsequent check-in in the window would be credited to the same notification.
     *
     * [latestDismissedAt] is what keeps it from crediting a rejection. A nudge the user swiped away
     * did not cause the check-in that happened to follow it, and counting it would make the one
     * signal the dismiss receiver exists to capture invisible to the only metric that reads it.
     */
    fun canCredit(
        shownAt: Long,
        actionAt: Long,
        windowMs: Long,
        latestConvertedAt: Long?,
        latestDismissedAt: Long?,
    ): Boolean = actionAt >= shownAt &&
        actionAt - shownAt <= windowMs &&
        (latestConvertedAt == null || latestConvertedAt < shownAt) &&
        (latestDismissedAt == null || latestDismissedAt < shownAt)
}

/**
 * Records what each notification did, so nudges can be judged on whether they actually produce
 * check-ins rather than on whether they were sent.
 *
 * Conversion is attributed in Kotlin rather than SQL because the sessions table lives in a different
 * database — the deliberate cost of keeping engagement data isolated from session data.
 */
interface EngagementLog {
    suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long)

    /**
     * Records a session-reminder event. Kept to its own entry point rather than widening [record],
     * because everything a nudge needs — a variant, a place in the frequency cap, eligibility for
     * conversion credit — is exactly what a reminder must not have.
     */
    suspend fun recordPresenceCheck(event: EngagementEventType, atMillis: Long)

    /**
     * Records a foreground-service or alarm lifecycle event, with a short free-text [detail] stored
     * in the key column (a reason, or an instant). Scoped to [EngagementSource.SERVICE] for the same
     * reason presence rows are scoped: it must be invisible to the nudge cap and to attribution.
     */
    suspend fun recordService(event: ServiceEventType, atMillis: Long, detail: String = "")

    /**
     * Marks a check-in at [atMillis] as converted if a nudge was shown within [windowMs] before it
     * and hasn't already been credited. Returns the nudge credited, or null if the check-in was
     * unprompted.
     */
    suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge?

    /**
     * Records a tap against whichever nudge was most recently shown within [windowMs]. The tap
     * itself carries no identity, so attribution has to come from the log rather than the intent.
     */
    suspend fun recordOpenedForLastShown(atMillis: Long, windowMs: Long): Nudge?

    /** How many nudges have been shown since [since] — the daily frequency cap reads this. */
    suspend fun shownCountSince(since: Long): Int

    fun recent(limit: Int): Flow<List<EngagementEvent>>

    suspend fun clear()

    /** Drops events older than [before]; the log is analytics, not an audit trail. */
    suspend fun prune(before: Long)
}

class RoomEngagementLog(private val dao: EngagementEventDao) : EngagementLog {

    override suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long) {
        dao.insert(
            EngagementEvent(
                at = atMillis,
                key = nudge.name,
                variant = variant,
                event = event.name,
                source = EngagementSource.NUDGE.name,
            ),
        )
    }

    override suspend fun recordPresenceCheck(event: EngagementEventType, atMillis: Long) {
        dao.insert(
            EngagementEvent(
                at = atMillis,
                key = PRESENCE_CHECK_KEY,
                variant = 0,
                event = event.name,
                source = EngagementSource.PRESENCE.name,
            ),
        )
    }

    override suspend fun recordService(event: ServiceEventType, atMillis: Long, detail: String) {
        dao.insert(
            EngagementEvent(
                at = atMillis,
                key = detail,
                variant = 0,
                event = event.name,
                source = EngagementSource.SERVICE.name,
            ),
        )
    }

    override suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val latestConverted = dao.latestOfType(
            EngagementEventType.CONVERTED.name,
            EngagementSource.NUDGE.name,
            shown.at,
        )?.at
        // Any nudge swiped away since the showing, not only this one: `latestOfType` is not scoped by
        // key, and a dismissal inside the window reads as a rejection whichever nudge it landed on.
        // Under-crediting is the direction this log errs in deliberately.
        val latestDismissed = dao.latestOfType(
            EngagementEventType.DISMISSED.name,
            EngagementSource.NUDGE.name,
            shown.at,
        )?.at
        if (!AttributionRules.canCredit(shown.at, atMillis, windowMs, latestConverted, latestDismissed)) {
            return null
        }

        val nudge = shown.toNudge() ?: return null
        record(nudge, shown.variant, EngagementEventType.CONVERTED, atMillis)
        return nudge
    }

    override suspend fun recordOpenedForLastShown(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val nudge = shown.toNudge() ?: return null
        record(nudge, shown.variant, EngagementEventType.OPENED, atMillis)
        return nudge
    }

    private suspend fun lastShownWithin(atMillis: Long, windowMs: Long): EngagementEvent? = dao.latestOfType(
        EngagementEventType.SHOWN.name,
        EngagementSource.NUDGE.name,
        atMillis - windowMs,
    )

    /** Null when the stored name no longer maps to a nudge — a renamed or removed experiment. */
    private fun EngagementEvent.toNudge(): Nudge? = Nudge.entries.firstOrNull { it.name == key }

    override suspend fun shownCountSince(since: Long): Int =
        dao.countOfTypeSince(EngagementEventType.SHOWN.name, EngagementSource.NUDGE.name, since)

    override fun recent(limit: Int): Flow<List<EngagementEvent>> = dao.recent(limit)

    override suspend fun clear() = dao.clear()

    override suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
