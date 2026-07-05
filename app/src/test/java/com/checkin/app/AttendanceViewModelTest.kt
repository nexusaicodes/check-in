package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.ui.attendance.AttendanceViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        dao: FakeCheckInSessionDao,
        settings: FakeAttendanceSettings,
        time: FixedTime
    ): AttendanceViewModel {
        val repo = CheckInRepository(dao, time) { settings.readSchedule() }
        return AttendanceViewModel(repo, settings, time)
    }

    @Test
    fun `selectDay toggles the selection`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val viewModel = buildViewModel(dao, settings, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectDay("2026-06-10")
        advanceUntilIdle()
        assertEquals("2026-06-10", viewModel.uiState.value.selectedDateKey)

        viewModel.selectDay("2026-06-10")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedDateKey)
    }

    @Test
    fun `day rollover folds the just-finished day into the deficit without a resume`() = runTest {
        // No sessions at all — every past tracked day is a full day of leave.
        val dao = FakeCheckInSessionDao()
        val start = LocalDate.of(2026, 6, 15)
        val settings = FakeAttendanceSettings(trackingStart = start)
        val time = FixedTime(0L, LocalDate.of(2026, 6, 15))
        val viewModel = buildViewModel(dao, settings, time)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // On the start day itself, the deficit window is empty (today is excluded).
        assertEquals(0.0, viewModel.uiState.value.deficit, 0.0)
        assertEquals(0, viewModel.uiState.value.trackedDaysInMonth)

        // Cross midnight → 06-15 is now a past, session-less day counted as one full-day leave.
        time.day.value = LocalDate.of(2026, 6, 16)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 16), viewModel.uiState.value.today)
        assertEquals(1, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(1.0, viewModel.uiState.value.deficit, 0.0)
    }

    @Test
    fun `on the last calendar day of the month today is still excluded from tracked days`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        // Today is June 30th (June's last day): monthEnd == today, so the in-progress day must not count.
        val viewModel = buildViewModel(dao, settings, FixedTime(0L, LocalDate.of(2026, 6, 30)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(29, viewModel.uiState.value.trackedDaysInMonth)
    }

    @Test
    fun `month navigation shifts the visible month and clears selection`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val viewModel = buildViewModel(dao, settings, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectDay("2026-06-10")
        advanceUntilIdle()
        viewModel.previousMonth()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 5), viewModel.uiState.value.currentMonth)
        assertNull(viewModel.uiState.value.selectedDateKey)
    }
}
