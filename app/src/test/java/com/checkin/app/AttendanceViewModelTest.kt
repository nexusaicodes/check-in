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
        time: FixedTime,
    ): AttendanceViewModel {
        val repo = CheckInRepository(dao, time)
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
    fun `day rollover folds the just-finished day into the all-time average without a resume`() = runTest {
        val dao = FakeCheckInSessionDao()
        val start = LocalDate.of(2026, 6, 15)
        val fourHours = 4 * 3_600_000L
        dao.seedCompleted("2026-06-15", startedAt = 0L, durationMs = fourHours)
        val settings = FakeAttendanceSettings(trackingStart = start)
        val time = FixedTime(0L, LocalDate.of(2026, 6, 15))
        val viewModel = buildViewModel(dao, settings, time)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // On the start day itself the averaging window is empty — today is always excluded.
        assertEquals(0L, viewModel.uiState.value.allTimeAvgDailyMs)
        assertEquals(0, viewModel.uiState.value.trackedDaysInMonth)

        // Cross midnight → 06-15 becomes a completed past day and enters the window.
        time.day.value = LocalDate.of(2026, 6, 16)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 16), viewModel.uiState.value.today)
        assertEquals(1, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(fourHours, viewModel.uiState.value.allTimeAvgDailyMs)
    }

    /** Absent days stay in the denominator, so the mean is per tracked day, not per worked day. */
    @Test
    fun `the all-time average divides by tracked days including absent ones`() = runTest {
        val dao = FakeCheckInSessionDao()
        val sixHours = 6 * 3_600_000L
        dao.seedCompleted("2026-06-01", startedAt = 0L, durationMs = sixHours)
        // 06-02 and 06-03 have no sessions at all.
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val viewModel = buildViewModel(dao, settings, FixedTime(0L, LocalDate.of(2026, 6, 4)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Window is 06-01..06-03 — three tracked days holding six hours between them.
        assertEquals(sixHours / 3, viewModel.uiState.value.allTimeAvgDailyMs)
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
