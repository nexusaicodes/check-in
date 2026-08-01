package com.checkin.app.di

import android.content.SharedPreferences
import androidx.core.content.edit
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.TimeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Read/write seam over the `attendance_prefs` settings so ViewModels never touch SharedPreferences
 * directly (makes them pure-JVM testable). All reads/writes go through the centralized
 * [AttendancePrefs] helpers.
 */
interface AttendanceSettings {
    fun readTrackingStart(): LocalDate
    fun readTrackingStartOrNull(): LocalDate?

    /** Anchors tracking at today, only if tracking hasn't started yet. */
    fun seedTrackingStartIfNeeded()

    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(): Boolean

    /** Records that the camera prominent-disclosure screen has been shown and accepted. */
    fun markCameraDisclosureSeen()
}

class SharedPrefsAttendanceSettings(private val prefs: SharedPreferences, private val timeSource: TimeSource) :
    AttendanceSettings {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Read on every hot-flow emission across all ViewModels, and write-once (seeded at first
    // check-in), so it is worth not going back to disk each time.
    @Volatile private var cachedTrackingStart: LocalDate? = null

    override fun readTrackingStart(): LocalDate = readTrackingStartOrNull() ?: AttendancePrefs.readTrackingStart(prefs)

    override fun readTrackingStartOrNull(): LocalDate? =
        cachedTrackingStart ?: AttendancePrefs.readTrackingStartOrNull(prefs)?.also { cachedTrackingStart = it }

    override fun seedTrackingStartIfNeeded() {
        if (prefs.getString(AttendancePrefs.KEY_TRACKING_START_DATE, null) != null) return
        val today = timeSource.today()
        prefs.edit { putString(AttendancePrefs.KEY_TRACKING_START_DATE, today.format(dateFormatter)) }
        cachedTrackingStart = today
    }

    override fun hasSeenCameraDisclosure(): Boolean = AttendancePrefs.hasSeenCameraDisclosure(prefs)

    override fun markCameraDisclosureSeen() {
        prefs.edit { putBoolean(AttendancePrefs.KEY_CAMERA_DISCLOSURE_SEEN, true) }
    }
}
