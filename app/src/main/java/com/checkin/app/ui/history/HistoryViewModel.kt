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
    /**
     * The last day every figure here counts through — [today] once it has been checked out of,
     * otherwise yesterday. See `ConsistencyStats.countedThrough`.
     */
    val countedThrough: LocalDate,
    val summaries: Map<String, DailyAggregate> = emptyMap(),
    val selectedDateKey: String? = null,
    val selectedDaySessions: List<CheckInSession> = emptyList(),
    /** Mean worked time per tracked day since tracking began, up to [countedThrough]. */
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
            // One query serves every all-time figure: the mean per *tracked* day (so days without
            // sessions stay in the denominator), the peak day the calendar shades against, and the
            // best streak the month's own streak is ringed against. It runs through today, and how
            // far of that range counts is decided per emission — today joins the moment it holds a
            // completed session, and the whole screen is anchored to that one day.
            val allTimeFlow = if (start == null || start.isAfter(today)) {
                flowOf(AllTime(countedThrough = today.minusDays(1)))
            } else {
                repository.dailyAggregatesFlow(start.format(dateFormatter), today.format(dateFormatter))
                    .map { aggregates ->
                        val summaries = repository.byDateKey(aggregates)
                        val countedThrough = ConsistencyStats.countedThrough(summaries, today)
                        // The record's first day, still unfinished: there is no counted day to
                        // divide by yet, let alone average.
                        if (start.isAfter(countedThrough)) {
                            AllTime(countedThrough = countedThrough)
                        } else {
                            val trackedDays = countedThrough.toEpochDay() - start.toEpochDay() + 1
                            AllTime(
                                avgDailyMs = ConsistencyStats.totalWorkedMs(summaries) / trackedDays,
                                peakDayMs = ConsistencyStats.peakDayMs(summaries),
                                bestStreak = ConsistencyStats.bestStreak(summaries, start, countedThrough),
                                countedThrough = countedThrough,
                            )
                        }
                    }
            }
            combine(
                monthData,
                selectedDateKey,
                selectedSessions,
                allTimeFlow,
            ) { monthPair, selectedKey, sessions, allTime ->
                val (month, summaries) = monthPair
                val window = trackedWindow(month, start, allTime.countedThrough)
                HistoryUiState(
                    currentMonth = month,
                    trackingStartDate = start,
                    today = today,
                    countedThrough = allTime.countedThrough,
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
                countedThrough = timeSource.today().minusDays(1),
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

    /**
     * The all-time figures, carried together because one query produces all of them — including
     * [countedThrough], which that query is also what decides.
     */
    private data class AllTime(
        val avgDailyMs: Long = 0L,
        val peakDayMs: Long = 0L,
        val bestStreak: Int = 0,
        val countedThrough: LocalDate,
    )

    /** An inclusive run of tracked, counted days. */
    private data class TrackedWindow(val start: LocalDate, val end: LocalDate) {
        fun days(): Int = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    }

    /**
     * The tracked, counted days of [month], or null when it holds none. Both the tracked-day count
     * and the month's best streak are measured over exactly this window, so they cannot disagree
     * about where the month begins or how much of today is in it.
     *
     * A null [trackingStart] means nothing is recorded at all, so no month holds a tracked day —
     * which is what keeps a record with no sessions from reporting days that were missed.
     */
    private fun trackedWindow(month: YearMonth, trackingStart: LocalDate?, countedThrough: LocalDate): TrackedWindow? {
        if (trackingStart == null) return null
        val effectiveStart = maxOf(trackingStart, month.atDay(1))
        // A fully-past month ends at its own last day; the current one ends wherever counting
        // currently reaches, which is today only once today has been checked out of.
        val effectiveEnd = minOf(month.atEndOfMonth(), countedThrough)
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
