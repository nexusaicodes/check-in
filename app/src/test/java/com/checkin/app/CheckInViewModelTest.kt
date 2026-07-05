package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.ui.checkin.CheckInViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        dao: FakeCheckInSessionDao,
        settings: FakeAttendanceSettings,
        service: FakeServiceController,
        time: FixedTime
    ): CheckInViewModel {
        val repo = CheckInRepository(dao, time) { settings.readSchedule() }
        return CheckInViewModel(repo, settings, time, service)
    }

    @Test
    fun `check-in seeds tracking, inserts a session, and starts the timer`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = null)
        val service = FakeServiceController()
        val viewModel = buildViewModel(dao, settings, service, FixedTime(1000L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(1, settings.seedCalls)
        assertEquals(1, dao.sessions.size)
        assertEquals(listOf(1L), service.started)
        assertTrue(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `resuming a paused session re-arms the service`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val service = FakeServiceController()
        val viewModel = buildViewModel(dao, settings, service, FixedTime(5000L, LocalDate.of(2026, 6, 15)))
        // An active session with an open pause window (a fired-but-unacknowledged presence check).
        dao.insertSession(
            com.checkin.app.data.local.CheckInSession(
                startedAt = 1000L,
                dateKey = "2026-06-15",
                pauseStartedAt = 3000L
            )
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPaused)

        viewModel.requestResume()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(1, service.rearmCount)
    }

    @Test
    fun `an existing user's seeded state already knows tracking has started`() = runTest {
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val viewModel = buildViewModel(
            FakeCheckInSessionDao(), settings, FakeServiceController(), FixedTime(0L, LocalDate.of(2026, 6, 15))
        )
        // The stateIn seed (read before any async emission) must not flash the first-run welcome.
        assertTrue(viewModel.uiState.value.hasEverTracked)
    }

    @Test
    fun `today total sums completed sessions for today`() = runTest {
        val dao = FakeCheckInSessionDao()
        val today = LocalDate.of(2026, 6, 15)
        dao.seedCompleted(today.toString(), startedAt = 0L, durationMs = 3_600_000L)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val viewModel = buildViewModel(dao, settings, FakeServiceController(), FixedTime(0L, today))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(3_600_000L, viewModel.uiState.value.todayTotalDuration)
    }

    @Test
    fun `day rollover advances today's date key without a resume`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val service = FakeServiceController()
        val time = FixedTime(1000L, LocalDate.of(2026, 6, 15))
        val viewModel = buildViewModel(dao, settings, service, time)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("2026-06-15", viewModel.uiState.value.todayDateKey)

        time.day.value = LocalDate.of(2026, 6, 16)
        advanceUntilIdle()

        assertEquals("2026-06-16", viewModel.uiState.value.todayDateKey)
    }

    @Test
    fun `check-out closes the session and stops the timer`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val service = FakeServiceController()
        val viewModel = buildViewModel(dao, settings, service, FixedTime(1000L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.requestCheckOut()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(1, service.stopCount)
        assertFalse(viewModel.uiState.value.isRunning)
        assertNotNull(dao.sessions.first().stoppedAt)
    }
}
