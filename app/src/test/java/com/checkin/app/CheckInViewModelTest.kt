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
