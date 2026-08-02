package com.checkin.app.data

import android.content.SharedPreferences

/**
 * Central definition of the `prompt_prefs` SharedPreferences namespace, its keys, and readers.
 *
 * Everything here records that the app has already raised a one-time prompt, so it doesn't raise it
 * twice. Both flags describe an interaction with *this device*, not anything about the user's
 * record — which is why neither is restored from a cloud backup (see `data_extraction_rules.xml`).
 *
 * Nothing about the record belongs here for that same reason: the day tracking began is read from
 * the sessions table (`CheckInRepository.trackingStartFlow`), never stored, so a restore cannot
 * bring it back without the rows it indexes.
 */
object PromptPrefs {
    const val NAME = "prompt_prefs"
    const val KEY_CAMERA_DISCLOSURE_SEEN = "camera_disclosure_seen"
    const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_CAMERA_DISCLOSURE_SEEN, false)

    /**
     * Whether the launch-time notification permission request has already been made.
     *
     * Persisted rather than derived from the grant state, because "refused" and "not yet asked" look
     * identical through [android.content.pm.PackageManager] — without this the app would re-request
     * on every cold start, and Android silently drops the dialog after two refusals, so the user
     * would see nothing while the app kept asking.
     */
    fun hasAskedNotifications(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)
}
