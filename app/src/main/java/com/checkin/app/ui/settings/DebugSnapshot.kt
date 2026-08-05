package com.checkin.app.ui.settings

import com.checkin.app.notify.NotificationDelivery
import com.checkin.app.util.TimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What is true right now, where the event log says only what happened.
 *
 * Nothing else in the app can show these: a killed service, a boundary never re-armed after a
 * package replace, alarms standing over a closed session. Each looks like an ordinary running app,
 * so reading the state directly is the only way to see one.
 *
 * Pure, and split from the platform reads as [NotificationDelivery] and [NotificationBlock] are —
 * otherwise [warnings], the half worth trusting, is the one part the JVM suite cannot reach.
 */
data class DebugSnapshot(
    val nowMs: Long,
    val session: SessionState?,
    val serviceRunning: Boolean,
    val nextReminderAt: Long,
    val dayBoundaryAt: Long,
    val remindersSent: Int,
    /**
     * Where the boundary *should* sit, from the session's own `date_key`; null with no session or an
     * unparseable key. Worth comparing against [dayBoundaryAt] because the armed instant is stored at
     * check-in and never re-derived, so a mid-session time-zone change leaves it on the old midnight.
     */
    val expectedDayBoundaryAt: Long?,
    val channels: List<ChannelState>,
) {

    /**
     * The state as labelled lines, for the screen and the clipboard. Instants carry both a clock and
     * an offset: the clock says which midnight an alarm aims at, the offset whether it is overdue.
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
     * The states that are wrong, named. Empty when everything is consistent. Each one leaves the app
     * looking entirely normal, so the alternative is inferring backwards from a wrong number later.
     */
    fun warnings(): List<String> = buildList {
        if (session != null) {
            // START_STICKY is best effort, and the Check-In screen renders from the row — so a killed
            // service still draws a cheerfully running timer with nothing behind it.
            if (!serviceRunning) {
                add("Open session with no service. A watchdog revive is due (app open, boot, or hourly pass).")
            }
            // The only thing that ends a forgotten session. Unarmed, it runs until the user notices
            // and then writes a multi-day duration onto an uneditable row.
            if (dayBoundaryAt == 0L) {
                add("Day boundary NOT armed. This session will not be closed at midnight.")
            }
            if (nextReminderAt == 0L) {
                add("Reminder not armed.")
            }
            // A past-due alarm is delivered immediately, so sitting well past the boundary means it
            // was dropped rather than merely late.
            if (dayBoundaryAt in 1 until nowMs - PAST_DUE_GRACE_MS) {
                val overdue = TimeFormat.durationShort(nowMs - dayBoundaryAt)
                add("Day boundary is $overdue past due and the session is still open.")
            }
            if (expectedDayBoundaryAt != null && dayBoundaryAt != 0L && dayBoundaryAt != expectedDayBoundaryAt) {
                val armed = clockOf(dayBoundaryAt)
                add("Day boundary is armed for $armed but date_key implies ${clockOf(expectedDayBoundaryAt)}.")
            }
        } else {
            // Check-out cancels both alarms because `ServiceController.stop()` is a caught no-op once
            // the service is dead; either half failing shows up here.
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
     * One block of text, for pasting into a bug note. Both lists are bound *before* the builder, and
     * that is not style: `StringBuilder` is a `CharSequence`, so a bare `lines()` inside `buildString`
     * resolves to the stdlib extension splitting the buffer — same return type, silently empty.
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
        /** How late an alarm may be before it counts as dropped — wide enough for the delivery race. */
        const val PAST_DUE_GRACE_MS = 2L * 60L * 1_000L

        val format: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.US).withZone(ZoneId.systemDefault())

        fun clockOf(atMs: Long): String = format.format(Instant.ofEpochMilli(atMs))
    }
}

/** The open session, reduced to what a snapshot reads. */
data class SessionState(val id: Long, val startedAt: Long, val dateKey: String)

/**
 * One channel's deliverability as the three switches rather than a boolean, because which one is off
 * is the diagnostic: "notifications were enabled" is usually true of the permission and false of the
 * channel, and they are fixed in different places.
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
