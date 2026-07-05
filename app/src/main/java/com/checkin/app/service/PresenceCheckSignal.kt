package com.checkin.app.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges a notification tap to the full-screen presence gate shown at the UI root. The service posts
 * the notification; [MainActivity][com.checkin.app.MainActivity] flips this to the matching [Reason]
 * on the resulting intent, and the root composable shows the auth gate regardless of the active tab.
 */
object PresenceCheckSignal {
    /** Why the root gate is showing — decides what a successful auth does. */
    enum class Reason {
        /** No gate requested. */
        NONE,

        /** Forgot-to-check-out reminder: success re-arms the next check and resumes a paused clock. */
        REAUTH,

        /** Notification "Check Out" action: success checks the active session out. */
        CHECK_OUT
    }

    val request = MutableStateFlow(Reason.NONE)
}
