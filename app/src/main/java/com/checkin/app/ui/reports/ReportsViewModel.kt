package com.checkin.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.AttendanceStats
import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.di.CsvExporter
import com.checkin.app.di.ExportResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** A day's worked time, for the daily-hours chart. */
data class DayPoint(val date: LocalDate, val workedMs: Long)

/** A month's worked time, for the monthly-totals chart. */
data class MonthPoint(val month: YearMonth, val workedMs: Long)

data class ReportsUiState(
    val loading: Boolean = true,
    val trackingStartDate: LocalDate,
    val totalDays: Int = 0,
    val presentDays: Int = 0,
    val halfDays: Int = 0,
    val absentDays: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val dailyTargetHours: Int = TargetSchedule.DEFAULT_TARGET_HOURS,
    /** Trailing window ending yesterday, gap-filled so absent days read as zero rather than vanish. */
    val dailySeries: List<DayPoint> = emptyList(),
    val monthlySeries: List<MonthPoint> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val repository: CheckInRepository,
    private val settings: AttendanceSettings,
    private val timeSource: TimeSource,
    private val csvExporter: CsvExporter
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val refresh = MutableStateFlow(0)

    // One-shot export outcomes — a Channel (not a StateFlow) so a config-change re-collect can't replay
    // a past result as a duplicate snackbar.
    private val exportChannel = Channel<ExportResult>(Channel.BUFFERED)
    val exportEvents: Flow<ExportResult> = exportChannel.receiveAsFlow()

    // Overall stats up to yesterday (today is excluded), recomputed on DB writes, on refresh, and at midnight.
    private val statsFlow: Flow<ReportsUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today ->
        val start = settings.readTrackingStart()
        val yesterday = today.minusDays(1)
        val targetHours = settings.dailyTargetHoursToday()

        if (start.isAfter(yesterday)) {
            flowOf(ReportsUiState(loading = false, trackingStartDate = start, dailyTargetHours = targetHours))
        } else {
            repository.dailyAggregatesFlow(start.format(dateFormatter), yesterday.format(dateFormatter))
                .map { aggregates ->
                    // One range query feeds every figure and all three charts.
                    val summaries = repository.summariesFrom(aggregates)
                    val totalDays = (yesterday.toEpochDay() - start.toEpochDay() + 1).toInt()
                    val present = AttendanceStats.presentDays(summaries)
                    val half = summaries.values.count { it.status == AttendanceStatus.HALF_DAY_LEAVE }
                    ReportsUiState(
                        loading = false,
                        trackingStartDate = start,
                        totalDays = totalDays,
                        presentDays = present,
                        halfDays = half,
                        // Days with no sessions never reach the summary map, so absences are what's
                        // left of the tracked window once classified days are removed.
                        absentDays = (totalDays - present - half).coerceAtLeast(0),
                        currentStreak = AttendanceStats.currentStreak(summaries, start, yesterday),
                        bestStreak = AttendanceStats.bestStreak(summaries, start, yesterday),
                        dailyTargetHours = targetHours,
                        dailySeries = dailySeries(summaries, start, yesterday),
                        monthlySeries = monthlySeries(summaries, start, yesterday)
                    )
                }
        }
    }

    val uiState: StateFlow<ReportsUiState> = statsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReportsUiState(trackingStartDate = settings.readTrackingStart())
    )

    /**
     * The trailing [DAILY_WINDOW_DAYS] days ending at [end], never starting before [start]. Days
     * without sessions are emitted as zero: a gap in a line chart reads as missing data, whereas an
     * absent day is a real zero.
     */
    private fun dailySeries(
        summaries: Map<String, DailySummary>,
        start: LocalDate,
        end: LocalDate
    ): List<DayPoint> {
        val from = maxOf(start, end.minusDays((DAILY_WINDOW_DAYS - 1).toLong()))
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { day -> DayPoint(day, summaries[day.format(dateFormatter)]?.totalDurationMs ?: 0L) }
            .toList()
    }

    /** Worked time per calendar month over the trailing [MONTHLY_WINDOW_MONTHS], oldest first. */
    private fun monthlySeries(
        summaries: Map<String, DailySummary>,
        start: LocalDate,
        end: LocalDate
    ): List<MonthPoint> {
        val firstMonth = maxOf(
            YearMonth.from(start),
            YearMonth.from(end).minusMonths((MONTHLY_WINDOW_MONTHS - 1).toLong())
        )
        val lastMonth = YearMonth.from(end)
        val totals = summaries.values.groupBy { YearMonth.from(LocalDate.parse(it.dateKey, dateFormatter)) }
            .mapValues { (_, days) -> days.sumOf { it.totalDurationMs } }
        return generateSequence(firstMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(lastMonth) }
            .map { month -> MonthPoint(month, totals[month] ?: 0L) }
            .toList()
    }

    fun onResumed() {
        refresh.value++
    }

    /** Records [hours] effective from today; past days keep the target that was in effect then. */
    fun updateDailyTarget(hours: Int) {
        settings.recordTargetChange(hours)
        refresh.value++
    }

    fun exportCsv(rangeType: ExportRange) {
        viewModelScope.launch {
            val (startStr, endStr) = when (rangeType) {
                ExportRange.THIS_MONTH -> {
                    val month = YearMonth.from(timeSource.today())
                    month.atDay(1).format(dateFormatter) to month.atEndOfMonth().format(dateFormatter)
                }
                ExportRange.ALL_TIME ->
                    settings.readTrackingStart().format(dateFormatter) to timeSource.today().format(dateFormatter)
            }
            val summaries = repository.getDailySummaries(startStr, endStr)
            exportChannel.send(csvExporter.export(startStr, endStr, summaries))
        }
    }

    companion object {
        const val DAILY_WINDOW_DAYS = 30
        const val MONTHLY_WINDOW_MONTHS = 6

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                ReportsViewModel(
                    container.repository,
                    container.settings,
                    container.timeSource,
                    container.csvExporter
                )
            }
        }
    }
}

enum class ExportRange {
    THIS_MONTH,
    ALL_TIME
}
