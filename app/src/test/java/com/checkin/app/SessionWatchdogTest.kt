package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.ServiceEventType
import com.checkin.app.service.SessionWatchdog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * An open session with no service timing it was a reachable and terminal state: `START_STICKY` is
 * best effort, nothing restarted the service after check-in, and the Check-In screen rendered a
 * running timer straight from the row — so the app looked healthy while the notification, the
 * presence check and any chance of noticing were all gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionWatchdogTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val now = today.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val time = FixedTime(now, today)
    private val dao = FakeCheckInSessionDao()
    private val settings = FakeAttendanceSettings(trackingStart = today)
    private val repository = CheckInRepository(dao, time) { settings.readSchedule() }
    private val controller = FakeServiceController()
    private val log = FakeEngagementLog()

    private fun watchdog(serviceRunning: Boolean) =
        SessionWatchdog(repository, controller, log, time) { serviceRunning }

    private fun serviceEvents() = log.events.value.filter { it.source == EngagementSource.SERVICE.name }

    @Test
    fun `an open session with no service is revived`() = runTest {
        val session = repository.checkIn()

        val acted = watchdog(serviceRunning = false).reviveIfNeeded("test")

        assertTrue(acted)
        assertEquals(listOf(session.id), controller.started)
        assertEquals(ServiceEventType.REVIVED.name, serviceEvents().single().event)
    }

    @Test
    fun `a live service is left alone`() = runTest {
        repository.checkIn()

        val acted = watchdog(serviceRunning = true).reviveIfNeeded("test")

        assertFalse(acted)
        assertTrue(controller.started.isEmpty())
        assertTrue(serviceEvents().isEmpty())
    }

    /** No open row means nothing to time — starting a service here would post an orphan timer. */
    @Test
    fun `no open session means no revive`() = runTest {
        val acted = watchdog(serviceRunning = false).reviveIfNeeded("test")

        assertFalse(acted)
        assertTrue(controller.started.isEmpty())
    }

    @Test
    fun `a session already checked out is not revived`() = runTest {
        val session = repository.checkIn()
        repository.checkOut(session.id)

        val acted = watchdog(serviceRunning = false).reviveIfNeeded("test")

        assertFalse(acted)
        assertTrue(controller.started.isEmpty())
    }

    /**
     * Starting a foreground service from the background is restricted, so a refusal is an ordinary
     * outcome for the hourly caller. It has to be recorded rather than thrown — a silent refusal is
     * indistinguishable from never having tried, which is the state this whole mechanism exists to
     * make visible.
     */
    @Test
    fun `a refused start is logged as degraded rather than thrown`() = runTest {
        repository.checkIn()
        controller.startAllowed = false

        val acted = watchdog(serviceRunning = false).reviveIfNeeded("hourly pass")

        assertTrue("the attempt still counts as having acted", acted)
        assertEquals(ServiceEventType.DEGRADED.name, serviceEvents().single().event)
        assertTrue(serviceEvents().single().key.contains("hourly pass"))
    }

    @Test
    fun `the revive records where it came from`() = runTest {
        repository.checkIn()

        watchdog(serviceRunning = false).reviveIfNeeded("boot")

        assertEquals("boot", serviceEvents().single().key)
    }
}
