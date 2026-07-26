package com.checkin.app

import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.ExportResult
import com.checkin.app.ui.reports.DayPoint
import com.checkin.app.ui.reports.ExportRange
import com.checkin.app.ui.reports.MonthPoint
import com.checkin.app.ui.reports.ReportsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

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
        assertEquals(0, state.absentDays)
        // Nothing to plot yet — the charts must render an empty series, not a phantom zero day.
        assertEquals(emptyList<DayPoint>(), state.dailySeries)
        assertEquals(emptyList<MonthPoint>(), state.monthlySeries)
    }

    @Test
    fun `untracked days are counted as absent rather than dropped`() = runTest {
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
        // 5 tracked days, one of them present, none half — the other four never reach the summary map.
        assertEquals(5, state.totalDays)
        assertEquals(1, state.presentDays)
        assertEquals(0, state.halfDays)
        assertEquals(4, state.absentDays)
    }

    @Test
    fun `the daily series gap-fills absent days with zero and ends yesterday`() = runTest {
        val dao = FakeCheckInSessionDao()
        val eightHours = 8 * 3_600_000L
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = eightHours)
        val start = LocalDate.of(2026, 6, 10)
        val settings = FakeAttendanceSettings(trackingStart = start)
        val viewModel = buildViewModel(dao, settings, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val series = viewModel.uiState.value.dailySeries
        // Window is clamped to the tracking start, and today (06-15) is excluded.
        assertEquals(start, series.first().date)
        assertEquals(LocalDate.of(2026, 6, 14), series.last().date)
        assertEquals(5, series.size)
        assertEquals(eightHours, series.first { it.date == LocalDate.of(2026, 6, 12) }.workedMs)
        // A day with no sessions is a real zero, not a hole in the line.
        assertEquals(0L, series.first { it.date == LocalDate.of(2026, 6, 11) }.workedMs)
    }

    @Test
    fun `the daily series is capped to its trailing window`() = runTest {
        val dao = FakeCheckInSessionDao()
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2025, 1, 1))
        val viewModel = buildViewModel(dao, settings, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val series = viewModel.uiState.value.dailySeries
        assertEquals(ReportsViewModel.DAILY_WINDOW_DAYS, series.size)
        assertEquals(LocalDate.of(2026, 6, 14), series.last().date)
    }

    @Test
    fun `the monthly series covers every month in the window including empty ones`() = runTest {
        val dao = FakeCheckInSessionDao()
        val fourHours = 4 * 3_600_000L
        dao.seedCompleted("2026-04-02", startedAt = 0L, durationMs = fourHours)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 4, 1))
        val viewModel = buildViewModel(dao, settings, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val series = viewModel.uiState.value.monthlySeries
        assertEquals(listOf(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)), series.map { it.month })
        assertEquals(fourHours, series[0].workedMs)
        assertEquals(0L, series[1].workedMs) // May had no sessions but still needs a bar
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
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 3_600_000L)
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
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 10))
        val viewModel = buildViewModel(dao, settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))

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

    // The exporter fills every gap day as FULL_DAY_LEAVE, so a range reaching past the last completed
    // day writes recorded absences for days that were never worked — or never happened at all.

    @Test
    fun `a mid-month export stops at yesterday, not at the end of the month`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-05", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 1, 1))
        val viewModel = buildViewModel(
            dao, settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15))
        )

        viewModel.exportCsv(ExportRange.THIS_MONTH)
        advanceUntilIdle()

        assertEquals("2026-06-01" to "2026-06-14", exporter.lastRange)
    }

    /**
     * The month is clamped at both ends, not just the later one: days before the user had ever used
     * the app are not absences, and gap-filling them would contradict the same user's all-time export.
     */
    @Test
    fun `a mid-month export starts at the tracking start, not the first of the month`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-21", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 20))
        val viewModel = buildViewModel(
            dao, settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 25))
        )

        viewModel.exportCsv(ExportRange.THIS_MONTH)
        advanceUntilIdle()

        assertEquals("2026-06-20" to "2026-06-24", exporter.lastRange)
    }

    @Test
    fun `an all-time export excludes today, which is still being worked`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-05-01", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 4, 20))
        val viewModel = buildViewModel(
            dao, settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15))
        )

        viewModel.exportCsv(ExportRange.ALL_TIME)
        advanceUntilIdle()

        assertEquals("2026-04-20" to "2026-06-14", exporter.lastRange)
    }

    /**
     * A range can be well-formed and hold nothing — every check-in abandoned, or the app installed
     * and left idle. The file would then be pure gap-fill: a document asserting a week of absences
     * the app never recorded.
     */
    @Test
    fun `a valid range holding no completed session reports nothing`() = runTest {
        val exporter = FakeCsvExporter(ExportResult.Success)
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 8))
        val viewModel = buildViewModel(
            FakeCheckInSessionDao(), settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15))
        )

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        viewModel.exportCsv(ExportRange.ALL_TIME)
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Nothing), events)
        assertNull(exporter.lastRange)
    }

    @Test
    fun `an export with no completed day reports nothing rather than writing absences`() = runTest {
        val exporter = FakeCsvExporter(ExportResult.Success)
        // Tracking began today, so there is no completed day in either range.
        val settings = FakeAttendanceSettings(trackingStart = LocalDate.of(2026, 6, 1))
        val viewModel = buildViewModel(
            FakeCheckInSessionDao(), settings, exporter, FixedTime(0L, LocalDate.of(2026, 6, 1))
        )

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        viewModel.exportCsv(ExportRange.THIS_MONTH)
        viewModel.exportCsv(ExportRange.ALL_TIME)
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Nothing, ExportResult.Nothing), events)
        assertNull(exporter.lastRange)
    }
}
