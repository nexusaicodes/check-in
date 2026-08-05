package com.checkin.app.ui.settings

import com.checkin.app.notify.NotificationDelivery
import com.checkin.app.util.TimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The live state behind the failure modes the event log can only show the aftermath of.
 *
 * The log answers "what happened"; this answers "what is true right now". They are complementary and
 * the second is the one nothing else in the app can show: an open session whose service was killed,
 * a day boundary that was never re-armed after a package replace, alarms left standing over a closed
 * session. Each renders as an ordinary-looking app — a timer that keeps counting, a session that
 * quietly runs past midnight — so the only way to see them is to read the state directly.
 *
 * Pure, and separate from the platform reads for the same reason as [NotificationDelivery] and
 * [NotificationBlock]: the reading half is Android-only and unreachable from this project's JVM-only
 * suite, which would leave [warnings] — the half worth trusting — as the one part nothing exercises.
 */
data class DebugSnapshot(
    val nowMs: Long,
    val session: SessionState?,
    val serviceRunning: Boolean,
    val nextReminderAt: Long,
    val dayBoundaryAt: Long,
    val remindersSent: Int,
    /**
     * Where the day boundary *should* sit, derived from the session's own `date_key`. Null when there
     * is no session, or when the key does not parse. Compared against [dayBoundaryAt] because the
     * armed instant is persisted at check-in and never re-derived, so a device that changed time zone
     * mid-session keeps an alarm aimed at the old midnight.
     */
    val expectedDayBoundaryAt: Long?,
    val channels: List<ChannelState>,
) {

    /**
     * The state as flat labelled lines, for reading on screen and for the clipboard.
     *
     * Instants print as both a wall clock and an offset from now, because each answers a different
     * question: the clock says which midnight an alarm is aimed at, the offset says whether it is
     * about to fire or long overdue.
     */
    fun lines(): List<String> = buildList {
        add("now        ${clockOf(nowMs)}")
        if (session == null) {
            add("session    none open")
        } else {
            add("session    #${session.id}  ${session.dateKey}")
            val elapsed = TimeFormat.durationShort(nowMs - session.startedAt)
            add("started    ${clockOf(session.startedAt)}  ($elapsed ago)")
        }
        add("service    ${if (serviceRunning) "running" else "not running"}")
        add("reminder   ${alarmLine(nextReminderAt)}  (sent $remindersSent)")
        add("boundary   ${alarmLine(dayBoundaryAt)}")
        if (expectedDayBoundaryAt != null) {
            add("  expected ${alarmLine(expectedDayBoundaryAt)}")
        }
        channels.forEach { channel ->
            add("channel    ${channel.id}  ${channel.blocker() ?: "deliverable"}")
        }
    }

    /**
     * The states that are wrong, named. Empty when everything is consistent.
     *
     * This is the reason the card exists rather than a `logcat` filter: each entry below is a
     * documented failure mode that leaves the app looking entirely normal, so knowing which one you
     * are in is otherwise a matter of inferring backwards from a wrong number hours later.
     */
    fun warnings(): List<String> = buildList {
        if (session != null) {
            // START_STICKY is best effort. A force stop, an OEM background-management kill or a crash
            // all leave the row open with nothing on the shade, and the Check-In screen renders from
            // the row — so it shows a cheerfully running timer with no service behind it.
            if (!serviceRunning) {
                add("Open session with no service. A watchdog revive is due (app open, boot, or hourly pass).")
            }
            // The boundary close is the only thing that ends a forgotten session. Unarmed, the session
            // runs until the user notices and then writes a multi-day duration onto an uneditable row.
            if (dayBoundaryAt == 0L) {
                add("Day boundary NOT armed. This session will not be closed at midnight.")
            }
            if (nextReminderAt == 0L) {
                add("Reminder not armed.")
            }
            // The platform delivers a past-due alarm immediately, so an open session sitting well past
            // its boundary means the alarm was dropped rather than merely late.
            if (dayBoundaryAt in 1 until nowMs - PAST_DUE_GRACE_MS) {
                val overdue = TimeFormat.durationShort(nowMs - dayBoundaryAt)
                add("Day boundary is $overdue past due and the session is still open.")
            }
            if (expectedDayBoundaryAt != null && dayBoundaryAt != 0L && dayBoundaryAt != expectedDayBoundaryAt) {
                val armed = clockOf(dayBoundaryAt)
                add("Day boundary is armed for $armed but date_key implies ${clockOf(expectedDayBoundaryAt)}.")
            }
        } else {
            // Check-out cancels both alarms precisely because `ServiceController.stop()` is a caught
            // no-op when the service is already dead; either half failing shows up here.
            if (serviceRunning) {
                add("Service running with no open session. That is an orphan notification.")
            }
            if (nextReminderAt != 0L || dayBoundaryAt != 0L) {
                add("Alarms still armed with no open session. Check-out did not cancel them.")
            }
        }
        channels.mapNotNull { channel ->
            channel.blocker()?.let { "Channel ${channel.id} cannot deliver: $it." }
        }.forEach(::add)
    }

    /**
     * The snapshot and its warnings as one block of text, for pasting into a bug note.
     *
     * Both lists are bound *before* the builder, and that is not style. `StringBuilder` is a
     * `CharSequence`, so a bare `lines()` inside a `buildString` block resolves to the stdlib
     * extension that splits the buffer being built — same `List<String>` return type, so it compiles
     * clean and silently emits nothing.
     */
    fun asText(): String {
        val facts = lines()
        val warnings = warnings()
        return buildString {
            facts.forEach { appendLine(it) }
            if (warnings.isEmpty()) return@buildString
            appendLine()
            warnings.forEach { appendLine("! $it") }
        }
    }

    private fun alarmLine(atMs: Long): String = when {
        atMs == 0L -> "not armed"
        atMs >= nowMs -> "${clockOf(atMs)}  (in ${TimeFormat.durationShort(atMs - nowMs)})"
        else -> "${clockOf(atMs)}  (${TimeFormat.durationShort(nowMs - atMs)} ago)"
    }

    private companion object {
        /**
         * How far past its instant an alarm may sit before it counts as dropped rather than late.
         * Wide enough to absorb the race between the boundary passing and the broadcast arriving.
         */
        const val PAST_DUE_GRACE_MS = 2L * 60L * 1_000L

        val format: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.US).withZone(ZoneId.systemDefault())

        fun clockOf(atMs: Long): String = format.format(Instant.ofEpochMilli(atMs))
    }
}

/** The open session, reduced to what a snapshot reads. */
data class SessionState(val id: Long, val startedAt: Long, val dateKey: String)

/**
 * One channel's deliverability, kept as the three switches rather than a boolean.
 *
 * Which switch is off is the whole diagnostic: "I had notifications enabled" is usually true of the
 * permission and false of the channel, and the two are fixed in different places.
 */
data class ChannelState(
    val id: String,
    val permissionGranted: Boolean,
    val appEnabled: Boolean,
    val importance: Int?,
) {
    /** Why a post here would be dropped, or null when it would be delivered. */
    fun blocker(): String? = when {
        !permissionGranted -> "POST_NOTIFICATIONS denied"
        !appEnabled -> "notifications off app-wide"
        importance == null -> "channel missing"
        importance == NotificationDelivery.IMPORTANCE_NONE -> "channel muted"
        else -> null
    }
}
