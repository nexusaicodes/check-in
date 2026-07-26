package com.checkin.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
private const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE
private const val MILLIS_PER_HOUR = MILLIS_PER_MINUTE * MINUTES_PER_HOUR

/** Single source of truth for time/duration formatting used across service, view-models and screens. */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    /** Elapsed duration as HH:MM:SS (e.g. a running timer). */
    fun hms(millis: Long): String {
        val seconds = (millis / MILLIS_PER_SECOND) % SECONDS_PER_MINUTE
        val minutes = (millis / MILLIS_PER_MINUTE) % MINUTES_PER_HOUR
        val hours = millis / MILLIS_PER_HOUR
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Live elapsed for a running clock: "0m 0s" through "59m 59s", then "1h 0m" onward. Seconds are
     * what make a just-started session visibly move; past the hour they are noise, and the minute is
     * the unit everything else in the app reports in.
     */
    fun durationLive(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        return if (hours > 0) {
            "${hours}h ${(totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE}m"
        } else {
            "${totalSeconds / SECONDS_PER_MINUTE}m ${totalSeconds % SECONDS_PER_MINUTE}s"
        }
    }

    /** Compact duration as "Hh Mm" (e.g. a daily total). */
    fun durationShort(millis: Long): String {
        val totalMinutes = millis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return "${hours}h ${minutes}m"
    }

    /** Wall-clock time of an epoch-millis instant in the device zone (e.g. "09:05 AM"). */
    fun clock(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(clockFormatter)
}
