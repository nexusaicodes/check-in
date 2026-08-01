package com.checkin.app.data

import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Central definition of the `attendance_prefs` SharedPreferences namespace, its keys, and readers. */
object AttendancePrefs {
    const val NAME = "attendance_prefs"
    const val KEY_TRACKING_START_DATE = "tracking_start_date"
    const val KEY_CAMERA_DISCLOSURE_SEEN = "camera_disclosure_seen"

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_CAMERA_DISCLOSURE_SEEN, false)

    /** The stored tracking start, or null before the first authenticated check-in seeds it. */
    fun readTrackingStartOrNull(prefs: SharedPreferences): LocalDate? =
        prefs.getString(KEY_TRACKING_START_DATE, null)?.let { LocalDate.parse(it, dateFormatter) }

    /** The tracking start, falling back to today when tracking hasn't begun. */
    fun readTrackingStart(prefs: SharedPreferences): LocalDate = readTrackingStartOrNull(prefs) ?: LocalDate.now()
}
