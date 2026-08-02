package com.checkin.app.di

import android.content.SharedPreferences
import androidx.core.content.edit
import com.checkin.app.data.PromptPrefs

/**
 * Read/write seam over the `prompt_prefs` settings so callers never touch SharedPreferences
 * directly (makes them pure-JVM testable). All reads/writes go through the centralized
 * [PromptPrefs] helpers.
 *
 * It carries the one-time prompt flags and nothing else. The tracking start is deliberately absent:
 * it is derived from the sessions table, so there is no setting to seed or read.
 */
interface PromptSettings {
    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(): Boolean

    /** Records that the camera prominent-disclosure screen has been shown and accepted. */
    fun markCameraDisclosureSeen()

    /** Whether the launch-time notification permission request has already been made. */
    fun hasAskedNotifications(): Boolean

    /** Records that it has, so the app asks once rather than on every cold start. */
    fun markNotificationsAsked()
}

class SharedPrefsPromptSettings(private val prefs: SharedPreferences) : PromptSettings {

    override fun hasSeenCameraDisclosure(): Boolean = PromptPrefs.hasSeenCameraDisclosure(prefs)

    override fun markCameraDisclosureSeen() {
        prefs.edit { putBoolean(PromptPrefs.KEY_CAMERA_DISCLOSURE_SEEN, true) }
    }

    override fun hasAskedNotifications(): Boolean = PromptPrefs.hasAskedNotifications(prefs)

    override fun markNotificationsAsked() {
        prefs.edit { putBoolean(PromptPrefs.KEY_NOTIFICATIONS_ASKED, true) }
    }
}
