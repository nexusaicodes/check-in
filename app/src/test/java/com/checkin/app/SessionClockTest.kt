package com.checkin.app

import com.checkin.app.service.SessionClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The elapsed time on the ongoing notification is the number a user reads to decide whether they are
 * still checked in, and it lived inside an Android `Service` where nothing could reach it. These are
 * the cases that were only ever verifiable by installing the app and waiting.
 */
class SessionClockTest {

    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    // --- The chronometer origin ---

    /** Nothing paused: the platform counts up from the check-in instant itself. */
    @Test
    fun `an unpaused session counts from its check-in`() {
        assertEquals(start, SessionClock.chronometerBase(start, pausedMs = 0, pauseStartedAt = null))
    }

    /**
     * Settled paused time is expressed by pushing the origin later, not by re-posting on a timer —
     * a chronometer can only run forward, and this is what makes it read net worked time for free.
     */
    @Test
    fun `settled paused time moves the origin forward by exactly that much`() {
        val base = SessionClock.chronometerBase(start, pausedMs = 30 * minute, pauseStartedAt = null)

        assertEquals(start + 30 * minute, base)
        // Read back at two hours in: 2h wall-clock less the 30m unverified gap.
        assertEquals(90 * minute, start + 120 * minute - base!!)
    }

    /** While a pause is open no fixed origin stays correct, so there is no chronometer at all. */
    @Test
    fun `an open pause has no origin`() {
        assertNull(SessionClock.chronometerBase(start, pausedMs = 0, pauseStartedAt = start + minute))
        assertNull(SessionClock.chronometerBase(start, pausedMs = 30 * minute, pauseStartedAt = start + minute))
    }

    /**
     * A service that has posted before adopting a row. Counting up from the epoch for the instant
     * before the reconcile corrects it would flash a decades-long timer on the shade.
     */
    @Test
    fun `a session with no start yet has no origin`() {
        assertNull(SessionClock.chronometerBase(0L, pausedMs = 0, pauseStartedAt = null))
    }

    // --- Elapsed time ---

    @Test
    fun `elapsed is wall-clock since check-in when nothing was paused`() {
        assertEquals(90 * minute, SessionClock.elapsedMs(start + 90 * minute, start, 0, null))
    }

    @Test
    fun `settled paused time is subtracted`() {
        assertEquals(60 * minute, SessionClock.elapsedMs(start + 90 * minute, start, 30 * minute, null))
    }

    /** The open window counts too: the clock has to freeze the moment the check goes out. */
    @Test
    fun `an open pause freezes the clock at the instant it opened`() {
        val pauseAt = start + 60 * minute

        val atPause = SessionClock.elapsedMs(pauseAt, start, 0, pauseAt)
        val tenMinutesLater = SessionClock.elapsedMs(pauseAt + 10 * minute, start, 0, pauseAt)

        assertEquals(60 * minute, atPause)
        assertEquals("a frozen clock must not advance", atPause, tenMinutesLater)
    }

    @Test
    fun `settled and open paused time are both subtracted`() {
        val pauseAt = start + 90 * minute

        val elapsed = SessionClock.elapsedMs(pauseAt + 20 * minute, start, 30 * minute, pauseAt)

        assertEquals(60 * minute, elapsed)
    }

    /**
     * A backwards clock is a changed system time or a corrupt row. Showing nothing beats showing a
     * negative duration, and `TimeFormat` is not asked to render one.
     */
    @Test
    fun `time running backwards floors at zero`() {
        assertEquals(0L, SessionClock.elapsedMs(start - minute, start, 0, null))
        assertEquals(0L, SessionClock.elapsedMs(start + minute, start, 10 * minute, null))
    }

    /**
     * A pause stamped in the future — the system clock moved while a check was outstanding. It
     * contributes nothing rather than *adding* time back: the two floors are separate on purpose, so
     * one nonsensical field cannot inflate a session's hours.
     */
    @Test
    fun `a pause stamped in the future adds nothing`() {
        val elapsed = SessionClock.elapsedMs(start + minute, start, 0, start + 5 * minute)

        assertEquals(minute, elapsed)
    }

    @Test
    fun `a session with no start yet reads zero rather than epoch`() {
        assertEquals(0L, SessionClock.elapsedMs(start, 0L, 0, null))
    }
}
