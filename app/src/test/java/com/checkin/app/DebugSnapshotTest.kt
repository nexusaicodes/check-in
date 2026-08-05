package com.checkin.app

import com.checkin.app.ui.settings.ChannelState
import com.checkin.app.ui.settings.DebugSnapshot
import com.checkin.app.ui.settings.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the debug diagnostics card calls wrong.
 *
 * Each warning below names a state the app renders as entirely normal — an open session whose
 * service was killed still draws a running timer, because the Check-In screen draws from the DB row,
 * and a session whose day boundary was never re-armed looks identical until it writes a multi-day
 * duration. The whole value of the card is that these are stated rather than inferred, so the
 * conditions are pinned here.
 */
class DebugSnapshotTest {

    private val now = 1_700_000_000_000L
    private val minute = 60L * 1_000L
    private val session = SessionState(id = 7L, startedAt = now - minute, dateKey = "2026-06-15")

    private fun snapshot(
        session: SessionState? = this.session,
        serviceRunning: Boolean = true,
        nextReminderAt: Long = now + minute,
        dayBoundaryAt: Long = now + minute,
        expectedDayBoundaryAt: Long? = now + minute,
        channels: List<ChannelState> = emptyList(),
    ) = DebugSnapshot(
        nowMs = now,
        session = session,
        serviceRunning = serviceRunning,
        nextReminderAt = nextReminderAt,
        dayBoundaryAt = dayBoundaryAt,
        remindersSent = 0,
        expectedDayBoundaryAt = expectedDayBoundaryAt,
        channels = channels,
    )

    @Test
    fun `a healthy session warns about nothing`() {
        assertEquals(emptyList<String>(), snapshot().warnings())
    }

    /**
     * `START_STICKY` is best effort: a force stop, an OEM background-management kill or a crash all
     * leave the row open with the service gone. This is the state `SessionWatchdog` exists to repair.
     */
    @Test
    fun `an open session with no service is reported`() {
        val warnings = snapshot(serviceRunning = false).warnings()

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("no service"))
    }

    /**
     * The day boundary is the only thing that ends a forgotten session. A force stop and a package
     * replace both cancel alarms while leaving the session open, which is what `ensureArmed` repairs.
     */
    @Test
    fun `an unarmed day boundary is reported`() {
        val warnings = snapshot(dayBoundaryAt = 0L, expectedDayBoundaryAt = now + minute).warnings()

        assertTrue(warnings.any { it.contains("NOT armed") })
    }

    /**
     * The platform delivers a past-due alarm immediately, so a session still open well after its
     * boundary means the alarm was dropped rather than merely running late.
     */
    @Test
    fun `a day boundary long past due with the session still open is reported`() {
        val warnings = snapshot(dayBoundaryAt = now - 10 * minute).warnings()

        assertTrue(warnings.any { it.contains("past due") })
    }

    /** Just-passed is the ordinary race between the boundary and the broadcast, not a fault. */
    @Test
    fun `a boundary that has only just passed is not reported`() {
        val justPassed = now - 1000L
        val warnings = snapshot(dayBoundaryAt = justPassed, expectedDayBoundaryAt = justPassed).warnings()

        assertEquals(emptyList<String>(), warnings)
    }

    /**
     * The armed instant is persisted at check-in and never re-derived, so a device that changed time
     * zone mid-session keeps an alarm aimed at the old midnight.
     */
    @Test
    fun `a boundary disagreeing with the session's date key is reported`() {
        val warnings = snapshot(
            dayBoundaryAt = now + minute,
            expectedDayBoundaryAt = now + 60 * minute,
        ).warnings()

        assertTrue(warnings.any { it.contains("date_key implies") })
    }

    /** Check-out cancels both alarms; either half failing strands them over a closed session. */
    @Test
    fun `alarms left armed with no session are reported`() {
        val warnings = snapshot(
            session = null,
            serviceRunning = false,
            expectedDayBoundaryAt = null,
        ).warnings()

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("no open session"))
    }

    /** A service with nothing behind it is an orphan notification the reconcile should have torn down. */
    @Test
    fun `a service running with no session is reported`() {
        val warnings = snapshot(
            session = null,
            serviceRunning = true,
            nextReminderAt = 0L,
            dayBoundaryAt = 0L,
            expectedDayBoundaryAt = null,
        ).warnings()

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("orphan notification"))
    }

    /** Nothing open and nothing armed is the resting state, not a fault. */
    @Test
    fun `a closed idle app warns about nothing`() {
        val warnings = snapshot(
            session = null,
            serviceRunning = false,
            nextReminderAt = 0L,
            dayBoundaryAt = 0L,
            expectedDayBoundaryAt = null,
        ).warnings()

        assertEquals(emptyList<String>(), warnings)
    }

    /**
     * Which switch is off is the whole diagnostic — "I had notifications enabled" is usually true of
     * the permission and false of the channel, and the two are fixed in different places.
     */
    @Test
    fun `each blocked channel names the switch that blocked it`() {
        val importanceDefault = 3
        val channels = listOf(
            ChannelState("a", permissionGranted = false, appEnabled = true, importance = importanceDefault),
            ChannelState("b", permissionGranted = true, appEnabled = false, importance = importanceDefault),
            ChannelState("c", permissionGranted = true, appEnabled = true, importance = 0),
            ChannelState("d", permissionGranted = true, appEnabled = true, importance = null),
            ChannelState("e", permissionGranted = true, appEnabled = true, importance = importanceDefault),
        )

        assertEquals("POST_NOTIFICATIONS denied", channels[0].blocker())
        assertEquals("notifications off app-wide", channels[1].blocker())
        assertEquals("channel muted", channels[2].blocker())
        // A post to a channel that was never created is discarded, so a missing one is undeliverable.
        assertEquals("channel missing", channels[3].blocker())
        assertEquals(null, channels[4].blocker())

        // Four of the five reach the warnings; the deliverable one does not.
        assertEquals(4, snapshot(channels = channels).warnings().size)
    }

    /** The clipboard payload carries the warnings, not just the facts they were derived from. */
    @Test
    fun `the text report includes the warnings`() {
        val text = snapshot(serviceRunning = false).asText()

        assertTrue(text.contains("session    #7"))
        assertTrue(text.contains("! Open session with no service"))
    }
}
