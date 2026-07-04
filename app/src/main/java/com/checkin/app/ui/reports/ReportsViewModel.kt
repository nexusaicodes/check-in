package com.checkin.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.AttendanceStats
import com.checkin.app.data.DeficitCalculator
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.di.CsvExporter
import com.checkin.app.di.ExportResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ReportsUiState(
    val loading: Boolean = true,
    val trackingStartDate: LocalDate,
    val totalDays: Int = 0,
    val presentDays: Int = 0,
    val totalHoursMs: Long = 0L,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val deficit: Double = 0.0,
    val dailyTargetHours: Int = TargetSchedule.DEFAULT_TARGET_HOURS,
    val exportEvent: ExportResult? = null
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
    private val exportEvent = MutableStateFlow<ExportResult?>(null)

    // Overall stats up to yesterday (today is excluded), recomputed on DB writes and on refresh.
    private val statsFlow: Flow<ReportsUiState> = refresh.flatMapLatest {
        val start = settings.readTrackingStart()
        val yesterday = timeSource.today().minusDays(1)
        val targetHours = settings.dailyTargetHoursToday()

        if (start.isAfter(yesterday)) {
            flowOf(ReportsUiState(loading = false, trackingStartDate = start, dailyTargetHours = targetHours))
        } else {
            repository.dailyAggregatesFlow(start.format(dateFormatter), yesterday.format(dateFormatter))
                .map { aggregates ->
                    val summaries = repository.summariesFrom(aggregates)
                    ReportsUiState(
                        loading = false,
                        trackingStartDate = start,
                        totalDays = (yesterday.toEpochDay() - start.toEpochDay() + 1).toInt(),
                        presentDays = AttendanceStats.presentDays(summaries),
                        totalHoursMs = AttendanceStats.totalWorkedMs(summaries),
                        deficit = DeficitCalculator.computeDeficit(summaries, start, yesterday),
                        currentStreak = AttendanceStats.currentStreak(summaries, start, yesterday),
                        bestStreak = AttendanceStats.bestStreak(summaries, start, yesterday),
                        dailyTargetHours = targetHours
                    )
                }
        }
    }

    val uiState: StateFlow<ReportsUiState> = combine(statsFlow, exportEvent) { base, event ->
        base.copy(exportEvent = event)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReportsUiState(trackingStartDate = settings.readTrackingStart())
    )

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
            exportEvent.value = csvExporter.export(startStr, endStr, summaries)
        }
    }

    fun consumeExportEvent() {
        exportEvent.value = null
    }

    companion object {
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
