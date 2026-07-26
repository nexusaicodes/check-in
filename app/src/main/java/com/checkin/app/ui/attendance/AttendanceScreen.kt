package com.checkin.app.ui.attendance

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.attendance.components.CalendarGrid
import com.checkin.app.ui.attendance.components.MonthSummaryCard
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.util.TimeFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AttendanceScreen(
    innerPadding: PaddingValues,
    viewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Roll the date window / re-read tracking start when the screen resumes.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val widthSizeClass = calculateWindowSizeClass(LocalContext.current as Activity).widthSizeClass
    val topPad = innerPadding.calculateTopPadding() + 16.dp
    val bottomPad = innerPadding.calculateBottomPadding() + 8.dp
    val hasDetail = uiState.selectedDateKey != null &&
        uiState.selectedDaySessions.any { it.stoppedAt != null }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Cells are sized from what the grid can actually have: the real viewport (not the physical
        // screen, which counts the app bar and bottom nav as usable) less the chrome, the month
        // selector, the weekday header and the summary card that has to stay on screen beside it.
        val available = maxHeight - topPad - bottomPad
        val textGrowth = TEXT_CONTENT_HEIGHT * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
        val gridBudget = available - MONTH_SELECTOR_HEIGHT - WEEKDAY_HEADER_HEIGHT -
            SUMMARY_CARD_HEIGHT - SECTION_SPACING * 2 - textGrowth
        // The floor wins over fitting: 48dp is the minimum tap target, so a 6-row month on a small
        // screen scrolls a little rather than shrinking its days below it.
        val cellHeight = (gridBudget / weeksIn(uiState.currentMonth))
            .coerceIn(MIN_CELL_HEIGHT, MAX_CELL_HEIGHT)

        AttendanceContent(
            uiState = uiState,
            viewModel = viewModel,
            widthSizeClass = widthSizeClass,
            cellHeight = cellHeight,
            hasDetail = hasDetail,
            topPad = topPad,
            bottomPad = bottomPad,
        )
    }
}

@Composable
private fun AttendanceContent(
    uiState: AttendanceUiState,
    viewModel: AttendanceViewModel,
    widthSizeClass: WindowWidthSizeClass,
    cellHeight: Dp,
    hasDetail: Boolean,
    topPad: Dp,
    bottomPad: Dp,
) {
    if (widthSizeClass == WindowWidthSizeClass.Expanded) {
        // Two-pane: calendar + summary on the left, the selected day's detail on the right.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                calendarItems(uiState, viewModel, cellHeight)
                monthSummaryItem(uiState)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasDetail) {
                    dayDetailItems(uiState)
                } else {
                    item {
                        EmptyState(
                            icon = Icons.Default.Event,
                            title = stringResource(R.string.empty_day_detail_title),
                            message = stringResource(R.string.empty_day_detail_message),
                        )
                    }
                }
            }
        }
    } else {
        ConstrainedContent {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                calendarItems(uiState, viewModel, cellHeight)
                // Detail takes the summary's slot rather than appending below it, so selecting a day
                // never turns this into a scrolling screen.
                if (hasDetail) dayDetailItems(uiState) else monthSummaryItem(uiState)
            }
        }
    }
}

private fun LazyListScope.calendarItems(uiState: AttendanceUiState, viewModel: AttendanceViewModel, cellHeight: Dp) {
    item {
        MonthSelector(
            currentMonth = uiState.currentMonth,
            onPrevious = { viewModel.previousMonth() },
            onNext = { viewModel.nextMonth() },
        )
    }
    item {
        CalendarGrid(
            yearMonth = uiState.currentMonth,
            summaries = uiState.summaries,
            selectedDateKey = uiState.selectedDateKey,
            trackingStartDate = uiState.trackingStartDate,
            today = uiState.today,
            onDayClick = { viewModel.selectDay(it) },
            cellHeight = cellHeight,
        )
    }
}

private fun LazyListScope.monthSummaryItem(uiState: AttendanceUiState) {
    item {
        MonthSummaryCard(
            summaries = uiState.summaries,
            month = uiState.currentMonth,
            trackedDaysInMonth = uiState.trackedDaysInMonth,
            allTimeAvgDailyMs = uiState.allTimeAvgDailyMs,
            today = uiState.today,
            formatDuration = TimeFormat::durationShort,
        )
    }
}

private const val DAYS_PER_WEEK = 7

/** Rendered week rows for [month] — the same 4-6 range the grid lays out. */
private fun weeksIn(month: YearMonth): Int {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val startOffset =
        (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    // Round up: a partial trailing week still occupies a row.
    return (startOffset + month.lengthOfMonth() + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
}

private val MIN_CELL_HEIGHT = 48.dp

// A cell is only ~53dp wide on a phone, so an unbounded height stretches it into a ribbon. This cap
// keeps the proportions sane and leaves the summary card on screen alongside the grid.
private val MAX_CELL_HEIGHT = 80.dp

// What the grid has to share the viewport with, measured from the composables themselves.
private val MONTH_SELECTOR_HEIGHT = 48.dp
private val WEEKDAY_HEADER_HEIGHT = 20.dp
private val SUMMARY_CARD_HEIGHT = 200.dp
private val SECTION_SPACING = 16.dp

/** The part of the above that is text, and so grows with the user's font-size setting. */
private val TEXT_CONTENT_HEIGHT = 100.dp

private fun LazyListScope.dayDetailItems(uiState: AttendanceUiState) {
    item {
        Text(
            text = stringResource(R.string.day_detail_title, uiState.selectedDateKey ?: ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    items(uiState.selectedDaySessions.filter { it.stoppedAt != null }, key = { it.id }) { session ->
        DayDetailRow(session, TimeFormat::durationShort)
    }
}

@Composable
private fun MonthSelector(currentMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month),
            )
        }
    }
}

@Composable
private fun DayDetailRow(session: CheckInSession, formatDuration: (Long) -> String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${TimeFormat.clock(session.startedAt)} - ${session.stoppedAt?.let {
                    TimeFormat.clock(it)
                } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = session.duration?.let { formatDuration(it) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
