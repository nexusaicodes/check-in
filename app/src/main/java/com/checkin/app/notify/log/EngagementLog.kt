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
     * it, inside [windowMs], and nothing has already been credited since that showing.
     *
     * The last condition is what keeps a conversion rate honest: without it, every subsequent
     * check-in in the window would be credited to the same notification and push the rate past 100%.
     */
    fun canCredit(
        shownAt: Long,
        actionAt: Long,
        windowMs: Long,
        latestConvertedAt: Long?
    ): Boolean =
        actionAt >= shownAt &&
            actionAt - shownAt <= windowMs &&
            (latestConvertedAt == null || latestConvertedAt < shownAt)
}

/**
 * Records what each notification did, so nudges can be judged on whether they actually produce
 * check-ins rather than on whether they were sent.
 *
 * Conversion is attributed in Kotlin rather than SQL because the sessions table lives in a different
 * database — the deliberate cost of keeping engagement data isolated from attendance data.
 */
interface EngagementLog {
    suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long)

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
            EngagementEvent(at = atMillis, nudge = nudge.name, variant = variant, event = event.name)
        )
    }

    override suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val latestConverted = dao.latestOfType(EngagementEventType.CONVERTED.name, shown.at)?.at
        if (!AttributionRules.canCredit(shown.at, atMillis, windowMs, latestConverted)) return null

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

    private suspend fun lastShownWithin(atMillis: Long, windowMs: Long): EngagementEvent? =
        dao.latestOfType(EngagementEventType.SHOWN.name, atMillis - windowMs)

    /** Null when the stored name no longer maps to a nudge — a renamed or removed experiment. */
    private fun EngagementEvent.toNudge(): Nudge? =
        Nudge.entries.firstOrNull { it.name == nudge }

    override suspend fun shownCountSince(since: Long): Int =
        dao.countOfTypeSince(EngagementEventType.SHOWN.name, since)

    override fun recent(limit: Int): Flow<List<EngagementEvent>> = dao.recent(limit)

    override suspend fun clear() = dao.clear()

    override suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
