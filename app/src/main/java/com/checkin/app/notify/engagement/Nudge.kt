package com.checkin.app.notify.engagement

import com.checkin.app.notify.NotificationIds

/**
 * The kinds of engagement notification the app can send. Order is the evaluation priority when more
 * than one is eligible in the same pass — the first match wins, so the most valuable nudge goes
 * first. Names are persisted in the engagement log, so renaming a constant orphans its history.
 *
 * [notificationId] must reference a dedicated constant in [NotificationIds], never derive from this
 * enum's ordinal or position: two nudges sharing an id replace each other in the tray, and reordering
 * this enum must not change any existing id.
 */
enum class Nudge(val notificationId: Int) {
    /** Tracking has started but the user hasn't checked in today, and the trigger hour has passed. */
    NOT_CHECKED_IN_BY(NotificationIds.NUDGE_NOT_CHECKED_IN_BY),
}
