package com.checkin.app.notify.log

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** What happened to a notification we sent. */
enum class EngagementEventType {
    /** Posted to the system tray. */
    SHOWN,

    /** The user tapped it. */
    OPENED,

    /** The user swiped it away. */
    DISMISSED,

    /** The user checked in soon enough after a SHOWN for it to plausibly be the cause. */
    CONVERTED
}

/**
 * Which subsystem sent the notification an event belongs to.
 *
 * This exists so the two can share one table without interfering. Nudge frequency capping and
 * conversion attribution both ask "what was shown most recently" — questions that must only ever see
 * [NUDGE] rows. A presence check counted toward the daily cap would silence that day's real nudge,
 * and one sitting at the head of the log would absorb a tap or a check-in that belonged to a nudge.
 */
enum class EngagementSource {
    /** An optional encouragement nudge, opt-in and experiment-tracked. */
    NUDGE,

    /** The mid-session presence check. Recorded for visibility only; it drives no rules. */
    PRESENCE
}

/**
 * One notification lifecycle event. [key], [source] and [event] are stored as names rather than
 * ordinals so reordering an enum can't silently reinterpret history.
 */
@Entity(tableName = "engagement_events")
data class EngagementEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "at")
    val at: Long,

    /** The nudge's enum name for a nudge; [PRESENCE_CHECK_KEY] for the presence check. */
    @ColumnInfo(name = "nudge")
    val key: String,

    /** Which copy variant was used, so conversion can be compared across variants. Always 0 for PRESENCE. */
    @ColumnInfo(name = "variant")
    val variant: Int,

    @ColumnInfo(name = "event")
    val event: String,

    /**
     * Defaulted so the v1→v2 migration can backfill existing rows: everything written before this
     * column existed was, by definition, a nudge.
     */
    @ColumnInfo(name = "source", defaultValue = "NUDGE")
    val source: String = EngagementSource.NUDGE.name
)

/**
 * The [EngagementEvent.key] the presence check is logged under.
 *
 * Deliberately a bare constant rather than a `Nudge` entry: adding it to that enum would make it
 * selectable by `NudgeEligibility`, listed in the Settings nudge loop, and force-sendable from the
 * debug harness — none of which apply to a session-scoped check the foreground service owns.
 */
const val PRESENCE_CHECK_KEY = "PRESENCE_CHECK"
