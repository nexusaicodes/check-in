package com.checkin.app

import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.ExportResult
import com.checkin.app.ui.reports.ExportRange
import com.checkin.app.ui.reports.ReportsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        dao: FakeCheckInSessionDao,
        settings: FakeAttendanceSettings,
        exporter: FakeCsvExporter,
        time: FixedTime
    ): ReportsViewModel {
        val repo = CheckInRepository(dao, time) { settings.readSchedule() }
        return ReportsViewModel(repo, settings, time, exporter)
    }

    @Test
    fun `tracking that starts today yields all-zero stats`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 15))
        val viewModel = buildViewModel(dao, settings, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.totalDays)
        assertEquals(0, state.presentDays)
        assertEquals(0.0, state.deficit, 0.0)
    }

    @Test
    fun `a completed full-target day counts as present`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 8 * 3_600_000L)
        val start = LocalDate.of(2026, 6, 10)
        val settings = FakeAttendanceSettings(
            trackingStart = start,
            schedule = listOf(TargetSchedule.Entry(start, 8)),
            targetHoursToday = 8
        )
        val viewModel = buildViewModel(dao, settings, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.totalDays) // 2026-06-10 .. 2026-06-14 inclusive
        assertEquals(1, state.presentDays)
    }

    @Test
    fun `updateDailyTarget records the change`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 10))
        val viewModel = buildViewModel(dao, settings, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.updateDailyTarget(6)
        advanceUntilIdle()

        assertEquals(6, settings.recordedTarget)
    }

    @Test
    fun `export invokes the exporter and emits the result once`() = runTest {
        val dao = FakeCheckInSessionDao()
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 10))
        val viewModel = buildViewModel(dao, settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        viewModel.exportCsv(ExportRange.ALL_TIME)
        advanceUntilIdle()

        assertNotNull(exporter.lastRange)
        assertEquals(listOf(ExportResult.Success), events)
    }

    @Test
    fun `a consumed export event does not replay to a later collector`() = runTest {
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 10))
        val viewModel = buildViewModel(FakeCheckInSessionDao(), settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))

        // First collector receives the event, then goes away (e.g. the screen is recreated).
        val first = mutableListOf<ExportResult>()
        val job = launch { viewModel.exportEvents.collect { first += it } }
        viewModel.exportCsv(ExportRange.ALL_TIME)
        advanceUntilIdle()
        job.cancel()

        // A later collector (post-config-change re-subscribe) gets no replay of the past result.
        val second = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { second += it } }
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Success), first)
        assertEquals(emptyList<ExportResult>(), second)
    }
}
