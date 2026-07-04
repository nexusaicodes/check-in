package com.checkin.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Single source of truth for time/duration formatting used across service, view-models and screens. */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    /** Elapsed duration as HH:MM:SS (e.g. a running stopwatch). */
    fun hms(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /** Compact duration as "Hh Mm" (e.g. a daily total). */
    fun durationShort(millis: Long): String {
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }

    /** Wall-clock time of an epoch-millis instant in the device zone (e.g. "09:05 AM"). */
    fun clock(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(clockFormatter)
}
