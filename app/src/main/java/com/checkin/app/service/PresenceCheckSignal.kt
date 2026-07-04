package com.checkin.app.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges a reminder-notification tap to the re-auth prompt shown at the UI root. The service posts
 * the reminder; [MainActivity][com.checkin.app.MainActivity] flips this flag on the resulting
 * intent, and the root composable shows the auth gate regardless of the active tab.
 */
object PresenceCheckSignal {
    val requested = MutableStateFlow(false)
}
