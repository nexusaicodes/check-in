package com.checkin.app.notify

/**
 * Every notification id the app posts under.
 *
 * Ids are a namespace shared by the foreground service and the engagement layer, and posting twice
 * under one id replaces rather than adds. They used to be bare literals in two files that had no way
 * to see each other, which is only safe for as long as nobody adds a third sender.
 *
 * Nudge ids are deliberately not listed here — they live on
 * [com.checkin.app.notify.engagement.Nudge] itself, one per constant, so that adding or reordering
 * that enum cannot shuffle them. [NUDGE_BASE] is the floor they are assigned from; everything below
 * it belongs to the service.
 */
object NotificationIds {

    /** The ongoing check-in timer. Foreground-service notification, never dismissible. */
    const val TIMER = 1

    /** The mid-session presence check. */
    const val PRESENCE_CHECK = 2

    /** Nudge ids start here; lower values are reserved for the service's own notifications. */
    const val NUDGE_BASE = 10
}
