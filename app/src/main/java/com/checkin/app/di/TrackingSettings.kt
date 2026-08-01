package com.checkin.app.di

import android.content.SharedPreferences
import androidx.core.content.edit
import com.checkin.app.data.TimeSource
import com.checkin.app.data.TrackingPrefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Read/write seam over the `tracking_prefs` settings so ViewModels never touch SharedPreferences
 * directly (makes them pure-JVM testable). All reads/writes go through the centralized
 * [TrackingPrefs] helpers.
 */
interface TrackingSettings {
    fun readTrackingStart(): LocalDate
    fun readTrackingStartOrNull(): LocalDate?

    /** Anchors tracking at today, only if tracking hasn't started yet. */
    fun seedTrackingStartIfNeeded()

    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(): Boolean

    /** Records that the camera prominent-disclosure screen has been shown and accepted. */
    fun markCameraDisclosureSeen()

    /** Whether the launch-time notification permission request has already been made. */
    fun hasAskedNotifications(): Boolean

    /** Records that it has, so the app asks once rather than on every cold start. */
    fun markNotificationsAsked()
}

class SharedPrefsTrackingSettings(private val prefs: SharedPreferences, private val timeSource: TimeSource) :
    TrackingSettings {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Read on every hot-flow emission across all ViewModels, and write-once (seeded at first
    // check-in), so it is worth not going back to disk each time.
    @Volatile private var cachedTrackingStart: LocalDate? = null

    override fun readTrackingStart(): LocalDate = readTrackingStartOrNull() ?: TrackingPrefs.readTrackingStart(prefs)

    override fun readTrackingStartOrNull(): LocalDate? =
        cachedTrackingStart ?: TrackingPrefs.readTrackingStartOrNull(prefs)?.also { cachedTrackingStart = it }

    override fun seedTrackingStartIfNeeded() {
        if (prefs.getString(TrackingPrefs.KEY_TRACKING_START_DATE, null) != null) return
        val today = timeSource.today()
        prefs.edit { putString(TrackingPrefs.KEY_TRACKING_START_DATE, today.format(dateFormatter)) }
        cachedTrackingStart = today
    }

    override fun hasSeenCameraDisclosure(): Boolean = TrackingPrefs.hasSeenCameraDisclosure(prefs)

    override fun markCameraDisclosureSeen() {
        prefs.edit { putBoolean(TrackingPrefs.KEY_CAMERA_DISCLOSURE_SEEN, true) }
    }

    override fun hasAskedNotifications(): Boolean = TrackingPrefs.hasAskedNotifications(prefs)

    override fun markNotificationsAsked() {
        prefs.edit { putBoolean(TrackingPrefs.KEY_NOTIFICATIONS_ASKED, true) }
    }
}
