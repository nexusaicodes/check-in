package com.checkin.app.data.local

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * An append-only, date-ordered log of daily-target changes serialized as
 * `yyyy-MM-dd=hours;yyyy-MM-dd=hours`.
 *
 * A day is classified against the target in effect on that date, so changing the target only
 * affects that day forward — past days keep their original classification (sessions are immutable,
 * and so is their historical target).
 */
object TargetSchedule {

    const val DEFAULT_TARGET_HOURS = 2

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    data class Entry(val effectiveFrom: LocalDate, val targetHours: Int)

    fun parse(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { token ->
            val parts = token.split('=')
            if (parts.size != 2) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[0], dateFormatter) }.getOrNull()
                ?: return@mapNotNull null
            val hours = parts[1].toIntOrNull() ?: return@mapNotNull null
            Entry(date, hours)
        }.sortedBy { it.effectiveFrom }
    }

    fun serialize(entries: List<Entry>): String =
        entries.sortedBy { it.effectiveFrom }
            .joinToString(";") { "${it.effectiveFrom.format(dateFormatter)}=${it.targetHours}" }

    /** Target hours effective on [date]; defaults to [DEFAULT_TARGET_HOURS] before the first entry. */
    fun effectiveTargetHours(entries: List<Entry>, date: LocalDate): Int =
        entries.filter { !it.effectiveFrom.isAfter(date) }
            .maxByOrNull { it.effectiveFrom }
            ?.targetHours
            ?: DEFAULT_TARGET_HOURS

    fun effectiveTargetMs(entries: List<Entry>, date: LocalDate): Long =
        effectiveTargetHours(entries, date).toLong() * 60 * 60 * 1000L

    /**
     * Resolves the schedule to use given a possibly-empty parsed log. When the log is empty but
     * tracking has already started under a legacy scalar target ([trackingStart] present), a single
     * entry anchored at that start is synthesized so the pre-log target survives — otherwise the
     * empty log falls through to [DEFAULT_TARGET_HOURS].
     */
    fun withLegacyFallback(parsed: List<Entry>, trackingStart: LocalDate?, legacyHours: Int): List<Entry> {
        if (parsed.isNotEmpty()) return parsed
        if (trackingStart == null) return emptyList()
        return listOf(Entry(trackingStart, legacyHours))
    }

    /**
     * Returns [entries] with [hours] effective from [date]. Any existing entry for that exact date
     * is replaced; a change that matches the value already in effect is dropped so the log stays
     * minimal.
     */
    fun withChange(entries: List<Entry>, date: LocalDate, hours: Int): List<Entry> {
        val withoutSameDate = entries.filterNot { it.effectiveFrom == date }
        if (effectiveTargetHours(withoutSameDate, date) == hours) {
            return withoutSameDate.sortedBy { it.effectiveFrom }
        }
        return (withoutSameDate + Entry(date, hours)).sortedBy { it.effectiveFrom }
    }
}
