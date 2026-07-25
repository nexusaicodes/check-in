package com.checkin.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Single source of truth for time/duration formatting used across service, view-models and screens. */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    /** Elapsed duration as HH:MM:SS (e.g. a running timer). */
    fun hms(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Live elapsed for a running clock: "0m 0s" through "59m 59s", then "1h 0m" onward. Seconds are
     * what make a just-started session visibly move; past the hour they are noise, and the minute is
     * the unit everything else in the app reports in.
     */
    fun durationLive(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            "${hours}h ${(totalSeconds % 3600) / 60}m"
        } else {
            "${totalSeconds / 60}m ${totalSeconds % 60}s"
        }
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
