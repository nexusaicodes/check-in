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
    CONVERTED,
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
    PRESENCE,

    /**
     * Foreground-service and alarm lifecycle. Recorded for visibility only; it drives no rules.
     *
     * This exists because a session that silently loses its service leaves no trace anywhere: the
     * notification is gone, the DB row still looks open, and the app keeps rendering a running timer
     * from that row. Diagnosing one previously meant inferring backwards from a wrong duration.
     */
    SERVICE,
}

/**
 * What happened to the foreground service or its alarm. Stored in the same `event` column as
 * [EngagementEventType], and safe to share it because every query that reads that column is also
 * scoped to a [EngagementSource] — these names only ever appear against [EngagementSource.SERVICE].
 */
enum class ServiceEventType {
    /** The service entered the foreground for a session. */
    STARTED,

    /** The service tore itself down: check-out, or a reconcile that found no open session. */
    STOPPED,

    /** The watchdog found an open session with no service and restarted it. */
    REVIVED,

    /** A presence-check alarm was set, with the target instant as the detail. */
    ALARM_SET,

    /** A presence-check alarm fired and was handled. */
    ALARM_FIRED,

    /**
     * A platform call was refused or threw and the app carried on. The single most useful row in
     * this table: it is the difference between "the service died" and "the service died *because*".
     */
    DEGRADED,
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
    val source: String = EngagementSource.NUDGE.name,
)

/**
 * The [EngagementEvent.key] the presence check is logged under.
 *
 * Deliberately a bare constant rather than a `Nudge` entry: adding it to that enum would make it
 * selectable by `NudgeEligibility`, listed in the Settings nudge loop, and force-sendable from the
 * debug harness — none of which apply to a session-scoped check the foreground service owns.
 */
const val PRESENCE_CHECK_KEY = "PRESENCE_CHECK"
