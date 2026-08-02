package com.checkin.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.repository.CheckInRepository
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

data class HistoryUiState(
    val currentMonth: YearMonth,
    /** The day of the first session, or null until one exists. Read from the sessions, never stored. */
    val trackingStartDate: LocalDate?,
    val today: LocalDate,
    val summaries: Map<String, DailyAggregate> = emptyMap(),
    val selectedDateKey: String? = null,
    val selectedDaySessions: List<CheckInSession> = emptyList(),
    /** Mean worked time per tracked day since tracking began, up to yesterday. */
    val allTimeAvgDailyMs: Long = 0L,
    /**
     * The longest single day on record, which is what a calendar cell's strength is measured
     * against. All-time rather than per-month so a day reads the same however it is navigated to.
     */
    val peakDayMs: Long = 0L,
    val trackedDaysInMonth: Int = 0,
    /**
     * Longest run of consecutive days shown up *within the displayed month*. A run crossing a month
     * boundary is truncated at it, which is what "this month" has to mean for a per-month figure.
     */
    val monthBestStreak: Int = 0,
    /** The same figure over the whole record — the baseline [monthBestStreak] is measured against. */
    val allTimeBestStreak: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: CheckInRepository, private val timeSource: TimeSource) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val currentMonth = MutableStateFlow(YearMonth.from(timeSource.today()))
    private val selectedDateKey = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0)

    // Month summaries, re-queried when the visible month or a refresh trigger changes.
    private val monthData = combine(currentMonth, refresh) { month, _ -> month }
        .flatMapLatest { month ->
            repository.dailyAggregatesFlow(
                month.atDay(1).format(dateFormatter),
                month.atEndOfMonth().format(dateFormatter),
            ).map { month to repository.byDateKey(it) }
        }

    private val selectedSessions = selectedDateKey.flatMapLatest { key ->
        if (key == null) flowOf(emptyList<CheckInSession>()) else repository.sessionsForDateFlow(key)
    }

    // One day subscription drives the whole screen: the averaging window, the today marker, and the
    // tracked-day count all roll together on refresh and at midnight, with no divergent poll loops.
    val uiState: StateFlow<HistoryUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today -> repository.trackingStartFlow().map { today to it } }
        .flatMapLatest { (today, start) ->
            val yesterday = today.minusDays(1)
            // One query serves every all-time figure: the mean per *tracked* day (so days without
            // sessions stay in the denominator), the peak day the calendar shades against, and the
            // best streak the month's own streak is ringed against. Today is excluded from all
            // three, as everywhere else.
            val allTimeFlow = if (start == null || start.isAfter(yesterday)) {
                flowOf(AllTime())
            } else {
                val trackedDays = yesterday.toEpochDay() - start.toEpochDay() + 1
                repository.dailyAggregatesFlow(start.format(dateFormatter), yesterday.format(dateFormatter))
                    .map { aggregates ->
                        val summaries = repository.byDateKey(aggregates)
                        AllTime(
                            avgDailyMs = ConsistencyStats.totalWorkedMs(summaries) / trackedDays,
                            peakDayMs = ConsistencyStats.peakDayMs(summaries),
                            bestStreak = ConsistencyStats.bestStreak(summaries, start, yesterday),
                        )
                    }
            }
            combine(
                monthData,
                selectedDateKey,
                selectedSessions,
                allTimeFlow,
            ) { monthPair, selectedKey, sessions, allTime ->
                val (month, summaries) = monthPair
                val window = trackedWindow(month, start, today)
                HistoryUiState(
                    currentMonth = month,
                    trackingStartDate = start,
                    today = today,
                    summaries = summaries,
                    selectedDateKey = selectedKey,
                    selectedDaySessions = sessions,
                    allTimeAvgDailyMs = allTime.avgDailyMs,
                    peakDayMs = allTime.peakDayMs,
                    trackedDaysInMonth = window?.days() ?: 0,
                    monthBestStreak = window?.let {
                        ConsistencyStats.bestStreak(summaries, it.start, it.end)
                    } ?: 0,
                    allTimeBestStreak = allTime.bestStreak,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HistoryUiState(
                currentMonth = YearMonth.from(timeSource.today()),
                // Unknown until the first query lands; the calendar reads that as "nothing recorded".
                trackingStartDate = null,
                today = timeSource.today(),
            ),
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

    /** The all-time figures, carried together because one query produces all of them. */
    private data class AllTime(val avgDailyMs: Long = 0L, val peakDayMs: Long = 0L, val bestStreak: Int = 0)

    /** An inclusive run of past, tracked days. */
    private data class TrackedWindow(val start: LocalDate, val end: LocalDate) {
        fun days(): Int = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    }

    /**
     * The past, tracked days of [month] up to yesterday, or null when it holds none. Both the
     * tracked-day count and the month's best streak are measured over exactly this window, so they
     * cannot disagree about where the month begins or whether today is in it.
     *
     * A null [trackingStart] means nothing is recorded at all, so no month holds a tracked day —
     * which is what keeps a record with no sessions from reporting days that were missed.
     */
    private fun trackedWindow(month: YearMonth, trackingStart: LocalDate?, today: LocalDate): TrackedWindow? {
        if (trackingStart == null) return null
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val effectiveStart = if (trackingStart.isAfter(monthStart)) trackingStart else monthStart
        // Exclude today: a fully-past month ends at monthEnd, otherwise cap at yesterday. On the last
        // calendar day of the current month, monthEnd == today, so this must still fall back to yesterday.
        val effectiveEnd = if (monthEnd.isBefore(today)) monthEnd else today.minusDays(1)
        return TrackedWindow(effectiveStart, effectiveEnd).takeIf { !effectiveStart.isAfter(effectiveEnd) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                HistoryViewModel(container.repository, container.timeSource)
            }
        }
    }
}
