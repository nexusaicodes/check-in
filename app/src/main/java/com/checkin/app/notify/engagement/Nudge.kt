package com.checkin.app.notify.engagement

/**
 * The kinds of engagement notification the app can send. Order is the evaluation priority when more
 * than one is eligible in the same pass — the first match wins, so the most valuable nudge goes
 * first. Names are persisted in the engagement log, so renaming a constant orphans its history.
 */
enum class Nudge {
    /** Tracking has started but the user hasn't checked in today, and the trigger hour has passed. */
    NOT_CHECKED_IN_BY
}
