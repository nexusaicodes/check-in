package com.checkin.app.notify.log

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

    /** A session alarm was set, with the target instant as the detail. */
    ALARM_SET,

    /** A session alarm fired and was handled. */
    ALARM_FIRED,

    /**
     * A platform call was refused or threw and the app carried on. The single most useful row in
     * this table: it is the difference between "the service died" and "the service died *because*".
     */
    DEGRADED,
}
