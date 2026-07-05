package com.checkin.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.DeficitCalculator
import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.DailySummary
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class AttendanceUiState(
    val currentMonth: YearMonth,
    val trackingStartDate: LocalDate,
    val today: LocalDate,
    val summaries: Map<String, DailySummary> = emptyMap(),
    val selectedDateKey: String? = null,
    val selectedDaySessions: List<CheckInSession> = emptyList(),
    val deficit: Double = 0.0,
    val trackedDaysInMonth: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    private val repository: CheckInRepository,
    private val settings: AttendanceSettings,
    private val timeSource: TimeSource
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val currentMonth = MutableStateFlow(YearMonth.from(timeSource.today()))
    private val selectedDateKey = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0)

    // Month summaries, re-queried when the visible month or a refresh trigger changes.
    private val monthData = combine(currentMonth, refresh) { month, _ -> month }
        .flatMapLatest { month ->
            repository.dailyAggregatesFlow(
                month.atDay(1).format(dateFormatter),
                month.atEndOfMonth().format(dateFormatter)
            ).map { month to repository.summariesFrom(it) }
        }

    private val selectedSessions = selectedDateKey.flatMapLatest { key ->
        if (key == null) flowOf(emptyList<CheckInSession>()) else repository.sessionsForDateFlow(key)
    }

    // One day subscription drives the whole screen: the deficit window, the today marker, and the
    // tracked-day count all roll together on refresh and at midnight, with no divergent poll loops.
    val uiState: StateFlow<AttendanceUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today ->
            val yesterday = today.minusDays(1)
            val start = settings.readTrackingStartOrNull()
            // Rolling deficit up to yesterday (today is excluded).
            val deficitFlow = if (start == null || start.isAfter(yesterday)) {
                flowOf(0.0)
            } else {
                repository.dailyAggregatesFlow(start.format(dateFormatter), yesterday.format(dateFormatter))
                    .map { DeficitCalculator.computeDeficit(repository.summariesFrom(it), start, yesterday) }
            }
            combine(
                monthData, selectedDateKey, selectedSessions, deficitFlow
            ) { monthPair, selectedKey, sessions, deficit ->
                val (month, summaries) = monthPair
                val trackingStart = settings.readTrackingStart()
                AttendanceUiState(
                    currentMonth = month,
                    trackingStartDate = trackingStart,
                    today = today,
                    summaries = summaries,
                    selectedDateKey = selectedKey,
                    selectedDaySessions = sessions,
                    deficit = deficit,
                    trackedDaysInMonth = trackedDays(month, trackingStart, today)
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AttendanceUiState(
                currentMonth = YearMonth.from(timeSource.today()),
                trackingStartDate = settings.readTrackingStart(),
                today = timeSource.today()
            )
        )

    fun onResumed() {
        refresh.value++
    }

    fun previousMonth() {
        currentMonth.value = currentMonth.value.minusMonths(1)
        selectedDateKey.value = null
    }

    fun nextMonth() {
        currentMonth.value = currentMonth.value.plusMonths(1)
        selectedDateKey.value = null
    }

    fun selectDay(dateKey: String) {
        selectedDateKey.value = if (selectedDateKey.value == dateKey) null else dateKey
    }

    /** Count of past, tracked days in [month] up to yesterday ([today] is excluded). */
    private fun trackedDays(month: YearMonth, trackingStart: LocalDate, today: LocalDate): Int {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val effectiveStart = if (trackingStart.isAfter(monthStart)) trackingStart else monthStart
        // Exclude today: a fully-past month ends at monthEnd, otherwise cap at yesterday. On the last
        // calendar day of the current month, monthEnd == today, so this must still fall back to yesterday.
        val effectiveEnd = if (monthEnd.isBefore(today)) monthEnd else today.minusDays(1)
        return if (!effectiveStart.isAfter(effectiveEnd)) {
            (effectiveEnd.toEpochDay() - effectiveStart.toEpochDay() + 1).toInt()
        } else 0
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                AttendanceViewModel(container.repository, container.settings, container.timeSource)
            }
        }
    }
}
