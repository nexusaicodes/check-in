package com.checkin.app.di

import android.content.SharedPreferences
import androidx.core.content.edit
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.TargetSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Read/write seam over the `attendance_prefs` settings so ViewModels never touch SharedPreferences
 * directly (makes them pure-JVM testable). All reads/writes go through the centralized
 * [AttendancePrefs] and [TargetSchedule] helpers.
 */
interface AttendanceSettings {
    fun readSchedule(): List<TargetSchedule.Entry>
    fun readTrackingStart(): LocalDate
    fun readTrackingStartOrNull(): LocalDate?
    /** The per-day target ("present mark") in hours effective today. */
    fun dailyTargetHoursToday(): Int
    /** Records [hours] effective from today; past days keep the target that was in effect then. */
    fun recordTargetChange(hours: Int)
    /** Anchors tracking at today with the current target, only if tracking hasn't started yet. */
    fun seedTrackingStartIfNeeded()
}

class SharedPrefsAttendanceSettings(
    private val prefs: SharedPreferences,
    private val timeSource: TimeSource
) : AttendanceSettings {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun readSchedule(): List<TargetSchedule.Entry> = AttendancePrefs.readSchedule(prefs)

    override fun readTrackingStart(): LocalDate = AttendancePrefs.readTrackingStart(prefs)

    override fun readTrackingStartOrNull(): LocalDate? = AttendancePrefs.readTrackingStartOrNull(prefs)

    override fun dailyTargetHoursToday(): Int =
        TargetSchedule.effectiveTargetHours(readSchedule(), timeSource.today())

    override fun recordTargetChange(hours: Int) {
        val updated = TargetSchedule.withChange(readSchedule(), timeSource.today(), hours)
        prefs.edit {
            putInt(AttendancePrefs.KEY_DAILY_TARGET_HOURS, hours)
            putString(AttendancePrefs.KEY_TARGET_SCHEDULE, TargetSchedule.serialize(updated))
        }
    }

    override fun seedTrackingStartIfNeeded() {
        if (prefs.getString(AttendancePrefs.KEY_TRACKING_START_DATE, null) != null) return
        val today = timeSource.today()
        val targetHours = prefs.getInt(
            AttendancePrefs.KEY_DAILY_TARGET_HOURS,
            TargetSchedule.DEFAULT_TARGET_HOURS
        )
        val seeded = listOf(TargetSchedule.Entry(today, targetHours))
        prefs.edit {
            putString(AttendancePrefs.KEY_TRACKING_START_DATE, today.format(dateFormatter))
            putString(AttendancePrefs.KEY_TARGET_SCHEDULE, TargetSchedule.serialize(seeded))
        }
    }
}
