package com.checkin.app.notify

/**
 * Every notification id the app posts under, including nudges: posting twice under one id replaces
 * rather than adds, so every sender's ids must be visible in one place.
 *
 * A [com.checkin.app.notify.engagement.Nudge] constant's id must be a dedicated constant here, never
 * derived from the enum's ordinal or position — reordering the enum must not change any existing id.
 */
object NotificationIds {

    /** The ongoing check-in timer. Foreground-service notification, never dismissible. */
    const val TIMER = 1

    /** The periodic "still going?" reminder for an open session; each one replaces the last. */
    const val SESSION_REMINDER = 2

    /**
     * The id every nudge shares in releases that predate per-nudge ids. Nothing posts under it; it is
     * only cancelled, because a notification survives an app update and a nudge left in the tray by
     * such a release could otherwise never be retired.
     */
    const val RETIRED_SHARED_NUDGE = 3

    /** [com.checkin.app.notify.engagement.Nudge.NOT_CHECKED_IN_BY]'s id. */
    const val NUDGE_NOT_CHECKED_IN_BY = 10
}
