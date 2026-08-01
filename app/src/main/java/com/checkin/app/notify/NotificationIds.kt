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

    /**
     * The periodic "still going?" reminder for an open session.
     *
     * Keeps the id the mid-session presence check used, deliberately: the two never coexist, and
     * reusing it lets a reminder posted before an update be replaced rather than left beside its
     * successor on the shade.
     */
    const val SESSION_REMINDER = 2

    /**
     * The single id every nudge shared before they were given one each. Nothing posts under it any
     * more, but a notification survives an app update, so it is still cancelled — otherwise a nudge
     * posted by the previous release could never be retired.
     */
    const val RETIRED_SHARED_NUDGE = 3

    /** [com.checkin.app.notify.engagement.Nudge.NOT_CHECKED_IN_BY]'s id. */
    const val NUDGE_NOT_CHECKED_IN_BY = 10
}
