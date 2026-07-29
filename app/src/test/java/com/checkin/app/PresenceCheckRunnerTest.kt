package com.checkin.app

import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.service.PresenceCheckPolicy
import com.checkin.app.service.PresenceCheckRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The presence check used to be polled by the foreground service's one-second loop, which made it
 * hostage to that service twice over: the loop only advanced while the CPU was awake, and it stopped
 * entirely when the process was killed. It now runs from an alarm, in whatever process the broadcast
 * lands in, and reads the database rather than the service's memory.
 *
 * These tests exist because that path has no UI and no service behind it — every failure in it is
 * silent, and the last one cost a user sixteen unrecorded hours.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenceCheckRunnerTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val now = today.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val time = FixedTime(now, today)
    private val dao = FakeCheckInSessionDao()
    private val settings = FakeAttendanceSettings(
        trackingStart = today,
        schedule = listOf(TargetSchedule.Entry(today, 8)),
    )
    private val repository = CheckInRepository(dao, time) { settings.readSchedule() }
    private val notifier = FakeNotifier()
    private val schedule = FakePresenceSchedule()
    private val log = FakeEngagementLog()

    private fun runner() = PresenceCheckRunner(
        repository = repository,
        settings = settings,
        notifier = notifier,
        strings = StringResolver { "copy-$it" },
        schedule = schedule,
        log = log,
        timeSource = time,
    )

    private suspend fun openSession() = repository.checkIn()

    private fun targetMs() = TargetSchedule.effectiveTargetMs(settings.readSchedule(), today)

    // --- Arming ---

    @Test
    fun `arming schedules inside the 50 to 100 percent window`() = runTest {
        runner().arm(now)

        val at = schedule.lastScheduled
        assertNotNull(at)
        assertTrue("scheduled before the half mark", at!! >= now + targetMs() / 2)
        assertTrue("scheduled past the present mark", at <= now + targetMs())
    }

    @Test
    fun `arming with the check switched off cancels instead`() = runTest {
        settings.presenceCheckEnabled = false

        runner().arm(now)

        assertTrue(schedule.scheduled.isEmpty())
        assertEquals(1, schedule.cancelCount)
    }

    /** A fresh anchor means a fresh question; the retry count from the answered one must not carry. */
    @Test
    fun `arming clears any outstanding retry count`() = runTest {
        schedule.attempts = 3

        runner().arm(now)

        assertEquals(0, schedule.attempts)
    }

    // --- Firing ---

    @Test
    fun `a fired check posts, opens a pause and arms the next one`() = runTest {
        openSession()

        val outcome = runner().onAlarmFired()

        assertTrue(outcome is PresenceCheckRunner.Outcome.Fired)
        assertEquals(1, notifier.shown.size)
        assertEquals(NotificationIds.PRESENCE_CHECK, notifier.shown.single().id)
        assertNotNull(repository.getActiveSession()?.pauseStartedAt)
        assertEquals(PresenceCheckPolicy.retryAt(now, 1), schedule.lastScheduled)
    }

    /**
     * The pause is stamped at the instant the question was asked, never at the instant it was
     * scheduled for. An inexact alarm is allowed to land late, and back-dating would delete hours
     * the user worked while nothing had been asked of them — on a row the app cannot edit.
     */
    @Test
    fun `the pause is stamped when the question was asked`() = runTest {
        openSession()

        runner().onAlarmFired()

        assertEquals(now, repository.getActiveSession()?.pauseStartedAt)
    }

    @Test
    fun `continue mode fires without stopping the clock`() = runTest {
        settings.presenceCheckPauses = false
        openSession()

        val outcome = runner().onAlarmFired()

        assertEquals(false, (outcome as PresenceCheckRunner.Outcome.Fired).pauseOpened)
        assertNull(repository.getActiveSession()?.pauseStartedAt)
        assertEquals(1, notifier.shown.size)
    }

    /** The retries are reminders about one outstanding question, not new ones. */
    @Test
    fun `a retry does not reopen the pause already accruing`() = runTest {
        openSession()
        val runner = runner()
        runner.onAlarmFired()
        val firstPause = repository.getActiveSession()?.pauseStartedAt

        val second = runner.onAlarmFired()

        assertEquals(false, (second as PresenceCheckRunner.Outcome.Fired).pauseOpened)
        assertEquals(firstPause, repository.getActiveSession()?.pauseStartedAt)
        assertEquals(2, second.attempt)
    }

    @Test
    fun `the first ask alerts and the retries are silent`() = runTest {
        openSession()
        val runner = runner()

        runner.onAlarmFired()
        runner.onAlarmFired()

        assertFalse("the first ask must alert", notifier.shown[0].silent)
        assertTrue("a retry must not buzz", notifier.shown[1].silent)
    }

    @Test
    fun `each retry is armed further out than the last`() = runTest {
        openSession()
        val runner = runner()

        runner.onAlarmFired()
        val firstGap = schedule.lastScheduled!! - now
        runner.onAlarmFired()
        val secondGap = schedule.lastScheduled!! - now

        assertTrue("retries must back off, got $firstGap then $secondGap", secondGap > firstGap)
    }

    // --- The paths that used to fail silently ---

    /**
     * A refused post is a revoked notification permission, not a reason to write the session off.
     * The behaviour this replaced marked the check spent and never asked again, so restoring the
     * permission changed nothing until the next check-in.
     */
    @Test
    fun `a refused post keeps a retry armed and logs nothing as shown`() = runTest {
        openSession()
        notifier.refuse = true

        val outcome = runner().onAlarmFired()

        assertEquals(PresenceCheckRunner.Outcome.Refused, outcome)
        assertEquals(0, log.shownCountSince(0L))
        assertNotNull("a retry must stay armed", schedule.lastScheduled)
        assertNull("nothing was asked, so nothing may be charged for", repository.getActiveSession()?.pauseStartedAt)
    }

    /** Silence is not an answer: the escalation must not run out on checks nobody ever saw. */
    @Test
    fun `a refused post does not consume an attempt`() = runTest {
        openSession()
        notifier.refuse = true

        runner().onAlarmFired()

        assertEquals(0, schedule.attempts)
    }

    /**
     * Refusals need a back-off of their own. Without one, a session running with notifications off
     * re-asks every thirty minutes until check-out — waking the device all night on behalf of a
     * message that cannot appear.
     */
    @Test
    fun `repeated refusals back off`() = runTest {
        openSession()
        notifier.refuse = true
        val runner = runner()

        runner.onAlarmFired()
        val firstGap = schedule.lastScheduled!! - now
        runner.onAlarmFired()
        val secondGap = schedule.lastScheduled!! - now

        assertEquals(2, schedule.refusals)
        assertEquals(0, schedule.attempts)
        assertTrue("refusals must back off, got $firstGap then $secondGap", secondGap > firstGap)
    }

    /** Restoring the permission mid-session must not leave the session on the refusal back-off. */
    @Test
    fun `a successful post clears the refusal back-off`() = runTest {
        openSession()
        val runner = runner()

        notifier.refuse = true
        runner.onAlarmFired()
        notifier.refuse = false
        runner.onAlarmFired()

        assertEquals(0, schedule.refusals)
        assertEquals(1, schedule.attempts)
        assertEquals(1, notifier.shown.size)
        assertFalse("the first check the user can actually see must alert", notifier.shown.single().silent)
    }

    @Test
    fun `an alarm that outlives its session cancels itself`() = runTest {
        val outcome = runner().onAlarmFired()

        assertEquals(PresenceCheckRunner.Outcome.NoSession, outcome)
        assertEquals(1, schedule.cancelCount)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a check switched off between arming and firing does not fire`() = runTest {
        openSession()
        settings.presenceCheckEnabled = false

        val outcome = runner().onAlarmFired()

        assertEquals(PresenceCheckRunner.Outcome.Disabled, outcome)
        assertTrue(notifier.shown.isEmpty())
        assertNull(repository.getActiveSession()?.pauseStartedAt)
    }

    // --- Logging ---

    /**
     * Presence rows must stay invisible to the nudge daily cap and to conversion attribution, or a
     * check would silence that day's real nudge and take credit for a check-in it never caused.
     */
    @Test
    fun `a fired check is logged against the presence source only`() = runTest {
        openSession()

        runner().onAlarmFired()

        val presence = log.events.value.filter { it.source == EngagementSource.PRESENCE.name }
        assertEquals(1, presence.size)
        assertEquals(EngagementEventType.SHOWN.name, presence.single().event)
        assertEquals(0, log.shownCountSince(0L))
    }

    @Test
    fun `arming and firing both leave a service breadcrumb`() = runTest {
        openSession()
        val runner = runner()

        runner.arm(now)
        runner.onAlarmFired()

        val service = log.events.value.filter { it.source == EngagementSource.SERVICE.name }
        assertTrue("expected alarm breadcrumbs, got $service", service.size >= 2)
    }
}
