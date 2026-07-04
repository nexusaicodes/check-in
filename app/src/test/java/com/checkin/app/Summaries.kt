package com.checkin.app

import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shared test helpers for building a date-keyed summary map. */
internal object Summaries {

    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun key(date: LocalDate): String = date.format(iso)

    fun of(vararg entries: Pair<LocalDate, AttendanceStatus>): Map<String, DailySummary> =
        entries.associate { (date, status) ->
            key(date) to DailySummary(key(date), 0L, 1, 0L, null, status)
        }

    fun withDurations(vararg entries: Triple<LocalDate, AttendanceStatus, Long>): Map<String, DailySummary> =
        entries.associate { (date, status, totalMs) ->
            key(date) to DailySummary(key(date), totalMs, 1, 0L, null, status)
        }
}
